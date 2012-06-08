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

package gda.device.temperature;

import gda.device.DeviceException;
import gda.device.Serial;
import gda.device.TemperatureStatus;
import gda.factory.FactoryException;
import gda.factory.Finder;
import gda.util.PollerEvent;

import java.text.NumberFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to control the Newtronic Micro 96 temperature controller. The Newtronic expects even parity, 1 stop bit and 7
 * data bits with a baud rate selected between 300 & 9600
 */
public class Newtronic extends TemperatureBase implements ReplyChecker {
	
	private static final Logger logger = LoggerFactory.getLogger(Newtronic.class);
	
	private final static double MAXTEMP = 999.0;

	private final static double MINTEMP = 0.0;

	private final static String SETPOINT = "SP";

	private final static String MEASURED = "PV";

	private final static String WORKING = "SP";

	private final static String MINSET = "LS";

	private final static String MAXSET = "HS";

	private final static String XPBAND = "XP";

	private final static char STX = '\02';

	private final static char ETX = '\03';

	private final static char EOT = '\04';

	private final static char ENQ = '\05';

	private final static char ACK = '\06';

	private final static char NAK = 21;

	private String unit = "00";

	private String group = "00";

	private double startTime = 0;

	private int uid = 0;

	private int gid = 0;

	private Serial serial = null;

	private String serialDeviceName;

	private String debugName;

	private AsynchronousReaderWriter arw = null;

	private String parity = Serial.PARITY_EVEN;

	private int baudRate = Serial.BAUDRATE_1200;

	private int stopBits = Serial.STOPBITS_1;

	private int byteSize = Serial.BYTESIZE_7;

	/**
	 * Constructor
	 */
	public Newtronic() {
		// These will be overwritten by the values specified in the XML
		// but are given here as defaults.
		lowerTemp = MINTEMP;
		upperTemp = MAXTEMP;
	}

	@Override
	public void configure() throws FactoryException {
		super.configure();
		logger.debug("Finding: " + serialDeviceName);
		if ((serial = (Serial) Finder.getInstance().find(serialDeviceName)) == null) {
			logger.error("Serial Device " + serialDeviceName + " not found");
		} else {
			// debugName is used in error output
			debugName = getClass().getName() + " " + getName();

			try {
				serial.setBaudRate(baudRate);
				serial.setStopBits(stopBits);
				serial.setByteSize(byteSize);
				serial.setParity(parity);
				serial.setReadTimeout(0);
				serial.flush();
				arw = new AsynchronousReaderWriter(serial);
				arw.setReplyChecker(this);
				arw.setCommandEndString("");
				setHWLowerTemp(upperTemp);
				setHWUpperTemp(lowerTemp);
				setPoint = getSetPoint();
				currentTemp = getCurrentTemperature();
				startPoller();
				configured = true;
			} catch (DeviceException de) {
				logger.error(debugName + ".configure() caught DeviceException" + de.getMessage());
			}
		}
	}

	@Override
	public void reconfigure() throws FactoryException {
		if (!configured)
			configure();
	}

	@Override
	public void close() throws DeviceException {
		if (serial != null)
			serial.close();
		arw = null;
		configured = false;
	}

	/**
	 * @param serialDeviceName
	 */
	public void setSerialDeviceName(String serialDeviceName) {
		this.serialDeviceName = serialDeviceName;
	}

	/**
	 * @return serialDeviceName
	 */
	public String getSerialDeviceName() {
		return serialDeviceName;
	}

	/**
	 * @param uid
	 */
	public void setUid(int uid) {
		this.uid = uid;
		if (uid >= 0 && uid <= 9)
			unit = "" + uid + uid;
	}

	/**
	 * @return uid
	 */
	public int getUid() {
		return uid;
	}

	/**
	 * @param gid
	 */
	public void setGid(int gid) {
		this.gid = gid;
		if (gid >= 0 && gid <= 9)
			group = "" + gid + gid;
	}

	/**
	 * @return gid
	 */
	public int getGid() {
		return gid;
	}

	/**
	 * Check a valid termination character has been received in the response.
	 * 
	 * @param reply
	 *            is the raw reply from the Eurotherm.
	 * @throws DeviceException
	 */
	private void checkReply(String reply) throws DeviceException {
		int irep = reply.charAt(0);
		logger.debug(debugName + "checkReply character: " + irep);

		if (reply.charAt(0) == NAK) {
			throw new DeviceException("Negative Acknowledgement received from Eurotherm");
		} else if (reply.charAt(0) != ACK) {
			throw new DeviceException("Spurious reply from Eurotherm");
		}
	}

	/**
	 * Decode the raw response from the Eurotherm, checking the checksum value and returns the decoded value in String
	 * form.
	 * 
	 * @param buffer
	 *            the response from the Eurotherm
	 * @return the decoded value
	 * @throws DeviceException
	 */
	private String decodeReply(String buffer) throws DeviceException {
		String response = null;
		int bcc = 0;
		int i;

		for (i = 1; i < buffer.length(); i++) {
			bcc ^= buffer.charAt(i);
			if (buffer.charAt(i) == ETX)
				break;
		}

		if (buffer.charAt(++i) == bcc) {
			if (buffer.indexOf("-") >= 0) {
				String temp = buffer.replace('-', '.');
				response = "-" + temp.substring(2, 8);
			} else {
				response = buffer.substring(2, 8);
			}
			logger.debug("Reply from Newtronic: " + response);
		} else {
			throw new DeviceException("Eurotherm replied with checksum error");
		}
		return response;
	}

	@Override
	public void doStop() throws DeviceException {
		// sendStop();
	}

	/**
	 * Encode a mnemonic & parameter value for setting the value in the eurotherm controller
	 * 
	 * @param mnemonic
	 *            is the two letter code.
	 * @param value
	 *            of the parameter
	 * @return the encoded command
	 */
	private String encode(String mnemonic, double value) {
		// Calculate the verification digit BCC as explained in EUROTHERM book
		// First calculate integer value
		String str = String.valueOf(value);
		String pack = mnemonic + str.substring(0, Math.min(6, str.length())) + ETX;
		char bcc = 0;

		for (int i = 0; i < pack.length(); i++)
			bcc ^= pack.charAt(i);

		// send the instruction to the EUROTHERM
		// value should be 5 digits only!!!

		return EOT + group + unit + '\n' + '\r' + STX + pack + bcc;
	}

	/**
	 * Encode a mnemonic when requesting values from the Eurotherm controller
	 * 
	 * @param mnemonic
	 *            is the two letter code.
	 * @return encode command
	 */
	private String encode(String mnemonic) {
		return EOT + group + unit + mnemonic + ENQ + '\n' + '\r';
	}

	/**
	 * Gets the current temperature by asking the actual Eurotherm
	 * 
	 * @return currentTemp
	 * @throws DeviceException
	 */
	@Override
	public double getCurrentTemperature() throws DeviceException {
		String str = encode(MEASURED);
		String reply = arw.sendCommandAndGetReply(str);
		currentTemp = java.lang.Double.valueOf(decodeReply(reply)).doubleValue();
		return currentTemp;
	}

	/**
	 * Gets the current temperature without asking the actual Eurotherm
	 * 
	 * @return temperature (in degreesC)
	 */
	public double getTemperature() {
		return currentTemp;
	}

	/**
	 * Get the curent set point temperature of the EuroTherm controller
	 * 
	 * @return the current setpoint temperature
	 * @throws DeviceException
	 * @throws NumberFormatException
	 */
	public double getSetPoint() throws DeviceException, NumberFormatException {
		String str = encode(WORKING);
		String reply = arw.sendCommandAndGetReply(str);
		return java.lang.Double.valueOf(decodeReply(reply)).doubleValue();
	}

	/**
	 * Get to the Xp parameter
	 * 
	 * @return the Xp paramter
	 * @throws DeviceException
	 */
	private double getXp() throws DeviceException {
		String str = encode(XPBAND);
		String reply = arw.sendCommandAndGetReply(str);
		return java.lang.Double.valueOf(decodeReply(reply)).doubleValue();
	}

	/**
	 * Executes when poll timer fires
	 * 
	 * @param pe
	 *            the polling event
	 */
	@Override
	public void pollDone(PollerEvent pe) {
		String stateString = null;
		String dataString = null;

		NumberFormat n = NumberFormat.getInstance();
		n.setMaximumFractionDigits(2);
		n.setGroupingUsed(false);

		logger.debug("Newtronic pollDone called");

		try {
			if (isAtTargetTemperature()) {
				busy = false;
				startHoldTimer();
			}

			logger.debug("busy is " + busy);
			if (busy)
				stateString = (targetTemp > currentTemp) ? "Heating" : "Cooling";
			else if (currentRamp > -1)
				stateString = "At temperature";
			else
				stateString = "Idle";
		} catch (DeviceException de) {
			logger.error("Exception " + de.getMessage());
		}
		if (timeSinceStart >= 0.0) {
			Date d = new Date();
			timeSinceStart = d.getTime() - startTime;
		}

		dataString = "" + n.format(timeSinceStart / 1000.0) + " " + currentTemp;

		TemperatureStatus ts = new TemperatureStatus(currentTemp, currentRamp, stateString, dataString);

		logger.debug("Newtronic notifying IObservers with " + ts);
		notifyIObservers(this, ts);

		// change poll time ??
	}

	/*
	 * Sends the ramps to the hardware.
	 */
	@Override
	protected void sendRamp(int which) {
		if (!rampList.isEmpty()) {
			// sendRate(1,
			// ((TemperatureRamp)rampList.get(which)).getRate());
			// sendLimit(1,
			// ((TemperatureRamp)rampList.get(which)).getEndTemperature());
		}
	}

	/**
	 * Set the temperature of the Eurotherm controller.
	 * 
	 * @param temp
	 *            is the required temparature.
	 * @throws DeviceException
	 */
	@Override
	public void setTargetTemperature(double temp) throws DeviceException {
		if (temp > upperTemp || temp < lowerTemp)
			throw new DeviceException("Trying to set temperature outside of limits");
		if (busy)
			throw new DeviceException(debugName + " is already ramping to temerature");

		String str = encode(SETPOINT, temp);
		String reply = arw.sendCommandAndGetReply(str);
		checkReply(reply);
		setPoint = getSetPoint();
		count = 0;
		busy = true;
	}

	/**
	 * Set the proportional band control paramter Xp for the controller
	 * 
	 * @param temp
	 *            (the Xp temperature degrees)
	 * @throws DeviceException
	 */
	private void setXpControl(double temp) throws DeviceException {
		String str = encode(XPBAND, temp);
		String reply = arw.sendCommandAndGetReply(str);
		checkReply(reply);
	}

	/**
	 * Initiate the programmed temperature ramp in the Lauda bath.
	 * 
	 * @throws DeviceException
	 */
	public void sendStart() throws DeviceException {
		if (busy)
			throw new DeviceException("Eurotherm is already ramping to temperature");
		// if (!arw.sendCommandAndGetReply("START").equals(OKREPLY))
		// throw new DeviceException ("Eurotherm command failure");
		busy = true;
	}

	@Override
	public void doStart() throws DeviceException {
		Date d = new Date();
		startTime = d.getTime();
		sendStart();
	}

	@Override
	protected void startNextRamp() throws DeviceException {
		currentRamp++;
		logger.debug("startNextRamp called currentRamp now " + currentRamp);
		if (currentRamp < rampList.size()) {
			sendRamp(currentRamp);
			sendStart();
		} else {
			stop();
		}
	}

	@Override
	public boolean bufferContainsReply(StringBuffer buffer) {
		boolean reply = false;
		int len = buffer.length();

		if (buffer.charAt(0) == ACK || buffer.charAt(0) == NAK || (len > 1 && buffer.charAt(len - 2) == ETX)) {
			reply = true;
		}
		return reply;
	}

	@Override
	public void setAttribute(String name, Object value) throws DeviceException {
		if (name.equalsIgnoreCase("Xp")) {
			setXpControl(((Double) value).doubleValue());
		} else if (name.equalsIgnoreCase("Accuracy")) {
			setAccuracy(((Double) value).doubleValue());
		}
	}

	/**
	 * Overrides the DeviceBase method to get current DSC dataset name.
	 * 
	 * @param name
	 *            the name of the attribute requested
	 * @return the attribute requested
	 * @throws DeviceException
	 */
	@Override
	public Object getAttribute(String name) throws DeviceException {
		if (name.equalsIgnoreCase("Xp"))
			return new Double(getXp());

		return null;
	}

	@Override
	public void startTowardsTarget() throws DeviceException {
		logger.error("Warning: startTowardsTarget not implemented in Newtronic");
	}

	@Override
	protected void setHWLowerTemp(double lowerTemp) throws DeviceException {
		String str = encode(MINSET, lowerTemp);
		String reply = arw.sendCommandAndGetReply(str);
		checkReply(reply);
	}

	@Override
	protected void setHWUpperTemp(double upperTemp) throws DeviceException {
		String str = encode(MAXSET, upperTemp);
		String reply = arw.sendCommandAndGetReply(str);
		checkReply(reply);
	}

	@Override
	public void hold() throws DeviceException {
		// TODO Auto-generated method stub

	}

	@Override
	public void runRamp() throws DeviceException {
		// TODO Auto-generated method stub

	}

}
