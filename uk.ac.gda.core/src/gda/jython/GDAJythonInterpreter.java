/*-
 * Copyright © 2009 Diamond Light Source Ltd., Science and Technology
 * Facilities Council Daresbury Laboratory
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

package gda.jython;

import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static uk.ac.gda.common.util.EclipseUtils.PLATFORM_BUNDLE_PREFIX;
import static uk.ac.gda.common.util.EclipseUtils.URI_SEPARATOR;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.core.runtime.FileLocator;
import org.python.core.ContextManager;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyModule;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PySystemState;
import org.python.core.PyUnicode;
import org.python.core.ThreadState;
import org.python.core.imp;
import org.python.util.InteractiveConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.google.common.base.Stopwatch;

import gda.configuration.properties.LocalProperties;
import gda.device.Scannable;
import gda.factory.FactoryException;
import gda.factory.Finder;
import gda.jython.logging.JythonLogHandler;
import gda.jython.logging.PythonException;
import gda.jython.translator.Translator;
import uk.ac.gda.common.util.EclipseUtils;

/**
 * <p>
 * Wrapper for the JythonInterpreter class.
 * <P>
 * IMPORTANT: for the classes in this package to work properly jython must be fully installed on the machine and the
 * jython.jar located in the jython installation folder must be referenced in the java classpath. Jython.jar must NOT be
 * located anywhere else. This is because there is more to jython than just the files in jython.jar(!).****
 */
public class GDAJythonInterpreter {
	private static final Logger logger = LoggerFactory.getLogger(GDAJythonInterpreter.class);
	private static final String JYTHON_VERSION = "2.7";
	private static final String JYTHON_BUNDLE_PATH = "uk.ac.diamond.jython/jython%s";
	private static final String UTF_8 = "UTF-8";
	private static final Properties sysProps;

	private final OverwriteLock overwriteLock = new OverwriteLock();

	private static File cacheDir;

	// the Jython Interactive Console
	private InteractiveConsole interactiveConsole;

	// to avoid running the initialise method more than once
	private boolean initialized = false;

	// the translator object used to convert GDA syntax into 'true' jython
	private static Translator translator = null;

	// folders where beamline and user scripts are held
	private final ScriptPaths jythonScriptPaths;

	private final GDAJythonClassLoader classLoader = new GDAJythonClassLoader();

	/**
	 * Static initializer bock to set all the static parameters on the PySystemState class
	 * to be used in all instantiations, i.e. those that persist through reset_namespace
	 */
	static {
		// If not already specified, work out the Jython cache directory and
		// create if required
		String cacheDirName = LocalProperties.getVarDir() + "jythonCache";
		cacheDir = new File(cacheDirName);
		if (!cacheDir.exists()) {
			cacheDir.mkdir();
		}

		// custom properties for the GDA
		logger.info("determining Jython properties...");
		final Properties gdaCustomProperties = new Properties();
		gdaCustomProperties.setProperty("python.console.encoding", UTF_8);
		gdaCustomProperties.setProperty("python.cachedir", cacheDir.getAbsolutePath());

		try {
			final File jythonRoot = EclipseUtils.resolveBundleFolderFile(String.format(JYTHON_BUNDLE_PATH, JYTHON_VERSION));
			gdaCustomProperties.setProperty("python.home", jythonRoot.getAbsolutePath());
		} catch (IOException e) {
			throw new RuntimeException("Jython bundle not found", e);
		}

		if (LocalProperties.check("python.options.showJavaExceptions", false)) {
			gdaCustomProperties.setProperty("python.options.showJavaExceptions", "true");
		}
		if (LocalProperties.check("python.options.includeJavaStackInExceptions", false)) {
			gdaCustomProperties.setProperty("python.options.includeJavaStackInExceptions", "true");
		}
		if (LocalProperties.check("python.options.showPythonProxyExceptions", false)) {
			gdaCustomProperties.setProperty("python.options.showPythonProxyExceptions", "true");
		}
		String verbose = LocalProperties.get("python.verbose", "");
		if (!verbose.isEmpty()) {
			gdaCustomProperties.setProperty("python.verbose", verbose);
		}

		sysProps = System.getProperties();

		// Log the Jython version
		logger.info("Using Jython version: {}", PySystemState.version.toString().split("\n")[0]);

		// Initialise the Jython 'sys' class statics for use when constructing instances of it
		PySystemState.initialize(sysProps, gdaCustomProperties);
	}

	public GDAJythonInterpreter(final ScriptPaths scriptPaths) {
		jythonScriptPaths = scriptPaths;
	}

	/**
	 * @return string - the full path of the beamline's initialisation script.
	 */
	public String getGdaStationScript() {
		return jythonScriptPaths != null ? jythonScriptPaths.getStartupScript() : "";
	}

	/**
	 * Configures this interpreter.
	 */
	public void configure() {
		// Check the OSGi aware Jython Classloader is properly initialized before proceeding
		if (!GDAJythonClassLoader.useGDAClassLoader()) {
			logger.error("GDAJythonClassLoader not initialized properly GDA-Server cannot Start");
			throw new UnsupportedOperationException();
		}

		logger.info("adding GDA package locations to Jython path...");

		// Create a new Jython 'sys' instance to be used by the Py infrastructure based on the settings
		// supplied to PySystemState.initialize in the initializer block above.
		// Enclose in a try/catch so that any underlying Jython errors are not lost to the log.
		final PySystemState pss = new PySystemState();
		try {
			Py.setSystemState(pss);
			pss.setdefaultencoding(UTF_8);		// cannot be done before Py.setSystemState
			Py.defaultSystemState = pss;
		} catch (Exception e) {
			if (e instanceof PyException) {
				logger.error("Jython initialisation problem: " + e);     // Since PyException puts message in value member
			} else {
				logger.error("Jython initialisation problem: ", e);
			}
			throw e;
		}

		// In an OSGi environment Jython's normal CLASSPATH based automatic way of
		// discovering which packages are available in the JVM does not
		// work. Therefore to support "from XXXX import *" Jython has to be
		// told about the bundle locations.
		final boolean eclipseLaunch = Boolean.valueOf(sysProps.getProperty("gda.eclipse.launch"));
		final String bundlesRoot;
		if (eclipseLaunch) {
			bundlesRoot = LocalProperties.get(LocalProperties.GDA_GIT_LOC);
			iterateWorkspace(classLoader);
		} else {
			bundlesRoot = sysProps.getProperty("osgi.syspath");
			iteratePluginsDirectory(bundlesRoot);
		}

		if (jythonScriptPaths == null) {
			logger.warn("no jython script paths defined");
		} else {
			// Add the paths for the standard script folders to the existing _jythonScriptPaths
			// (the instance config scripts folders are handled by Spring injection into the JythonServer bean)
			int index = 1;
			for (Entry<String, String> scriptEntry : classLoader.getStandardFolders().entrySet()) {
				String pathFragment = scriptEntry.getKey();
				if (pathFragment.endsWith(URI_SEPARATOR)) {
					pathFragment = pathFragment.substring(0, pathFragment.length() - 1);
				}
				File scriptFolder = Paths.get(bundlesRoot, pathFragment).toFile(); // Default to non-plugin folder under workspace_git
				String frag = pathFragment;
				URL scriptFolderURL = null;
				try {
					while (scriptFolderURL == null && frag.contains(URI_SEPARATOR)) {
						scriptFolderURL = FileLocator.find(new URL(String.format(PLATFORM_BUNDLE_PREFIX, frag)));
						frag = frag.substring(frag.indexOf(URI_SEPARATOR) + 1);
					}
					if (scriptFolderURL != null) {
						scriptFolder = EclipseUtils.resolveFileFromPlatformURL(scriptFolderURL);
					} else if (!eclipseLaunch) {
						scriptFolder = Paths.get(bundlesRoot, "..", "utilities", pathFragment).toFile(); // Add in non-plugin folder offset for exported product
					}
					if (scriptFolder.exists() && scriptFolder.isDirectory()) {
						String title = scriptEntry.getValue() == null ? "Scripts: Std" + index++
								: scriptEntry.getValue();
						jythonScriptPaths.addProject(
								new ScriptProject(scriptFolder.getCanonicalPath(), title, ScriptProjectType.CORE));
					} else {
						throw new IOException(String.format("Script folder %s does not exist", scriptFolder));
					}
				} catch (IOException e) {
					logger.error(String.format(
							"Unable to locate plugin for script location %s, these scripts will not be accessible",
							pathFragment), e);
				}
			}

			// append the folders where standard scripts will be located to jython path
			// by this point _jythonScriptPaths should contain a List of these folder paths

			logger.info("clearing old Jython class files...");
			// Remove any previously compiled Jython class files from the script folders
			for (ScriptProject scriptProject : jythonScriptPaths.getProjects()) {
				try {
					final PyString scriptFolderName = new PyString(scriptProject.getPath());
					final File scriptDir = new File(scriptFolderName.toString());
					if (!scriptDir.exists()) {
						throw new FactoryException(String.format("Configured Jython script location %s does not exist.",
								scriptFolderName));
					}
					// toRealPath resolves the true absolute path resolving symlinks and ../'s
					logger.info("Adding '{}' to the Command Server Jython path with name '{}'", scriptDir.toPath().toRealPath(),
							scriptProject.getName());

					if (!pss.path.contains(scriptFolderName)) {
						removeAllJythonClassFiles(new File(scriptFolderName.getString()));
						pss.path.append(scriptFolderName);
					}
				} catch (Exception e) {
					logger.error("Error while setting up script paths, {} scripts will not be accessible", scriptProject.getPath(), e);
				}
			}
		}
		classLoader.setSysPath(pss.path); // Inform the ClassLoader of the sys.path contents

		// Log the sys.path in jython so where things will be loaded from
		logger.debug("sys.path: {}", pss.path);

		// Create the __main__ module for the console to use
		PyModule mod = imp.addModule("__main__");

		PySystemState.getDefaultBuiltins().__setitem__("overwriting", overwriteLock);

		// Replace globals dict to prevent scannables and aliases being overwritten
		GdaGlobals globals = new GdaGlobals(overwriteLock);
		globals.update(mod.__dict__);
		mod.__dict__ = globals;

		// Get instance of interactive console
		interactiveConsole = new GDAInteractiveConsole(mod.__dict__, pss);

		logger.info("Jython configured");
	}

	/**
	 * Retrieve the set of server plugin directories when running from the eclipse workspace and register
	 * them with Jython as sources for full package import.
	 *
	 * @param classLoader	The class loader being used by the interpreter to allow non-server bundle filtering
	 */
	private void iterateWorkspace(final GDAJythonClassLoader classLoader) {
		logger.info("Retrieving eclipse workspace server plugin paths for Jython");

		final String unwanted = "^.*?(.feature|.releng|.test|.site|.git).*$";
		final File workspaceGit = new File(System.getProperties().getProperty("gda.install.git.loc"));

		final File[]repoDirectories = workspaceGit.listFiles(f -> f.isDirectory() && f.getName().endsWith(".git"));
		for (File repoDir : repoDirectories) {
			// cope with 'group' repos that have their plugins in a 'plugins' sub directory
			final Path pluginsDir = Paths.get(repoDir.getAbsolutePath(), "plugins");
			if (Files.isDirectory(pluginsDir)) {
				repoDir = pluginsDir.toFile();
			}
			// filter 'non-bundle' and test directories
			final File[]pluginDirectories = repoDir.listFiles(f -> f.isDirectory() && f.getName().contains(".") && !f.getName().matches(unwanted));

			// Store only the paths for directories corresponding to server plugins from the workspace via isMappedBundle
			Arrays.stream(pluginDirectories).filter(dir -> classLoader.isMappedBundle(dir.getName()))
                                            .map(dir-> Paths.get(dir.getAbsolutePath(), getCorrectClassFilesLocation(dir)))
                                            .forEach(classesPath -> PySystemState.add_classdir(classesPath.toString()));
		}
	}

	/**
	 * Deal with the gda.core bin directory anomaly until we can get rid of the gda python script and make it consistent
	 *
	 * @param dir		The File object corresponding to a workspace plugin dir
	 * @return			classes/main for gda.core, "bin" for everything else
	 */
	private String getCorrectClassFilesLocation(final File dir) {
		return dir.getName().equals("uk.ac.gda.core") ? "classes" + File.separatorChar + "main" : "bin";
	}

	/**
	 * Retrieve the set of plugin directories when running from the exported product and
	 * register them with Jython as sources for full package import filtering out TP plugins.
	 * If parsing the artifacts.xml file fails an error is logged but execution will proceed
	 * without further restricting the plugins that will allow import *.
	 *
	 * @param osgiSysPath	The path to the plugins directory in the exported product
	 */
	private void iteratePluginsDirectory(final String osgiSysPath) {
		logger.info("Retrieving server product plugin paths for Jython");

		// Read in the target platform plugin names so that they can be skipped
		final Set<String> unwanted = new HashSet<>();
		try (Stream<String> stream = Files.lines(Paths.get(osgiSysPath, "..", "configuration", "target_platform_artifacts.xml"))) {
			stream.forEach(line -> {
				if (line.contains(" classifier='osgi.bundle'")) {
					unwanted.add(getTPArtifactAttributeValue(line, "id") + "_" + getTPArtifactAttributeValue(line, "version"));
					}
				});
		} catch (IOException | UncheckedIOException e) {
			logger.error("Unable to successfully read target platform artifacts file, import * will not be fully restricted", e);
		}
		// Initialise the Jython registry skipping target platform bundles.
		// The 'from xx import *' syntax will be supported for the remaining bundles
		for(File file : new File(osgiSysPath).listFiles()) {
			String name = file .getName();
			if (name.endsWith(".jar")) {
				name = name.substring(0, name.indexOf(".jar"));
			}
			if (!unwanted.contains(name)) {
				if (file.isDirectory()) {
					PySystemState.add_classdir(file.getAbsolutePath());
				} else {
					PySystemState.packageManager.addJar(file.getAbsolutePath(), false);
				}
			}
		}
	}

	/**
	 * Extract the value of an attribute from one of the artifact tags read in from the target platform
	 * artifact.xml file.
	 *
	 * @param artifactTag		The artifact tag string
	 * @param attributeName		The name of the attribute whose value it to be retrieved
	 * @return					The value requested if found successfully
	 * @throws					UncheckedIOException if the attribute name or its terminating quote cannot be found
	 */
	private String getTPArtifactAttributeValue(final String artifactTag, final String attributeName) throws UncheckedIOException {
		final String match = " " + attributeName + "='";
		final int matchLength = match.length();
		final int from = artifactTag.indexOf(match) + matchLength;	// indexOf will return -1 if not found in which case from will be < matchLength
		final int to = artifactTag.indexOf("'", from);
		if (from < matchLength || to == -1) {
			throw new UncheckedIOException(new IOException("Cannot read " + attributeName + " in artifact tag of target_platform_artifacts.xml file"));
		}
		return artifactTag.substring(from, to);
	}

	/**
	 * Set up the Jython interpreter and run Jython scripts to connect to the ObjectServer. This must be run once by the
	 * calling program after the interpreter instance has been created.
	 */
	protected void initialise(JythonServer jythonServer) throws Exception {
		if (!initialized) {

			try {
				// TODO Maybe the translator should be configured via Spring not property? This would remove this code.
				final String translatorClassName = LocalProperties.get("gda.jython.translator.class", "GeneralTranslator");
				final Class<?> translatorClass = Class.forName("gda.jython.translator." + translatorClassName);
				translator = (Translator) translatorClass.newInstance();

				// set the console output
				final Writer terminalWriter = jythonServer.getTerminalWriter();
				interactiveConsole.setOut(terminalWriter);
				interactiveConsole.setErr(terminalWriter);

				// give Jython the reference to this wrapper object
				interactiveConsole.set("GDAJythonInterpreter", this);
				interactiveConsole.set(Jython.SERVER_NAME, jythonServer);

				// standard imports
				logger.info("performing standard Jython interpreter imports...");
				interactiveConsole.runsource("import java");
				interactiveConsole.runsource("from java.lang import Thread");
				interactiveConsole.runsource("from java.lang import Runnable");
				interactiveConsole.runsource("from java.lang import InterruptedException");

				// gda imports
				interactiveConsole.runsource("from gda.scan import *");
				interactiveConsole.runsource("from gda.device import *");
				interactiveConsole.runsource("from gda.jython import JythonServer");
				interactiveConsole.runsource("from gda.jython import ScriptBase");
				interactiveConsole.runsource("from gda.device.monitor import BeamMonitor");

				interactiveConsole.runsource("from gda.factory import Finder");
				interactiveConsole.runsource("from gda.device.detector import DetectorBase");
				interactiveConsole.runsource("from gda.device import Scannable");
				interactiveConsole.runsource("from gda.device.scannable.scannablegroup import IScannableGroup");
				interactiveConsole.runsource("finder = Finder.getInstance();");
				interactiveConsole.runsource("from gda.device.scannable import ScannableBase");
				interactiveConsole.runsource("from gda.device.scannable import DummyScannable");
				interactiveConsole.runsource("from gda.device.scannable import ContinuouslyScannable");
				interactiveConsole.runsource("from gda.device.scannable import SimulatedContinuouslyScannable");
				interactiveConsole.runsource("from gda.device.scannable import PseudoDevice");
				interactiveConsole.runsource("from gda.jython.commands import ScannableCommands");
				interactiveConsole.runsource("from gda.jython.commands.ScannableCommands import *");
				interactiveConsole.runsource("from gda.jython.commands import GeneralCommands");
				interactiveConsole.runsource("from gda.jython.commands.GeneralCommands import *");
				interactiveConsole.runsource("from gda.jython.commands import InputCommands");
				interactiveConsole.runsource("from gda.jython.commands.InputCommands import *");

				// persistence
				interactiveConsole.runsource("from uk.ac.diamond.daq.persistence.jythonshelf import LocalParameters");
				interactiveConsole.runsource("from uk.ac.diamond.daq.persistence.jythonshelf import LocalObjectShelfManager");

				// import other interfaces to use with list command
				interactiveConsole.runsource("from gda.device import ScannableMotion");
				interactiveConsole.runsource("import gda.device.scannable.ScannableUtils");
				interactiveConsole.runsource("from gda.util.converters import IReloadableQuantitiesConverter");


				// scisoftpy
				interactiveConsole.runsource("import scisoftpy as dnp");
				// inform translator what the built-in commands are by
				// aliasing them -- i.e. reserved words
				exec("alias ls");
				exec("alias ls_names");
				exec("vararg_alias pos");
				exec("vararg_alias upos");
				exec("vararg_alias inc");
				exec("vararg_alias uinc");
				exec("alias help");
				exec("alias list_defaults");
				exec("vararg_alias add_default");
				exec("vararg_alias remove_default");
				exec("vararg_alias level");
				exec("alias pause");
				exec("alias reset_namespace");
				exec("alias run");
				exec("vararg_alias scan");
				exec("vararg_alias pscan");
				exec("vararg_alias cscan");
				exec("vararg_alias zacscan");
				exec("vararg_alias testscan");
				exec("vararg_alias gscan");
				exec("vararg_alias tscan");
				exec("vararg_alias timescan");
				exec("vararg_alias staticscan");
				exec("alias lastScanDataPoint");

				// define a function that can check a java object for a field or method called
				// __doc__ and print it out
				exec("def _gdahelp(obj=None):\n"
						+ "    if obj is None:\n"
						+ "        GeneralCommands.gdahelp()\n"
						+ "        return\n"
						+ "    if hasattr(obj, '__class__'):\n"
						+ "        if issubclass(obj.__class__, java.lang.Object):\n"
						+ "            helptext = None\n"
						+ "            if hasattr(obj, '__doc__'):\n"
						+ "                helptext = obj.__doc__\n"
						+ "                if not isinstance(helptext, str):\n"
						+ "                    if hasattr(helptext, '__call__'):\n"
						+ "                        helptext = helptext()\n"
						+ "                    elif isinstance(helptext, unicode):\n"
						+ "                        print helptext\n"
						+ "                        return\n"
						+ "                    else:\n"
						+ "                        helptext = None\n"
						+ "            if helptext is not None:\n"
						+ "                print helptext\n"
						+ "                return\n"
						+ "    import pydoc\n"
						+ "    pydoc.help(obj)\n"
						+ "    print\n");

				initialiseLoggingRedirection();
				populateNamespace();
				runStationStartupScript();

			} catch (Exception ex) {
				logger.error("GDAJythonInterpreter: error while initialising", ex);
				throw ex;
			} finally {
				initialized = true;
			}
		}
	}

	/**
	 * Adds two default handlers to the jython logging package.
	 * <p>
	 * One redirects all logs to the main GDA logs (including full stacktraces), the other
	 * writes messages (above INFO level) to the console for the user. Console messages include a list
	 * of exception causes but omits the full traceback.
	 * <p>
	 * See the python logging docs <a href="https://docs.python.org/2/library/logging.html">here</a>.
	 *
	 * @see JythonLogHandler
	 */
	private void initialiseLoggingRedirection() {
		String logInit = "import logging\n"
				+ "from loghandling import JythonLogRedirector, JythonTerminalPrinter\n"
				+ "_root_logger = logging.getLogger()\n"
				+ "_root_logger.name = 'gda.jython.root'\n"
				+ "_root_logger.level = 0\n" // set levels to 0 as slf4j filters logging
				+ "_root_logger.addHandler(JythonLogRedirector())\n"
				+ "_root_logger.addHandler(JythonTerminalPrinter(logging.INFO))\n"
				+ "del logging\n"
				+ "del JythonLogRedirector\n"
				+ "del JythonTerminalPrinter\n"
				+ "del _root_logger\n\n";
		exec(logInit);
	}

	/**
	 * Adds all Scannables from the Finder to the Jython namespace.
	 */
	private void populateNamespace() {
		logger.info("Populating Jython namespace...");

		final Map<String, Scannable> nameToScannable = Finder.getInstance().getFindablesOfType(Scannable.class);
		nameToScannable.forEach(this::placeInJythonNamespace);

		logger.info("Finished populating Jython namespace, added {} Scannables", nameToScannable.size());
	}

	/**
	 * Runs the station startup script, {@code localStation.py}.
	 */
	private void runStationStartupScript() {
		// import the station startup script
		// run this last as it may use variables set up above
		String gdaStationScript = getGdaStationScript();
		// File localStation = new File(getGdaScriptDir() + "localStation.py");
		if (StringUtils.hasText(gdaStationScript)) {
			logger.info("Running startupScript: {}", gdaStationScript);
			final Stopwatch localStationStopwatch = Stopwatch.createStarted();
			try {
				File localStation = new File(gdaStationScript);
				final String lines = JythonServerFacade.slurp(localStation);
				this.runscript(lines);
				logger.info("Completed startupScript. Took {} seconds", localStationStopwatch.elapsed(SECONDS));
			} catch (Exception e) {
				logger.error("Error running startupScript. Failed after {} seconds",
						localStationStopwatch.elapsed(SECONDS), e);
			}
		} else {
			logger.info("No startupScript defined");
		}
	}

	/**
	 * Translates and then runs the given file through the Jython interpreter.
	 *
	 * @param input
	 *            File
	 */
	protected void exec(String input) throws PyException {
		// translate script into true Jython line by line
		String translated = translateScriptToGDA(input);
		// pass entire script to interpreter
		try {
			interactiveConsole.exec(new PyUnicode(translated));
		} catch (PyException e) {
			interactiveConsole.showexception(e);
			throw e;
		}

	}

	/**
	 * Runs the script updating the CommandServer status as it goes.
	 *
	 * @param input script to run
	 */
	protected void runscript(String input) {
		// pass entire script to interpreter
		exec(input);
	}

	/**
	 * Gives the command to the JythonInterpreter's runsource method and runs it with a given STDIN
	 *
	 * @param command String to run in interpreter
	 * @param in InputStream to use for STDIN
	 * @return boolean
	 */
	protected boolean runsource(String command, InputStream in) {
		interactiveConsole.setIn(in);
		return interactiveConsole.runsource(command);
	}

	/**
	 * Get the object from the Jython namespace known by the given string.
	 *
	 * @param objectName
	 * @return Object
	 */
	protected Object getFromJythonNamespace(String objectName) {
		return interactiveConsole.get(objectName, Object.class);
	}

	/**
	 * Returns the contents of the top-level namespace.
	 * <p>
	 * This returns object references so cannot be distributed.
	 *
	 * @return PyObject
	 */
	public PyObject getAllFromJythonNamepsace() {
		return interactiveConsole.getLocals();
	}

	/**
	 * Place an object into the Jython namespace.
	 *
	 * @param objectName
	 *            What the object is to be known as.
	 * @param obj
	 */
	protected void placeInJythonNamespace(String objectName, Object obj) {
		interactiveConsole.set(objectName, obj);
		logger.debug("Added '{}' to Jython namespace", objectName);
	}

	/**
	 * Runs a Jython command which returns some output. As the Jython engine is in a distributed environment, only
	 * strings are returned. Object references will also be converted to strings.
	 *
	 * @param command
	 *            String - must be python code - cannot run import javaclass - this results in fixParseError - unknown
	 *            source
	 * @return String
	 */
	protected String evaluate(String command) {
		String output = null;
		try {
			command = translator.translate(command);
			PyObject result = interactiveConsole.eval(command);
			output = result.toString();
		} catch (PyException e) {
			logger.error("Error evaluating command: {}", command, PythonException.from(e));
		}
		return output;
	}

	/**
	 * Changes dynamically the translator being used.
	 *
	 * @param myTranslator
	 */
	protected void setTranslator(Translator myTranslator) {
		if (myTranslator != null) {
			GDAJythonInterpreter.translator = myTranslator;
		}
	}

	/**
	 * @return the translator
	 */
	protected static Translator getTranslator() {
		return translator;
	}

	/**
	 * Creates a script in a format compatible with the GDAJythonInterpreter runcode method. The main part of the script
	 * is from the input object. All lines are translated from GDA syntax into true Jython from GDA syntax where
	 * necessary. A header and footer are then added to make the script run in a separate thread, so that the GUI does
	 * not freeze up.
	 *
	 * @param input
	 *            a Jython script
	 * @return a string of the same Jython code (translated to true Jython where required) which will run in its own
	 *         thread
	 */
	public static String translateScriptToGDA(String input) {
		return stream(input.split("\n", -1))
				.map(translator::translate)
				.collect(joining("\n"));
	}

	/**
	 * Recursively moves through a directory and removes all Jython class files. This should be done during the
	 * initialise stage to ensure that a restart forces all Jython bytecode to be refreshed.
	 * <p>
	 * This shouldn't really be needed, and in future Jython releases may not be a problem. But for now it solves an
	 * observed problem on all beamlines that occasionally after a Command Server restart old versions of Jython modules
	 * are in use rather than the latest ones.
	 *
	 * @param dir
	 */
	private void removeAllJythonClassFiles(File dir) {

		// find all .class files and remove them
		FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith("$py.class");
			}
		};
		try {
			File[] filesToRemove = dir.listFiles(filter);
			for (File file : filesToRemove) {
				file.delete();
			}
		} catch (NullPointerException e) {
			logger.warn("not a directory or i/o error on: " + dir.toString());
		}

		// find all directories and recursively operate on them
		FileFilter fileFilter = new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory();
			}
		};
		File[] files = dir.listFiles(fileFilter);
		try {
			for (File file : files) {
				removeAllJythonClassFiles(file);
			}
		} catch (RuntimeException e) {
			logger.warn("Could not remove compiled class files from " + dir.toString());
		}
	}

	public InteractiveConsole getInterp() {
		return interactiveConsole;
	}

	protected static class OverwriteLock extends PyObject implements ContextManager {
		private transient ThreadLocal<Integer> locks = ThreadLocal.withInitial(() -> 0);
		@Override
		public PyObject __enter__(ThreadState ts) {
			logger.trace("Allowing scannable overwriting");
			updateLock(1);
			return Py.None;
		}

		@Override
		public boolean __exit__(ThreadState ts, PyException exception) {
			logger.trace("Preventing scannable overwriting");
			updateLock(-1);
			return false;
		}

		private void updateLock(int change) {
			int previous = locks.get();
			int next = previous + change;
			if (next >= 0) {
				logger.trace("Setting overwriting lock from {} -> {}", previous, next);
				locks.set(next);
			} else {
				logger.warn("Trying to set overwrite lock to {} - resetting to 0", next);
				locks.set(0);
			}
		}

		protected boolean enabled() {
			return locks.get() > 0;
		}

		@Override
		public String toString() {
			return "ScannableOverwritingBypass";
		}
	}

	/**
	 * Extension of dictionary to use for python globals so that we can intercept sets
	 * and check that we're not overriding a scannable or aliased command.
	 *
	 * Deletions are intercepted so that deleting an aliased command also removes the alias
	 */
	protected static class GdaGlobals extends PyStringMap {
		private final OverwriteLock overwriting;

		public GdaGlobals(OverwriteLock lock) {
			overwriting = lock;
		}
		@Override
		public void __setitem__(String key, PyObject value) {
			// TODO: DAQ-704 This should be reviewed to either allow all aliasing
			// or made stricter to prevent it. Currently just log occasions where
			// workarounds are used to avoid current checks

			// Check if we're trying to overwrite an aliased command
			if (translator != null
					&& (translator.getAliasedCommands().contains(key)
							|| translator.getAliasedVarargCommands().contains(key))) {
				logger.debug("Overwriting aliased command '{}' with '{}'", key, value);
			}

			// Try and get existing object from namespace
			PyObject obj = get(new PyString(key), Py.None);
			// Check if it's a scannable
			if (obj != Py.None && obj.__tojava__(Scannable.class) != Py.NoConversion) {
				if (overwriting.enabled()) {
					logger.debug("Overwriting scannable '{}' with '{}'", key, value);
				} else {
					// TODO: Throw exception instead of logging a warning
					logger.warn("Overwriting scannable '{}' with '{}' without overwriting enabled", key, value);
				}
			}
			super.__setitem__(key, value);
		}

		@Override
		public void __delitem__(String key) {
			// If deleting an aliased command, remove the alias
			if (translator != null) {
				translator.getAliasedCommands().remove(key);
				translator.getAliasedVarargCommands().remove(key);
			}
			super.__delitem__(key);
		}
	}
}
