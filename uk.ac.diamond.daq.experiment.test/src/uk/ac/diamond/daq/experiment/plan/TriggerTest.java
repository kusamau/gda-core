package uk.ac.diamond.daq.experiment.plan;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.diamond.daq.experiment.api.plan.IPlanRegistrar;
import uk.ac.diamond.daq.experiment.api.plan.ISampleEnvironmentVariable;
import uk.ac.diamond.daq.experiment.api.plan.ITrigger;

public class TriggerTest {

	private class TriggerCounter {

		private CountDownLatch countdown;
		private int target;

		public TriggerCounter(int target) {
			reset(target);
		}

		public void eventTriggered() {
			countdown.countDown();
		}

		public boolean await() throws InterruptedException {
			return countdown.await(target*50, TimeUnit.MILLISECONDS);
		}

		public void reset(int target) {
			this.target = target;
			countdown = new CountDownLatch(target);
		}
	}

	private Plan ep = mock(Plan.class);

	@Test
	public void testPositionTrigger() throws InterruptedException {
		TriggerCounter triggerCounter = new TriggerCounter(10);
		SEVTrigger trigger = new PositionTrigger(ep, getDummySEV(1.0), triggerCounter::eventTriggered, 0.1);

		trigger.setEnabled(true);

		for (int i=100; i >= 0; i--) {
			double signal = (double) i/100;
			trigger.signalChanged(signal);
		}
		boolean expectedEventsRecorded = triggerCounter.await();
		assertAllEventsCaptured(expectedEventsRecorded);
	}

	@Test
	public void testPositionTriggerAwkwardValues() throws InterruptedException {
		TriggerCounter triggerCounter = new TriggerCounter(6);

		SEVTrigger trigger = new PositionTrigger(ep, getDummySEV(1.94), triggerCounter::eventTriggered, 0.16);

		double[] signals = new double[] {1.94, // initial value
										 1.74, // delta = 0.2 - trigger!
										 1.7,
										 1.59,
										 1.58, // 0.16 since last trigger - trigger!
										 1.42, // trigger!
										 1.0,  // trigger!
										 0.9,
										 0.88,
										 0.76, // trigger!
										 0.0   // trigger!
									};

		trigger.setEnabled(true);

		for (double signal : signals) {
			trigger.signalChanged(signal);
		}

		boolean expectedEventsRecorded = triggerCounter.await();
		assertAllEventsCaptured(expectedEventsRecorded);
	}

	private ISampleEnvironmentVariable getDummySEV(double startingValue) {
		return new SampleEnvironmentVariable(()->startingValue);
	}

	@Test
	public void testSingleFireTrigger() throws InterruptedException {

		TriggerCounter triggerCounter = new TriggerCounter(1);

		double signalWhichShouldFire = 0.617;
		double tolerance = 0.01;
		OneShotWithMemory trigger = new OneShotWithMemory(ep, getDummySEV(0.0), triggerCounter::eventTriggered, signalWhichShouldFire, tolerance);
		trigger.setEnabled(true);

		for (int i=0; i<=200; i++) {
			double signal = (double) i/100;
			trigger.signalChanged(signal);
		}

		boolean expectedEventsRecorded = triggerCounter.await();
		assertAllEventsCaptured(expectedEventsRecorded);
		assertEquals(signalWhichShouldFire, trigger.triggeringSignal, tolerance);
	}

	/**
	 * Same as SingleFireTrigger, but recording the triggering signal
	 */
	class OneShotWithMemory extends SingleFireTrigger {

		OneShotWithMemory(IPlanRegistrar registrar, ISampleEnvironmentVariable sev, Runnable runnable, double triggerSignal, double tolerance) {
			super(registrar, sev, runnable, triggerSignal, tolerance);
		}

		double triggeringSignal;

		@Override
		protected boolean evaluateTriggerCondition(double signal) {
			boolean willTrigger = super.evaluateTriggerCondition(signal);
			if (willTrigger) triggeringSignal = signal;
			return willTrigger;
		}

	}

	@Test
	public void sameTriggerInMultipleSegments() throws InterruptedException {
		// When a segment ends, it disables the trigger
		// a later segment should be able to reenable the same trigger
		TriggerCounter triggerCounter = new TriggerCounter(3);
		SEVTrigger trigger = new PositionTrigger(ep, getDummySEV(0), triggerCounter::eventTriggered, 2);

		trigger.setEnabled(true);
		for (int i=1;i<7;i++) trigger.signalChanged(i);
		boolean expectedEventsRecorded = triggerCounter.await();
		trigger.setEnabled(false);
		assertAllEventsCaptured(expectedEventsRecorded);

		triggerCounter.reset(5);

		trigger.setEnabled(true);
		for (int i=1;i<11;i++) trigger.signalChanged(i);
		expectedEventsRecorded = triggerCounter.await();
		assertAllEventsCaptured(expectedEventsRecorded);
	}

	@Test
	public void enabledTriggerShouldIgnoreEnableCall() {
		ITrigger trigger = new PositionTrigger(ep, getDummySEV(0), ()-> {}, 9);
		trigger.setEnabled(true);
		trigger.setEnabled(true);
	}

	@Test
	public void disabledTriggerShouldIgnoreDisableCall() {
		ITrigger trigger = new TimedTrigger(ep, ()->{}, 1); // disabled on instantiation
		trigger.setEnabled(false);
	}

	@Test
	public void timedTriggerShouldStopWhenDisabled() throws InterruptedException {
		TriggerCounter triggerCounter = new TriggerCounter(3);
		ITrigger trigger = new TimedTrigger(ep, triggerCounter::eventTriggered, 15);

		trigger.setEnabled(true);
		boolean eventsDetectedWhileTriggerEnabled = triggerCounter.await();
		trigger.setEnabled(false);
		triggerCounter.reset(1);
		boolean eventsDetectedWhileTriggerDisabled = triggerCounter.await();
		assertThat(eventsDetectedWhileTriggerEnabled, is(true));
		assertThat(eventsDetectedWhileTriggerDisabled, is(false));
	}

	@Test
	@Ignore("Was useful for TDD but not reliable enough for CI tests")
	public void timedTriggerHasReasonableDuration() throws InterruptedException {
		TriggerCounter triggerCounter = new TriggerCounter(2);
		ITrigger timedTrigger = new TimedTrigger(ep, triggerCounter::eventTriggered, 50);
		Instant startTime = Instant.now();
		timedTrigger.setEnabled(true);
		boolean expectedEventsCaputured = triggerCounter.await();
		Duration duration = Duration.between(startTime, Instant.now());
		double durationInMilliSeconds = duration.toMillis();

		assertAllEventsCaptured(expectedEventsCaputured);

		// 2 events at 20 Hz (50 ms/event) +/- 15% = 100 ms +/- 15 ms
		assertThat(durationInMilliSeconds, is(closeTo(100, 15)));
	}

	private void assertAllEventsCaptured(boolean allCaptured) {
		assertThat("Timed out before expected events were recorded", allCaptured, is(true));
	}
}