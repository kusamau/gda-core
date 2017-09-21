/*-
 * Copyright © 2017 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package gda.jython.server;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReader.Option;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.python.core.Py;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.configuration.properties.LocalProperties;
import gda.jython.IScanDataPointObserver;
import gda.jython.JythonServerFacade;
import gda.jython.completion.AutoCompletion;
import gda.scan.ScanDataPoint;
import gda.util.Version;

/**
 * JythonShell provides the REPL for interacting with the GDA server.
 * It offers code completion and persistent history.
 * <p>
 * It has no knowledge of the connection type
 */
class JythonShell implements Closeable, gda.jython.Terminal, IScanDataPointObserver {

	private static final String PS1 = ">>> ";
	private static final String PS2 = "... ";
	private static final String JYTHON_SERVER_HISTORY_FILE = "jython_server.history";
	private static final String WELCOME_BANNER;
	private static final String BANNER_FILE_NAME = "welcome_banner.txt";
	private static final Logger logger = LoggerFactory.getLogger(JythonShell.class);
	private static final AtomicInteger counter = new AtomicInteger(0);
	static {
		String banner;
		try (InputStream in = JythonShell.class.getResourceAsStream(BANNER_FILE_NAME)) {
			banner = new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			logger.error("Could not close InputStream for {}", BANNER_FILE_NAME, e);
			banner = "Welcome to GDA %s\n";
		}
		WELCOME_BANNER = String.format(banner, Version.getRelease());
	}

	private final JythonServerFacade server;
	private final Terminal terminal;
	private final LineReader read;
	private final InputStream rawInput;
	private final int shellNumber;

	JythonShell(Terminal term) throws Exception {
		terminal = term;
		server = JythonServerFacade.getCurrentInstance();
		final String gdaVar = LocalProperties.getVarDir();
		File historyFile = new File(gdaVar, JYTHON_SERVER_HISTORY_FILE);
		read = LineReaderBuilder.builder()
				.terminal(term)
				.appName("GDA")
				.completer(new GdaJythonCompleter())
				.parser(GdaJythonLine::new)
				.variable(LineReader.HISTORY_FILE, historyFile)
				.variable(LineReader.SECONDARY_PROMPT_PATTERN, PS2)
				.variable(LineReader.ERRORS, 40)
					// ^^^ hack to work around jline hardcoded completion handling
					// https://github.com/jline/jline3/issues/147
				.build();
		read.unsetOpt(Option.HISTORY_REDUCE_BLANKS); // keep tabs/indents in history
		read.setOpt(Option.MENU_COMPLETE); // Show completion options as menu
		read.setOpt(Option.DISABLE_EVENT_EXPANSION); // prevent escape characters being ignored
		rawInput = new JlineInputStream();
		shellNumber = counter.getAndIncrement();
	}

	/**
	 * Run the setup code that makes this shell usable
	 * <p>
	 * Separate from constructor to prevent passing incomplete instance as listener
	 */
	private void init() {
		server.addOutputTerminal(this);
		server.addIScanDataPointObserver(this);
		terminal.writer().print(WELCOME_BANNER);
		setTitle(String.format("GDA %s", Version.getRelease()));
	}

	/**
	 * Run the actual REPL. Blocks while running and only returns when EOFException is reached
	 */
	public void run() {
		logger.info("Starting jython shell {}", shellNumber);
		init();
		StringBuilder fullCommand = new StringBuilder();
		String command = "";
		boolean needMore = false;
		while (command != null) {
			try {
				command = read.readLine(needMore ? PS2 : PS1);
				fullCommand.append(command);
				needMore = server.runsource(fullCommand.toString(), "input", rawInput);
				if (!needMore) {
					fullCommand = new StringBuilder();
				} else {
					fullCommand.append('\n');
				}
			} catch (UserInterruptException uie) {
				fullCommand = new StringBuilder();
				needMore = false;
				rawWrite("KeyboardInterrupt\n");
			}
		}
	}

	@Override
	public void close() {
		// Don't close the terminal here as it is managed by the connection
		server.deleteOutputTerminal(this);
		server.deleteIScanDataPointObserver(this);
	}

	/**
	 * Handle updates from {@link JythonServerFacade}. Most likely {@link ScanDataPoint}s
	 */
	@Override
	public void update(Object source, Object arg) {
		if (arg instanceof ScanDataPoint) {
			ScanDataPoint sdp = (ScanDataPoint) arg;
			// If its the first point in a scan print the header
			if (sdp.getCurrentPointNumber() == 0) {
				write(sdp.getHeaderString() + "\n");
			}
			write(sdp.toFormattedString() + "\n");
		}
	}

	/**
	 * Write to the terminal
	 * <p>
	 * If this instance is in the process of reading a new line when the output is being printed,
	 * clear the prompt line first, then print the output, then restore the prompt.
	 * This prevents half completed commands being disrupted by output from other terminals.
	 */
	@Override
	public void write(String output) {
		try {
			read.callWidget(LineReader.CLEAR);
			if (!"\n".equals(output)) {
				rawWrite(output);
				if (!output.endsWith("\n")) {
					rawWrite("\n");
				}
			}
			read.callWidget(LineReader.REDISPLAY);
			return;
		} catch (IllegalStateException iae) {
			// can't call widgets if the terminal is not in a readline call
			// not reading -> we don't need to preserve the prompt line
		}
		rawWrite(output);
	}

	/**
	 * Write to and flush the terminal
	 *
	 * @param output the String to be written
	 */
	private void rawWrite(String output) {
		terminal.writer().write(output);
		terminal.writer().flush();
	}

	/**
	 * Set the title of the terminal window used to connect to this shell.
	 * <br>
	 * Handles the escaping and control characters needed.
	 *
	 * @param title The title to be used
	 */
	private void setTitle(String title) {
		rawWrite(String.format("\033]0;%s\007", title));
	}

	/**
	 * InputStream to be used when a command needs to read from StdIn
	 * <p>
	 * Lets Jline handle the input to enable movement in the line etc
	 */
	class JlineInputStream extends InputStream {
		@Override
		public int read() throws IOException {
			// This should never be called but just pass through to socket input if it is
			logger.warn("Unexpected call to JlineInputStream.read()", new Exception("Non-error -> stack trace for unexpected call"));
			return terminal.input().read();
		}

		/**
		 * Use LineReader to wrap input stream and provide readline-like input support
		 */
		@Override
		public int read(byte[] buf, int offset, int len) throws IOException {
			String line;
			try {
				// We don't want the raw_input line to be kept in the history
				read.setVariable(LineReader.DISABLE_HISTORY, true);
				line = read.readLine(new String(buf, 0, offset));
			} catch (EndOfFileException eof) { // ctrl-d while editing
				throw Py.EOFError("EOF when reading a line");
			} catch (UserInterruptException uie) { // ctrl-c while editing
				// this is is never handled as another (java.nio.channels.ClosedByInterruptException)
				// is thrown before it can be caught
				throw Py.KeyboardInterrupt("KeyboardInterrupt");
			} finally {
				read.setVariable(LineReader.DISABLE_HISTORY, false);
			}
			byte[] bytes = line.getBytes();
			for (int i = 0; i < bytes.length; i++) {
				buf[i+offset] = bytes[i];
			}
			return bytes.length;
		}
	}

	/**
	 * Completer to convert completions provided by JythonServer into Candidates accepted by Jline
	 */
	private class GdaJythonCompleter implements Completer {
		@Override
		public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
			AutoCompletion ac = server.getCompletionsFor(line.line(), line.cursor());
			candidates
				.addAll(ac
						.getStrings()
						.stream()
						.map(v -> new Candidate(v, v, null, null, null, null, false))
						.collect(Collectors.toList()));
		}
	}
}