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

package gda.device.timer;

import gda.device.DeviceBase;
import gda.device.DeviceException;
import gda.device.Timer;
import gda.device.TimerStatus;
import gda.device.detector.DAServer;
import gda.factory.Finder;

import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A timer class for the VME time frame generator card implemented using DA.Server
 */
public class Tfg extends DeviceBase implements Timer, Runnable {

	public static final String AUTO_CONTINUE_ATTR_NAME = "Auto-Continue";

	public static final String EXT_START_ATTR_NAME = "Ext-Start";
	public static final String AUTO_REARM_ATTR_NAME = "Auto-Rearm";

	public static final String EXT_INHIBIT_ATTR_NAME = "Ext-Inhibit";

	public static final String VME_START_ATTR_NAME = "VME-Start";
	
	public static final String SOFTWARE_START_AND_TRIG_ATTR_NAME = "software triggering";

	private static final Logger logger = LoggerFactory.getLogger(Tfg.class);

	private static final int EC740_MAXFRAME = 1024;
	private static final String version = "Version 1";
	protected DAServer daServer = null;
	private String daServerName;
	protected boolean extStart = false;
	//if true that tfg.start sends "tfg start", otherwise sends "tfg arm"
	protected boolean vmeStart = true;
	//if true auto-rearm to added to the end of the setup-groups command rather than cycles
	protected boolean autoReArm = false;
	// if true then send tfg arm, tfg start. For the system to work correctly in 
	// this situation then there must be a pause in every frame, including the first frame.
	protected boolean softwareTriggering = false;
	
	protected boolean extInh = false;
	protected int cycles = 1;
	private int totalCycles = 0;
	protected int totalFrames = 0;
	protected Vector<FrameSet> timeFrameProfile = new Vector<FrameSet>();
	protected Thread runner;
	protected boolean started = false;
	protected long startTime = 0;
	protected long totalExptTime;
	protected long elapsedTime = 0;
	protected boolean framesLoaded = false;
	protected boolean waitingForExtStart = false;
	private String user = null;
	private String password = null;
	private String host = null;
	private String remoteEndian = "intel";
	private int autoContinue;
	private int updateInterval = 900;
	private boolean showArmed = false;

	private boolean monitorInBackground  = true;

	@Override
	public void configure() {

		// find daserver if not set
		if (daServer == null) {
			logger.debug("Finding: " + daServerName);
			if ((daServer = (DAServer) Finder.getInstance().find(daServerName)) == null) {
				logger.error("Server " + daServerName + " not found");
			}
		}

		// if now defined
		if (daServer != null ) {
			// FIXME we night end up with more than one runner
			runner = uk.ac.gda.util.ThreadManager.getThread(this, getClass().getName());
			runner.start();
			configured = true;
		}
	}

	@Override
	public void reconfigure() {
		if (!configured) {
			configure();
		}
	}

	@Override
	public void close() {
		// we don't actually close as other devices may use the same connection
		configured = false;
		framesLoaded = false;
	}

	/**
	 * @param daServerName
	 */
	public void setDaServerName(String daServerName) {
		this.daServerName = daServerName;
	}

	/**
	 * @return da.server name
	 */
	public String getDaServerName() {
		return daServerName;
	}

	/**
	 * @return Returns the daServer.
	 */
	public DAServer getDaServer() {
		return daServer;
	}

	/**
	 * @param daServer
	 *            The daServer to set.
	 */
	public void setDaServer(DAServer daServer) {
		this.daServer = daServer;
	}

	/**
	 * @return the update interval in milliseconds
	 */
	public int getUpdateInterval() {
		return updateInterval;
	}

	/**
	 * @param updateInterval
	 */
	public void setUpdateInterval(int updateInterval) {
		this.updateInterval = updateInterval;
	}

	protected String getAcqStatus() {
		String status = "IDLE";
		Object obj = daServer.sendCommand("tfg read status");
		// this might fail and return -1
		// William and Geoff say: Only when using the simulation
		// device
		if (obj == null || !(obj instanceof String)) {
			logger.error("Tfg: getStatus(): DA.Server is not connected or returned something unexpected");
		} else {
			 status = (String) obj;
		}
		return status;
	}

	@Override
	public int getStatus() {
		int state = Timer.IDLE;
		if (daServer != null && daServer.isConnected()) {
			// null reply is returned from daServer of not connected.
			// do we want to return IDLE if not connected or error?
			String statusCommand = showArmed ? "tfg read status show-armed" : "tfg read status";
			Object o = daServer.sendCommand(statusCommand);
			if (o == null || !(o instanceof String)) {
				logger.error("Tfg: getStatus(): DA.Server is not connected or returned something unexpected");
			} else {
				String status = (String) o;
				if (status.equals("RUNNING")) {
					state = Timer.ACTIVE;
				} else if (status.equals("PAUSED")) {
					state = Timer.PAUSED;
				} else if (status.equals("EXT-ARMED")) {
					state = Timer.ARMED;
				}
			}
		}
		return state;
	}

	/**
	 * get the tfg progress message
	 */
	public String getProgress() {
		if (daServer != null && daServer.isConnected()) {
			// null reply is returned from daServer of not connected.
			// do we want to return IDLE if not connected or error?
			String progressCommand = "tfg read progress";
			Object o = daServer.sendCommand(progressCommand);
			if (o == null || !(o instanceof String)) {
				logger.error(getName() +" not connected or returned something unexpected");
			} else {
				return o.toString();
			}
		}
		return null;
	}

	@Override
	public void stop() throws DeviceException {
		waitingForExtStart = false;
		if (daServer == null || !configured) {
			throw new DeviceException(getName() + "unconfigured");
		}
		if (!daServer.isConnected()) {
			throw new DeviceException(getName() + " not connected");
		}
		daServer.sendCommand("tfg init");
	}

	@Override
	public int getMaximumFrames() {
		return EC740_MAXFRAME;
	}

	/**
	 * @return the total number of frames
	 */
	public int getTotalFrames() {
		return totalFrames;
	}

	public int getTotalCycles() {
		return totalCycles;
	}

	public long getTotalExptTime() {
		return totalExptTime;
	}

	@Override
	public int getCurrentFrame() {
		int frame = 0;
		if (daServer != null && daServer.isConnected()) {
			frame = ((Integer) daServer.sendCommand("tfg read frame")).intValue();
		}

		return frame;
	}

	@Override
	public int getCurrentCycle() {
		int cycle = 0;
		if (daServer != null && daServer.isConnected()) {
			cycle = ((Integer) daServer.sendCommand("tfg read lap")).intValue();
		}

		return cycle;
	}

	@Override
	public void setCycles(int cycles) {
		this.cycles = cycles;
	}

	public void cont() throws DeviceException {
		checkOKToSendCommand();
		if (!framesLoaded) {
			throw new DeviceException(getName() + " no frames loaded");
		}
		
		daServer.sendCommand("tfg cont");
	}

	/**
	 * Starts or arms (or both) a predefine frameset 
	 */
	@Override
	public synchronized void start() throws DeviceException {
		checkOKToSendCommand();
		if (!framesLoaded) {
			throw new DeviceException(getName() + " no frames loaded");
		}
		
		if (softwareTriggering){
			daServer.sendCommand("tfg arm");
			daServer.sendCommand("tfg start");
			waitingForExtStart = false;
		} else if (vmeStart) {
			daServer.sendCommand("tfg start");
		} else {
			daServer.sendCommand("tfg arm");
			waitingForExtStart = true;
		}
		
		Date d = new Date();
		startTime = d.getTime();
		elapsedTime = 0;
		started = true;
		notify();
	}

	@Override
	public void restart() throws DeviceException {
		cont();
	}

	protected void checkOKToSendCommand() throws DeviceException {
		if (daServer == null || !configured) {
			throw new DeviceException(getName() + "unconfigured");
		}
		if (!daServer.isConnected()) {
			throw new DeviceException(getName() + " not connected");
		}
	}

	@Override
	public void addFrameSet(int frameCount, double requestedDeadTime, double requestedLiveTime) {
		addFrameSet(frameCount, requestedDeadTime, requestedLiveTime, 0, 0, 0, 0);
	}

	@Override
	public void addFrameSet(int frameCount, double requestedDeadTime, double requestedLiveTime, int deadPort,
			int livePort, int deadPause, int livePause) {
		timeFrameProfile.addElement(new FrameSet(frameCount, requestedDeadTime, requestedLiveTime, deadPort, livePort,
				deadPause, livePause));
	}

	@Override
	public void clearFrameSets() {
		timeFrameProfile.removeAllElements();
		totalFrames = 0;
		framesLoaded = false;
	}

	public List<FrameSet> getFramesets() {
		return timeFrameProfile;
	}

	@Override
	public void loadFrameSets() throws DeviceException {
		
		checkOKToSendCommand();
		
		StringBuffer sb = new StringBuffer();

		sb.append("tfg setup-groups");
		if (extStart) {
			sb.append(" ext-start");
		}
		if (extInh) {
			sb.append(" ext-inh");
		}

		if ( autoReArm){
			sb.append(" auto-rearm ");
		} else {
			sb.append(" cycles ");
			sb.append(cycles);
		}
		sb.append("\n");

		totalExptTime = 0;
		totalFrames = 0;
		for (FrameSet frameSet : timeFrameProfile) {
			totalExptTime += (int) (frameSet.getRequestedLiveTime() + frameSet.getRequestedDeadTime())
					* frameSet.getFrameCount();
			totalFrames += frameSet.getFrameCount();
			sb.append(frameSet.getFrameCount());
			sb.append(" ");
			sb.append(frameSet.getRequestedDeadTime() / 1000);
			sb.append(" ");
			sb.append(frameSet.getRequestedLiveTime() / 1000);
			sb.append(" ");
			sb.append(frameSet.getDeadPort());
			sb.append(" ");
			sb.append(frameSet.getLivePort());
			sb.append(" ");
			sb.append(frameSet.getDeadPause());
			sb.append(" ");
			sb.append(frameSet.getLivePause());
			sb.append("\n");
		}
		sb.append("-1 0 0 0 0 0 0");

		daServer.sendCommand(sb.toString());
		totalExptTime *= cycles;
		framesLoaded = true;
			
		notifyIObservers(this, timeFrameProfile);
	}

	/**
	 * Count the specified time (in ms)
	 */
	@Override
	public synchronized void countAsync(double time) {
		if (daServer != null && daServer.isConnected()) {
			// The da.server tfg generate command expects cycles (integer)
			// frames(integer) deadTime (seconds, double) liveTime
			// (seconds, double) pause (integer). The incoming time is in mS.
			totalExptTime = (int) time * cycles;
			daServer.sendCommand("tfg generate 1 1 0.001 " + time / 1000.0 + " 0");
			daServer.sendCommand("tfg start");
			Date d = new Date();
			startTime = d.getTime();
			elapsedTime = 0;
			started = true;
			notify();
		}
	}

	@Override
	public void output(String file) {
		Object obj;
		int handle;
		if (host == null || user == null || password == null || host.equals("") || user.equals("")
				|| password.equals("")) {
			if ((obj = daServer.sendCommand("module open 'tfg_times' header")) != null) {
				if ((handle = ((Integer) obj).intValue()) >= 0) {
					daServer.sendCommand("read 0 0 0 " + totalFrames + " 8 1 " + " to-local-file '" + file + "' from "
							+ handle + " float " + remoteEndian);

					daServer.sendCommand("close " + handle);
				}
			}
		} else {
			if ((obj = daServer.sendCommand("module open 'tfg_times' header")) != null) {
				if ((handle = ((Integer) obj).intValue()) >= 0) {
					daServer.sendCommand("read 0 0 0 " + totalFrames + " 8 1 " + " to-remote-file '" + file + "' on '"
							+ host + "' user '" + user + "' password '" + password + "' from " + handle + " float "
							+ remoteEndian);

					daServer.sendCommand("close " + handle);
				}
			}
		}
	}

	/**
	 * Set attribute values for "Ext-Start", "Ext-Inhibit", "VME-Start" and "Auto-Continue"
	 * 
	 * @param attributeName
	 *            the attribute name
	 * @param value
	 *            the attribute value
	 * @throws DeviceException 
	 */
	@Override
	public void setAttribute(String attributeName, Object value) throws DeviceException {
		if (EXT_START_ATTR_NAME.equals(attributeName)) {
			extStart = ((Boolean) value).booleanValue();
		} else if (EXT_INHIBIT_ATTR_NAME.equals(attributeName)) {
			extInh = ((Boolean) value).booleanValue();
		} else if (AUTO_REARM_ATTR_NAME.equals(attributeName)) {
			autoReArm = ((Boolean) value).booleanValue();
		} else if (VME_START_ATTR_NAME.equals(attributeName)) {
			vmeStart = ((Boolean) value).booleanValue();
		} else if (SOFTWARE_START_AND_TRIG_ATTR_NAME.equals(attributeName)) {
			softwareTriggering = ((Boolean) value).booleanValue();
		} else if (AUTO_CONTINUE_ATTR_NAME.equals(attributeName)) {
			autoContinue = (Boolean) value ? 1 : 0;
			checkOKToSendCommand();
			daServer.sendCommand("tfg options auto-cont " + autoContinue);
		} else if ("User".equals(attributeName)) {
			user = (String) value;
		} else if ("Password".equals(attributeName)) {
			password = (String) value;
		} else if ("Host".equals(attributeName)) {
			host = (String) value;
		} else if ("Endian".equals(attributeName)) {
			remoteEndian = (String) value;
		}
	}

	/**
	 * Get attribute values for "Ext-Start", "Ext-Inhibit".
	 * 
	 * @param attributeName
	 *            the attribute name
	 * @return the attribute value
	 */
	@Override
	public Object getAttribute(String attributeName) {
		Object obj = null;
		if (EXT_START_ATTR_NAME.equals(attributeName)) {
			obj = new Boolean(extStart);
		} else if (EXT_INHIBIT_ATTR_NAME.equals(attributeName)) {
			obj = new Boolean(extInh);
		} else if (AUTO_REARM_ATTR_NAME.equals(attributeName)) {
			obj = new Boolean(autoReArm);
		} else if ("VME_Start".equals(attributeName)) {
			obj = new Boolean(extInh);
		} else if (AUTO_CONTINUE_ATTR_NAME.equals(attributeName)) {
			obj = new Boolean((autoContinue == 1) ? true : false);
		} else if (SOFTWARE_START_AND_TRIG_ATTR_NAME.equals(attributeName)) {
			obj = new Boolean(softwareTriggering);
		} else if ("User".equals(attributeName)) {
			obj = user;
		} else if ("Password".equals(attributeName)) {
			obj = password;
		} else if ("Host".equals(attributeName)) {
			obj = host;
		} else if ("Endian".equals(attributeName)) {
			obj = remoteEndian;
		} else if ("TotalFrames".equals(attributeName)) {
			obj = getTotalFrames();
		} else if ("TotalExptTime".equals(attributeName)) {
			obj = getTotalExptTime();
		} else if ("Version".equals(attributeName)) {
			return version;
		} else if ("FrameSets".equals(attributeName)) {
			return getFramesets();
		} else if ("FramesLoaded".equals(attributeName)) {
			return framesLoaded;
		} else if ("Cycles".equals(attributeName)) {
			return cycles;
		}

		return obj;
	}

	public double getCurrentLiveTime(int currentFrame) {
		int frameCount = 0;
		double liveTime = 0.0;
		for (FrameSet frameSet : timeFrameProfile) {
			frameCount += frameSet.getFrameCount();
			if (currentFrame <= frameCount) {
				liveTime = frameSet.getRequestedLiveTime();
				break;
			}
		}
		return liveTime / 1000; // return in seconds
	}

	public double getCurrentDeadTime(int currentFrame) {
		int frameCount = 0;
		double deadTime = 0.0;
		for (FrameSet frameSet : timeFrameProfile) {
			frameCount += frameSet.getFrameCount();
			if (currentFrame <= frameCount) {
				deadTime = frameSet.getRequestedDeadTime();
				break;
			}
		}
		return deadTime / 1000; // return in seconds
	}

	public int getCurrentFrames(int currentFrame) {
		int frameCount = 0;
		int nframes = 0;
		for (FrameSet frameSet : timeFrameProfile) {
			frameCount += frameSet.getFrameCount();
			if (nframes > 0 && frameSet.getDeadPause() != 0) {
				break;
			}
			if (currentFrame <= frameCount) {
				nframes += frameSet.getFrameCount();
			}
		}
		return nframes;
	}

	/**
	 * Do not use this method - it is used by the TFG object to create a monitor thread. Instead add an observer to the object
	 */
	@Override
	public synchronized void run() {
		while (true) {
			try {
				wait();
				while (started && monitorInBackground) {
					String status = getAcqStatus();
					String currentStatus;
					TimerStatus timerStatus;
					if (status.equals("IDLE")) {
						if (waitingForExtStart) {
							currentStatus = "WAITING";
						} else {
							totalCycles += cycles;
							currentStatus = status;
							started = false;
						}
						timerStatus = new TimerStatus(0, 0, 0, currentStatus, totalCycles, 0);
					} else {
						int percentComplete = 0;
						Date d = new Date();
						long timeNow = d.getTime();
						if (waitingForExtStart) {
							waitingForExtStart = false;
							startTime = timeNow;
						} else {
							if (!status.equals("PAUSED")) {
								elapsedTime += timeNow - startTime;
							}
							if (totalExptTime > 0.0) {
								percentComplete = (int) ((elapsedTime * 100) / totalExptTime);
							}
							startTime = timeNow;
						}
						int frame = getCurrentFrame();
						if ((frame / 2) * 2 == frame) {
							if (status.equals("PAUSED")) {
								currentStatus = "DEAD PAUSE";
							} else {
								currentStatus = "DEAD FRAME";
							}
						} else {
							if (status.equals("PAUSED")) {
								currentStatus = "LIVE PAUSE";
							} else {
								currentStatus = "LIVE FRAME";
							}
						}
						int currentFrame = (frame / 2) + 1;
						int currentCycle = cycles - getCurrentCycle();
						int cycleCount = totalCycles + currentCycle;

						timerStatus = new TimerStatus(elapsedTime, currentFrame, currentCycle, currentStatus,
								cycleCount, percentComplete);
					}
					notifyIObservers(this, timerStatus);
					wait(updateInterval);
				}
			} catch (InterruptedException iox) {
				logger.debug("tfg run thread interrupted");
			}
		}
	}

	// this method is only for Junit testing
	protected void setFail() {
		if (daServer != null && daServer.isConnected()) {
			daServer.sendCommand("Fail");
		}
	}

	public boolean isShowArmed() {
		return showArmed;
	}

	public void setShowArmed(boolean showArmed) {
		this.showArmed = showArmed;
	}

	public boolean isMonitorInBackground() {
		return monitorInBackground;
	}

	/**
	 * 
	 * @param monitorInBackground if true (default) the tfg state is monitored regularly and observers are notified of changes.
	 */
	public void setMonitorInBackground(boolean monitorInBackground) {
		this.monitorInBackground = monitorInBackground;
	}
	
	
}