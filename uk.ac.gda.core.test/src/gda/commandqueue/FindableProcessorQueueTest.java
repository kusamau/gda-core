/*-
 * Copyright © 2010 Diamond Light Source Ltd.
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

package gda.commandqueue;

import gda.TestHelpers;
import gda.commandqueue.Processor.STATE;
import gda.observable.IObserver;

import java.io.File;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class FindableProcessorQueueTest implements IObserver {

	static final long MAX_TIMEOUT_MS = 500;

	@BeforeClass
	public static void setUpBeforeClass() {
	}

	@AfterClass
	public static void tearDownAfterClass() {
	}

	Processor processor;
	Queue queue;

	public void setUp(String testName) throws Exception {
		String testScratchDirectoryName = TestHelpers.setUpTest(FindableProcessorQueueTest.class, testName, true);
		FindableProcessorQueue simpleProcessor = new FindableProcessorQueue();
		simpleProcessor.setLogFilePath(testScratchDirectoryName + File.separator + "commandQueue.log");
		simpleProcessor.setStartImmediately(false);
		simpleProcessor.afterPropertiesSet();
		processor = simpleProcessor;
		queue = new CommandQueue();
		simpleProcessor.setQueue(queue);
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testPause() throws Exception {
		setUp("testPause");
		TestCommand pauseCommand = new TestCommand(processor,MAX_TIMEOUT_MS);
		pauseCommand.pause=true;
		queue.addToTail(pauseCommand);
		TestCommand normalCommand = new TestCommand(processor,MAX_TIMEOUT_MS, 0);
		queue.addToTail(normalCommand);
		processor.start(MAX_TIMEOUT_MS);
		Thread.sleep(500);
		Assert.assertEquals(Command.STATE.PAUSED, pauseCommand.getState());
		Assert.assertEquals(Command.STATE.NOT_STARTED, normalCommand.getState());
	}

	@Test
	public void testException() throws Exception {
		setUp("testException");
		TestCommand throwExceptionCommand = new TestCommand(processor,MAX_TIMEOUT_MS);
		throwExceptionCommand.throwException=true;
		throwExceptionCommand.setDescription("testException");
		queue.addToTail(throwExceptionCommand);
		processor.start(MAX_TIMEOUT_MS);
		Thread.sleep(500);
	}


	@Test
	public void testSkip() throws Exception {
		setUp("testSkip");
		TestCommand skipCommand = new TestCommand(processor,MAX_TIMEOUT_MS);
		skipCommand.skip=true;
		queue.addToTail(skipCommand);
		TestCommand normalCommand = new TestCommand(processor,MAX_TIMEOUT_MS, 0);
		queue.addToTail(normalCommand);
		processor.start(MAX_TIMEOUT_MS);
		Thread.sleep(500);
		processor.start(MAX_TIMEOUT_MS);
		Thread.sleep(500);
		Assert.assertEquals(Command.STATE.ABORTED, skipCommand.getState());
		Assert.assertEquals(Command.STATE.COMPLETED, normalCommand.getState());
	}


	@Test
	public void testStartWithQueueEmpty() throws Exception {
		setUp("testStartWithQueueEmpty");
		processor.addIObserver(this);
		processor.start(MAX_TIMEOUT_MS);
		TestCommand command = new TestCommand(processor,MAX_TIMEOUT_MS, 0);
		command.addIObserver(new IObserver() {

			@Override
			public void update(Object source, Object arg) {
				System.out.print(arg.toString());
			}
		});
		queue.addToTail(command);
		Thread.sleep(500);
		Assert.assertEquals(Command.STATE.COMPLETED, command.getState());

	}

	@Test
	public void testStartWithQueueNonEmpty() throws Exception {
		setUp("testStartWithQueueNonEmpty");
		processor.addIObserver(this);
		TestCommand command = new TestCommand(processor,MAX_TIMEOUT_MS, 0);
		queue.addToTail(command);
		processor.start(MAX_TIMEOUT_MS);
		Thread.sleep(500);
		Assert.assertEquals(Command.STATE.COMPLETED, command.getState());

	}

	@Test
	public void testStop() throws Exception {
		setUp("testStop");
		processor.addIObserver(this);
		processor.start(MAX_TIMEOUT_MS);
		processor.stop(MAX_TIMEOUT_MS);
		Thread.sleep(500);

		Assert.assertEquals(STATE.WAITING_START, state);
	}

	STATE state = STATE.UNKNOWN;
	@Override
	public void update(Object source, Object arg) {
		if( source == processor && arg instanceof Processor.STATE)
			this.state = (Processor.STATE)arg;

	}

}
