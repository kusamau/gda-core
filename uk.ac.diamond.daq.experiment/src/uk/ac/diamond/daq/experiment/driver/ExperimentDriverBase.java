package uk.ac.diamond.daq.experiment.driver;

import static uk.ac.diamond.daq.experiment.api.driver.DriverState.IDLE;
import static uk.ac.diamond.daq.experiment.api.driver.DriverState.PAUSED;
import static uk.ac.diamond.daq.experiment.api.driver.DriverState.RUNNING;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import uk.ac.diamond.daq.experiment.api.driver.AbortCondition;
import uk.ac.diamond.daq.experiment.api.driver.DriverState;
import uk.ac.diamond.daq.experiment.api.driver.ExperimentDriverModel;
import uk.ac.diamond.daq.experiment.api.driver.IExperimentDriver;
import uk.ac.diamond.daq.experiment.api.plan.SEVSignal;

public abstract class ExperimentDriverBase implements IExperimentDriver {
	
	private String name;
	
	private Map<String, SEVSignal> sensors;
	private ExperimentDriverModel model = new ExperimentDriverModel();
	
	// Should this be org.eclipse.scanning.api.event.scan.DeviceState?
	protected DriverState state = IDLE;

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setModel(ExperimentDriverModel model) {
		this.model = model;
	}

	@Override
	public ExperimentDriverModel getModel() {
		return model;
	}
	
	public void setSensors(Map<String, SEVSignal> sensors) {
		this.sensors = sensors;
	}

	@Override
	public Map<String, SEVSignal> getSensors() {
		return sensors;
	}
	
	@Override
	public void zero() {
		checkValidState("zero", IDLE);
		doZero();
	}

	@Override
	public void start() {
		checkValidState("start", IDLE);
		
		getModel().getAbortConditions().forEach(AbortCondition::activate);
		
		doStart();
		state = RUNNING;
	}

	@Override
	public void pause() {
		checkValidState("pause", RUNNING);
		doPause();
		state = PAUSED;
	}

	@Override
	public void resume() {
		checkValidState("resume", PAUSED);
		doResume();
		state = RUNNING;
	}

	@Override
	public void abort() {
		checkValidState("abort", RUNNING, PAUSED);
		
		getModel().getAbortConditions().forEach(AbortCondition::deactivate);
		
		doAbort();
		state = IDLE;
	}

	@Override
	public DriverState getState() {
		return state;
	}
	
	protected abstract void doZero();
	protected abstract void doStart();
	protected abstract void doPause();
	protected abstract void doResume();
	protected abstract void doAbort();
	
	private void checkValidState(String methodName, DriverState... allowedStates) {
		List<DriverState> validStates = Arrays.asList(allowedStates);
		if (validStates.contains(state)) return;
		final String states = validStates.stream().map(Object::toString).collect(Collectors.joining(", "));
		throw new IllegalStateException("Method " + methodName + " can only be called from: " + states);
	}

}