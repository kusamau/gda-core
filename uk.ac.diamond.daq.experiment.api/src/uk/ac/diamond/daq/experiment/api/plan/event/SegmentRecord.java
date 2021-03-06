package uk.ac.diamond.daq.experiment.api.plan.event;

import java.io.Serializable;
import java.time.Instant;

import uk.ac.diamond.daq.experiment.api.plan.ISegment;

/**
 * Keep track of when a particular {@link ISegment} begins and ends, and what signal caused it to terminate
 */
public class SegmentRecord implements Serializable {

	private static final long serialVersionUID = 7981329060834102814L;

	private String segmentName;
	private String sampleEnvironmentName;
	private long startTime;
	private long endTime;

	private double terminationSignal;

	public SegmentRecord(String segmentName, String sampleEnvironmentName) {
		startTime = Instant.now().toEpochMilli();
		this.segmentName = segmentName;
		this.sampleEnvironmentName = sampleEnvironmentName;
	}

	public SegmentRecord() {}

	public void terminated(double terminationSignal) {
		endTime = Instant.now().toEpochMilli();
		this.terminationSignal = terminationSignal;
	}

	public String getSegmentName() {
		return segmentName;
	}

	public void setSegmentName(String name) {
		this.segmentName = name;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public double getTerminationSignal() {
		return terminationSignal;
	}

	public void setTerminationSignal(double terminationSignal) {
		this.terminationSignal = terminationSignal;
	}

	public String getSampleEnvironmentName() {
		return sampleEnvironmentName;
	}

	public void setSampleEnvironmentName(String sampleEnvironmentName) {
		this.sampleEnvironmentName = sampleEnvironmentName;
	}
}
