package uk.ac.diamond.daq.experiment.plan;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.configuration.properties.LocalProperties;
import gda.factory.Findable;
import gda.jython.InterfaceProvider;
import uk.ac.diamond.daq.experiment.api.plan.IExperimentRecord;
import uk.ac.diamond.daq.experiment.api.plan.IPlan;
import uk.ac.diamond.daq.experiment.api.plan.IPlanFactory;
import uk.ac.diamond.daq.experiment.api.plan.IPlanRegistrar;
import uk.ac.diamond.daq.experiment.api.plan.ISampleEnvironmentVariable;
import uk.ac.diamond.daq.experiment.api.plan.ISegment;
import uk.ac.diamond.daq.experiment.api.plan.ITrigger;
import uk.ac.diamond.daq.experiment.api.plan.LimitCondition;
import uk.ac.diamond.daq.experiment.api.plan.SEVSignal;

public class Plan implements IPlan, IPlanRegistrar {
	
	private static final Logger logger = LoggerFactory.getLogger(Plan.class);
	
	private IPlanFactory factory;
	
	private String name;
	private List<ISampleEnvironmentVariable> sevs = new ArrayList<>();
	private List<ISegment> segments = new LinkedList<>();
	private List<ITrigger> triggers = new ArrayList<>();
	
	private Queue<ISegment> segmentChain;
	private ISegment activeSegment;
	private ExperimentRecord record;
	private boolean running;
	
	private String dataDirBeforeExperiment;
	private String experimentDataDir;
	
	public Plan(String name) {
		this.name = name;
		setFactory(new PlanFactory());
	}
	
	@Override
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public void start() {
		validatePlan();
		
		// set subdirectory
		dataDirBeforeExperiment = LocalProperties.get(LocalProperties.GDA_DATAWRITER_DIR);
		experimentDataDir = Paths.get(dataDirBeforeExperiment, validName(getName())).toString();
		LocalProperties.set(LocalProperties.GDA_DATAWRITER_DIR, experimentDataDir);
		
		running = true;
		record = new ExperimentRecord();
		
		logger.info("Plan '{}' execution started", getName());
		printBanner("Plan '" + getName() + "' execution started");
		
		activateNextSegment();
	}
	
	private void validatePlan() {
		if (segments.isEmpty()) throw new IllegalStateException("No segments defined!");
		segmentChain = new LinkedList<>(segments);
		
		// Ensure all segments have unique names
		long uniqueSegmentNames = segments.stream().map(Findable::getName).distinct().count();
		if (segments.size() != uniqueSegmentNames) throw new IllegalStateException("Segments should have unique names!");
		
		// Ensure all triggers have unique names
		long uniqueTriggerNames = triggers.stream().map(Findable::getName).distinct().count();
		if (triggers.size() != uniqueTriggerNames) throw new IllegalStateException("Triggers should have unique names!");
	}
	
	private void activateNextSegment() {
		activeSegment = segmentChain.poll();
		if (activeSegment == null) {
			terminateExperiment();
		} else {
			record.segmentActivated(activeSegment.getName());
			activeSegment.activate();
		}
	}	
	
	private void terminateExperiment() {
		running = false;
		String summary = record.summary();
		logger.info("End of experiment'{}'", getName());
		logger.info(summary);
		
		printBanner("Plan '" + getName() + "' execution complete");
		
		LocalProperties.set(LocalProperties.GDA_DATAWRITER_DIR, dataDirBeforeExperiment);
	}
	
	private void printBanner(String msg) {
		String horizontal = Collections.nCopies(msg.length() + 4, "#").stream().collect(Collectors.joining());
		String banner = new StringBuilder("\n")
							.append(horizontal)
							.append('\n')
							.append("# ")
							.append(msg)
							.append(" #\n")
							.append(horizontal).toString();
		
		InterfaceProvider.getTerminalPrinter().print(banner);
	}
	
	@Override
	public boolean isRunning() {
		return running;
	}	
	
	@Override
	public void triggerOccurred(ITrigger trigger, double triggeringSignal) {
		switchToTriggerSubdirectory(activeSegment, trigger);
		record.triggerOccurred(trigger.getName(), triggeringSignal);
	}
	
	@Override
	public void segmentActivated(ISegment segment) {
		record.segmentActivated(segment.getName());
	}

	@Override
	public void segmentComplete(ISegment completedSegment, double terminatingSignal) {
		record.segmentComplete(completedSegment.getName(), terminatingSignal);
		activateNextSegment();
	}
	
	private void switchToTriggerSubdirectory(ISegment segment, ITrigger trigger) {
		LocalProperties.set(LocalProperties.GDA_DATAWRITER_DIR, Paths.get(experimentDataDir, validName(segment), validName(trigger)).toString());
	}
	
	private static final Pattern INVALID_CHARACTERS_PATTERN = Pattern.compile("[^a-zA-Z0-9\\.\\-\\_]");
	
	private String validName(Findable findable) {
		return INVALID_CHARACTERS_PATTERN.matcher(findable.getName()).replaceAll("_");
	}
	
	private String validName(String name) {
		return INVALID_CHARACTERS_PATTERN.matcher(name).replaceAll("_");
	}
	
	@Override
	public void setFactory(IPlanFactory factory) {
		this.factory = factory;
		factory.setRegistrar(this);
	}
	
	@Override
	public void setRegistrar(IPlanRegistrar registrar) {
		throw new IllegalStateException("For internal use only");
	}
	
	@Override
	public ISampleEnvironmentVariable addSEV(SEVSignal signalProvider) {
		ISampleEnvironmentVariable sev = factory.addSEV(signalProvider);
		sevs.add(sev);
		return sev;
	}
	
	@Override
	public ISegment addSegment(String name, ISampleEnvironmentVariable sev, LimitCondition limit, ITrigger... triggers) {
		ISegment segment = factory.addSegment(name, sev, limit, triggers);
		segments.add(segment);
		addTriggers(triggers);
		return segment;
	}
	
	@Override
	public ISegment addSegment(String name, long duration, ITrigger... triggers) {
		ISegment segment = factory.addSegment(name, duration, triggers);
		segments.add(segment);
		addTriggers(triggers);
		return segment;
	}
	
	@Override
	public ITrigger addTrigger(String name, ISampleEnvironmentVariable sev, Runnable runnable, double triggerInterval) {
		ITrigger trigger = factory.addTrigger(name, sev, runnable, triggerInterval);
		triggers.add(trigger);
		return trigger;
	}
	
	@Override
	public ITrigger addTrigger(String name, ISampleEnvironmentVariable sev, Runnable runnable,
			double triggerSignal, double tolerance) {
		ITrigger trigger = factory.addTrigger(name, sev, runnable, triggerSignal, tolerance);
		triggers.add(trigger);
		return trigger;
	}
	
	@Override
	public ITrigger addTimerTrigger(String name, Runnable runnable, long period) {
		ITrigger trigger = factory.addTimerTrigger(name, runnable, period);
		triggers.add(trigger);
		return trigger;
	}
	
	@Override
	public ISegment addSegment(String name, LimitCondition limit, ITrigger... triggers) {
		ISampleEnvironmentVariable sev = lastDefinedSEV();
		return addSegment(name, sev, limit, triggers);
	}
	
	@Override
	public ITrigger addTrigger(String name, Runnable runnable, double triggerInterval) {
		ISampleEnvironmentVariable sev = lastDefinedSEV();
		return addTrigger(name, sev, runnable, triggerInterval);
	}

	@Override
	public ITrigger addTrigger(String name, Runnable runnable, double triggerSignal, double tolerance) {
		ISampleEnvironmentVariable sev = lastDefinedSEV();
		return addTrigger(name, sev, runnable, triggerSignal, tolerance);
	}
	
	private void addTriggers(ITrigger... trigs) {
		for (ITrigger trigger : trigs) {
			if (!triggers.contains(trigger)) {
				triggers.add(trigger);
			}
		}
	}
	
	private ISampleEnvironmentVariable lastDefinedSEV() {
		if (sevs.isEmpty()) throw new IllegalArgumentException("No SEVs defined!");
		return sevs.get(sevs.size()-1);
	}
	
	@Override
	public IExperimentRecord getExperimentRecord() {
		if (isRunning()) throw new IllegalStateException("Experiment " + getName() + " is still running!");
		return record;
	}

	@Override
	public String toString() {
		return "Plan [name=" + name + ", sevs=" + sevs + ", segments=" + segments + ", triggers=" + triggers
				+ ", running=" + running + "]";
	}
	
	
}