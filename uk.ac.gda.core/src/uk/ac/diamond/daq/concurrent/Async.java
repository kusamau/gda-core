/*-
 * Copyright © 2018 Diamond Light Source Ltd.
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

package uk.ac.diamond.daq.concurrent;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods used to run short-lived tasks asynchronously.<p>
 *
 * Tasks are run in their own thread. Existing idle threads will be reused if possible to
 * minimise overhead of creating new threads.<p>
 * This is intended to replace the use of
 * <pre>
 * new Thread(new Runnable() {
 *     public void run() {
 *         // do stuff
 *     }
 * }).start();
 * </pre>
 * with
 * <pre>
 * Async.execute(() -> {
 *     // do stuff
 * });
 * </pre>
 * Repeating tasks can also be submitted in a similar way to using {@link ScheduledExecutorService}s.
 * For most uses, a thread running to execute something every second or so is idle for the majority
 * of the time. This allows fewer threads to be running and to reduce the idle time.
 *
 * @since 9.8
 * @see ExecutorService
 * @see ScheduledExecutorService
 * @see Future
 */
public final class Async {
	private static final Logger logger = LoggerFactory.getLogger(Async.class);
	/** Starting pool size for scheduling executor */
	private static final int SCHEDULER_THREAD_BASE_COUNT = 5;

	/** The time (in seconds) for threads to idle before they're terminated */
	private static final long CACHED_THREAD_KEEP_ALIVE_TIME = 60L;

	/** Thread pool to manage execution of asynchronous tasks */
	/* This is the same as Executors#newCachedThreadPool.
	 * Use constructor here rather than Executors factory method to avoid having to cast
	 * to get usage stats */
	private static final ThreadPoolExecutor THREAD_POOL = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            CACHED_THREAD_KEEP_ALIVE_TIME, SECONDS,
            new SynchronousQueue<Runnable>(),
            Threads.daemon().named("AsyncThreadPool").factory()
	);

	/** Thread pool to manage scheduled execution of repeated tasks */
	/* This is the same as Executors#newScheduledThreadPool.
	 * Use constructor here to avoid casting to get usage stats */
	private static final ScheduledThreadPoolExecutor SCHEDULER = new ScheduledThreadPoolExecutor(SCHEDULER_THREAD_BASE_COUNT,
			Threads.daemon().named("AsyncScheduler").factory()
	);

	/** Single thread to monitor state of common pools */
	private static final ScheduledExecutorService MONITOR = Executors.newSingleThreadScheduledExecutor(
			Threads.daemon().named("AsyncMonitor").factory()
	);

	static {
		// Report basic stats of ongoing tasks and update pool sizes if necessary
		// MONITOR must be single threaded as monitorUsage is not thread safe
		MONITOR.scheduleAtFixedRate(Async::monitorUsage, 1, 1, SECONDS);

		// Shutdown thread pools cleanly on shutdown
		Runtime.getRuntime().addShutdownHook(new Thread(Async::shutdown));
	}

	/**
	 * This class is not intended to be instantiated
	 */
	private Async() {}

	/**
	 * Run a {@link Runnable} in its own thread.
	 *
	 * @param target The task to be run
	 *
	 * @see ExecutorService#execute(Runnable)
	 * @since 9.8
	 */
	public static void execute(Runnable target) {
		execute(target, Thread.currentThread().getName());
	}


	/**
	 * Run a {@link Runnable} in its own named thread.
	 * The name is created using {@link String#format} with the given nameFormat and args
	 *
	 * @param target The task to be run
	 * @param nameFormat The name of the thread to run this task
	 * @param args Objects to format the name with
	 *
	 * @see ExecutorService#execute(Runnable)
	 * @since 9.8
	 */
	public static void execute(Runnable target, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Runnable must not be null");
		THREAD_POOL.submit(new ThreadNamingRunnableWrapper(String.format(nameFormat, args), target));
	}

	/**
	 * Submit a task to be run in its own thread. Returns a Future giving access to the completion or
	 * error state of the task and allowing tasks to be cancelled.<p>
	 * The Future also gives access to the return value of the {@link Callable}
	 *
	 * @param <T> the return type of the task to be run
	 * @param target The task to be run
	 * @return A Future
	 *
	 * @see ExecutorService#submit(Callable)
	 * @see Future
	 * @since 9.8
	 */
	public static <T> Future<T> submit(Callable<T> target) {
		return submit(target, Thread.currentThread().getName());
	}

	/**
	 * Submit a task to be run in its own named thread. Returns a Future giving access to the completion or
	 * error state of the task and allowing tasks to be cancelled.<p>
	 * The Future also gives access to the return value of the {@link Callable}.<p>
	 * This method differs from {@link #submit(Callable)} in that it renames the thread before running the task.
	 * The name is created using {@link String#format} with the given nameFormat and args
	 *
	 * @param <T> the return type of the task to be run
	 * @param target The task to be run
	 * @param nameFormat For the thread used to run this task
	 * @param args Objects to format the name with
	 * @return A Future
	 *
	 * @see ExecutorService#submit(Callable)
	 * @see Future
	 * @since 9.8
	 */
	public static <T> Future<T> submit(Callable<T> target, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Callable must not be null");
		return THREAD_POOL.submit(new ThreadNamingCallableWrapper<>(String.format(nameFormat, args), target));
	}

	/**
	 * Submit a task to be run in its own thread. Returns a Future giving access to the completion or
	 * error state of the task and allowing tasks to be cancelled.<p>
	 * The Future's get method will return {@code null} on successful completion
	 *
	 * @param target The task to be run
	 * @return Future giving access to the state of the running task
	 *
	 * @see ExecutorService#submit(Runnable)
	 * @see Future
	 * @since 9.8
	 */
	public static Future<?> submit(Runnable target) {
		return submit(target, Thread.currentThread().getName());
	}

	/**
	 * Submit a task to be run in its own thread. Returns a Future giving access to the completion or
	 * error state of the task and allowing tasks to be cancelled.<p>
	 * The Future's get method will return {@code null} on successful completion<p>
	 * This differs from {@link #submit(Runnable)} in that it renames the thread before running the task.
	 * The name is created using {@link String#format} with the given nameFormat and args
	 *
	 * @param target The task to be run
	 * @param nameFormat For the thread used to run this task
	 * @param args Objects to format the name with
	 * @return Future giving access to the state of the running task
	 *
	 * @see ExecutorService#submit(Runnable)
	 * @see Future
	 * @since 9.8
	 */
	public static Future<?> submit(Runnable target, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Runnable must not be null");
		return THREAD_POOL.submit(new ThreadNamingRunnableWrapper(String.format(nameFormat, args), target));
	}

	/**
	 * Schedule a task to be run repeatedly at a fixed rate after an initial delay.
	 * <p>
	 * This differs from {@link #scheduleWithFixedDelay} in that the time between executions can vary
	 * if the duration of the task is variable.
	 *
	 * @param target The task to run
	 * @param delay The time before the initial execution
	 * @param period The period between successive executions
	 * @param unit Time unit of delay and period
	 * @return Future giving caller ability to check status and cancel task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
	 * @since 9.8
	 */
	public static ScheduledFuture<?> scheduleAtFixedRate(Runnable target, long delay, long period, TimeUnit unit) {
		return scheduleAtFixedRate(target, delay, period, unit, Thread.currentThread().getName());
	}

	/**
	 * Schedule a task to be run repeatedly at a fixed rate after an initial delay.
	 * <p>
	 * This differs from {@link #scheduleWithFixedDelay} in that the time between executions can vary
	 * if the duration of the task is variable.<p>
	 * This differs from {@link #scheduleAtFixedRate(Runnable, long, long, TimeUnit)} in that it renames
	 * the thread before running the task.
	 * The name is created using {@link String#format} with the given nameFormat and args.
	 *
	 * @param target The task to run
	 * @param delay The time before the initial execution
	 * @param period The period between successive executions
	 * @param unit Time unit of delay and period
	 * @param nameFormat For the thread used to run this task
	 * @param args Objects to format the name with
	 * @return Future giving caller ability to check status and cancel task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
	 * @since 9.8
	 */
	public static ScheduledFuture<?> scheduleAtFixedRate(Runnable target, long delay, long period, TimeUnit unit, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Runnable must not be null");
		return SCHEDULER.scheduleAtFixedRate(new ThreadNamingRunnableWrapper(String.format(nameFormat, args), target), delay, period, unit);
	}

	/**
	 * Schedule a task to be run repeatedly with a fixed delay between executions. The first execution can
	 * be delayed by a different amount of time.
	 * <p>
	 * This differs from {@link #scheduleAtFixedRate} in that the period can vary if the duration of
	 * the task is variable;
	 *
	 * @param target The task to run
	 * @param delay The time before the initial execution
	 * @param period The time between successive executions
	 * @param unit Time unit of delay and period
	 * @return Future giving caller ability to check status and cancel task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
	 * @since 9.8
	 */
	public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable target, long delay, long period, TimeUnit unit) {
		return scheduleWithFixedDelay(target, delay, period, unit, Thread.currentThread().getName());
	}

	/**
	 * Schedule a task to be run repeatedly with a fixed delay between executions. The first execution can
	 * be delayed by a different amount of time.
	 * <p>
	 * This differs from {@link #scheduleAtFixedRate} in that the period can vary if the duration of
	 * the task is variable.<p>
	 * This differs from {@link #scheduleWithFixedDelay(Runnable, long, long, TimeUnit)} in that it renames
	 * the thread before running the task.
	 * The name is created using {@link String#format} with the given nameFormat and args.
	 *
	 * @param target The task to run
	 * @param delay The time before the initial execution
	 * @param period The time between successive executions
	 * @param unit Time unit of delay and period
	 * @param nameFormat For the thread used to run this task
	 * @param args Objects to format the name with
	 * @return Future giving caller ability to check status and cancel task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
	 * @since 9.8
	 */
	public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable target, long delay, long period, TimeUnit unit, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Runnable must not be null");
		return SCHEDULER.scheduleWithFixedDelay(new ThreadNamingRunnableWrapper(String.format(nameFormat, args), target), delay, period, unit);
	}

	/**
	 * Schedule a task to run after a given delay. The remaining time and status of the task can be
	 * checked by the caller using the returned future. {@link Future#get()} will return null after
	 * the task is complete.
	 *
	 * @param target The task to run
	 * @param delay The time to wait before running
	 * @param unit The units of the given delay
	 * @return A Future giving access to status of task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#schedule(Runnable, long, TimeUnit)
	 * @since 9.8
	 */
	public static ScheduledFuture<?> schedule(Runnable target, long delay, TimeUnit unit) {
		return schedule(target, delay, unit, Thread.currentThread().getName());
	}

	/**
	 * Schedule a task to run after a given delay. The remaining time and status of the task can be
	 * checked by the caller using the returned future. {@link Future#get()} will return null after
	 * the task is complete.<p>
	 * This differs from {@link #schedule(Runnable, long, TimeUnit)} in that it renames
	 * the thread before running the task.
	 * The name is created using {@link String#format} with the given nameFormat and args
	 *
	 * @param target The task to run
	 * @param delay The time to wait before running
	 * @param unit The units of the given delay
	 * @param nameFormat For the thread used to run this task
	 * @param args Objects to format the name with
	 * @return A Future giving access to status of task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#schedule(Runnable, long, TimeUnit)
	 * @since 9.8
	 */
	public static ScheduledFuture<?> schedule(Runnable target, long delay, TimeUnit unit, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Runnable must not be null");
		return SCHEDULER.schedule(new ThreadNamingRunnableWrapper(String.format(nameFormat, args), target), delay, unit);
	}

	/**
	 * Schedule a task to run after a given delay. The remaining time and status of the task can be
	 * checked by the caller using the returned future. {@link Future#get()} will return the result of
	 * the task after completion.
	 *
	 * @param target The task to run
	 * @param delay The time to wait before running
	 * @param unit The units of the given delay
	 * @param <T> The return type of the given task
	 * @return A Future giving access to status of task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#schedule(Callable, long, TimeUnit)
	 */
	public static <T> ScheduledFuture<T> schedule(Callable<T> target, long delay, TimeUnit unit) {
		return schedule(target, delay, unit, Thread.currentThread().getName());
	}

	/**
	 * Schedule a task to run after a given delay. The remaining time and status of the task can be
	 * checked by the caller using the returned future. {@link Future#get()} will return the result of
	 * the task after completion.<p>
	 * This differs from {@link #schedule(Callable, long, TimeUnit)} in that it renames
	 * the thread before running the task.
	 * The name is created using {@link String#format} with the given nameFormat and args
	 *
	 * @param target The task to run
	 * @param delay The time to wait before running
	 * @param unit The units of the given delay
	 * @param nameFormat For the thread used to run this task
	 * @param args Objects to format the name with
	 * @param <T> The return type of the given task
	 * @return A Future giving access to status of task
	 *
	 * @see Future
	 * @see ScheduledExecutorService#schedule(Callable, long, TimeUnit)
	 */
	public static <T> ScheduledFuture<T> schedule(Callable<T> target, long delay, TimeUnit unit, String nameFormat, Object... args) {
		Objects.requireNonNull(target, "Callable must not be null");
		return SCHEDULER.schedule(new ThreadNamingCallableWrapper<>(String.format(nameFormat, args), target), delay, unit);
	}

	/**
	 * Submit a callable object to be run asynchronously. This method is a duplicate of {@link #submit(Callable)}
	 * and only exists to work around a Jython bug where incorrect type resolution is used (Callable python objects
	 * are used as Runnables instead so return values are lost).<p>
	 *
	 * In most cases, Jython functions and methods are callable. If a method needs parameters passed to it, a lambda can
	 * be used. eg
	 *
	 * <pre>
	 * >>> def add(a, b):
	 * ...     sleep(10)
	 * ...     return a + b
	 * ...
	 * >>> future = Async.call(lambda: add(1, 2))
	 * >>> future.get() # waits (just under) 10 seconds before returning
	 * 3
	 * </pre>
	 *
	 * This can also be used to execute things that can't be cast to Callable directly
	 * (eg classes with __call__ method).
	 * The returned Future can be used as normal to cancel or get the return value
	 *
	 * @param target should be callable
	 * @return Future giving access to PyObject returned by callable. PyNone if void function
	 *
	 * @see Future
	 * @since 9.8
	 */
	public static Future<?> call(Callable<?> target) {
		return submit(target, "AsyncJython");
	}

	/**
	 * Log current queue size and number of threads
	 */
	private static void monitorUsage() {
		// POOL stats
		int threadCount = THREAD_POOL.getActiveCount();
		int poolSize = THREAD_POOL.getPoolSize();
		int queueSize = THREAD_POOL.getQueue().size();
		if (queueSize > 10) {
			// Should not really happen as new threads are created if none are available
			logger.warn("Current common pool thread using {}/{} threads. Queue size: {}",
					threadCount,
					poolSize,
					queueSize
					);
		} else {
			logger.trace("Current common pool thread using {}/{} threads. Queue size: {}",
					threadCount,
					poolSize,
					queueSize
					);
		}

		// SCHEDULE stats
		int scheduleThreadCount = SCHEDULER.getActiveCount();
		int schedulerPoolSize = SCHEDULER.getCorePoolSize();
		int scheduleQueueSize = SCHEDULER.getQueue().size();
		if (scheduleThreadCount >= schedulerPoolSize) {
			logger.warn("Current scheduled pool thread using {}/{} threads. Queue size: {}",
					scheduleThreadCount,
					schedulerPoolSize,
					scheduleQueueSize
					);
			logger.info("Increasing the scheduler pool size to {}", schedulerPoolSize + 1);
			SCHEDULER.setCorePoolSize(schedulerPoolSize + 1);
		} else {
			logger.trace("Current scheduled pool thread using {}/{} threads. Queue size: {}",
					scheduleThreadCount,
					schedulerPoolSize,
					scheduleQueueSize
					);
			if (schedulerPoolSize > scheduleThreadCount + 2 && schedulerPoolSize > SCHEDULER_THREAD_BASE_COUNT) {
				/* Reducing the core pool size while all threads are active will not kill the threads
				 * It only kills them when they become idle so in the case where new tasks have been added
				 * since the threads were counted, no processes will be affected
				 */
				int newSize = schedulerPoolSize - 1;
				logger.info("Reducing the scheduler pool size to {}", newSize);
				SCHEDULER.setCorePoolSize(newSize);
			}
		}
	}

	/**
	 * Shutdown thread pools
	 * <p>
	 * Waits up to 2 seconds for current tasks to end.
	 */
	private static void shutdown() {
		logger.info("Shutting down common thread pools");
		List<Runnable> remainingTasks = new ArrayList<>();
		remainingTasks.addAll(SCHEDULER.shutdownNow());
		remainingTasks.addAll(THREAD_POOL.shutdownNow());
		MONITOR.shutdownNow();
		if (!remainingTasks.isEmpty()) {
			logger.warn("{} tasks were remaining on shutdown", remainingTasks.size());
		}
		try {
			THREAD_POOL.awaitTermination(2, SECONDS);
		} catch (InterruptedException e) {
			logger.warn("Currently running tasks were not shutdown successfully and may be interrupted", e);
		}
		try {
			SCHEDULER.awaitTermination(2, SECONDS);
		} catch (InterruptedException e) {
			logger.warn("Currently running scheduled tasks were not shutdown successfully and may be interrupted", e);
		}
	}

	/**
	 * A wrapper around {@link Callable} to set the name of the thread it's running in.
	 * <p>
	 * Improves debugging of code run in a default thread pool
	 *
	 * @param <V> The return type of the wrapped Callable
	 */
	static class ThreadNamingCallableWrapper<V> implements Callable<V> {
		private final String name;
		private final Callable<V> target;

		/**
		 * Wrap a {@link Callable} so that the thread that runs it is renamed before execution.
		 * @param name The name for the thread that runs the given Callable
		 * @param target The Callable to wrap
		 */
		public ThreadNamingCallableWrapper(String name, Callable<V> target) {
			Objects.requireNonNull(name, "Thread name must not be null");
			Objects.requireNonNull(target, "Target callable must not be null");
			this.name = "AsyncPool: " + name;
			this.target = target;
		}

		/**
		 * {@inheritDoc}<p>
		 * Renames the thread it's running in to a given name (and resets it
		 * after completion).
		 */
		@Override
		public V call() throws Exception {
			String oldName = Thread.currentThread().getName();
			Instant start = Instant.now();
			try {
				Thread.currentThread().setName(name);
				return target.call();
			} finally {
				Thread.currentThread().setName(oldName);
				if (logger.isTraceEnabled()) {
					Duration executionTime = Duration.between(start, Instant.now());
					logger.trace("Callable '{}' took {}ms", name, executionTime.toMillis());
				}
			}
		}
	}

	/**
	 * A wrapper around {@link Runnable} to set the name of the thread it's running in.
	 * <p>
	 * Improves debugging of code run in a default thread pool
	 *
	 */
	static class ThreadNamingRunnableWrapper implements Runnable {
		private final String name;
		private final Runnable target;

		/**
		 * Wrap a {@link Runnable} so that the thread that runs it is renamed before execution.
		 * @param name The name for the thread that runs the given Runnable
		 * @param target The Runnable to wrap
		 */
		public ThreadNamingRunnableWrapper(String name, Runnable target) {
			Objects.requireNonNull(name, "Thread name must not be null");
			Objects.requireNonNull(target, "Target runnable must not be null");
			this.name = "AsyncPool: " + name;
			this.target = target;
		}

		/**
		 * {@inheritDoc}<p>
		 *
		 * Renames the thread it's running in to a given name (and resets it
		 * after completion).
		 */
		@Override
		public void run() {
			String oldName = Thread.currentThread().getName();
			Instant start = Instant.now();
			try {
				Thread.currentThread().setName(name);
				target.run();
			} finally {
				Thread.currentThread().setName(oldName);
				if (logger.isTraceEnabled()) {
					Duration executionTime = Duration.between(start, Instant.now());
					logger.trace("Runnable '{}' took {}ms", name, executionTime.toMillis());
				}
			}
		}
	}
}
