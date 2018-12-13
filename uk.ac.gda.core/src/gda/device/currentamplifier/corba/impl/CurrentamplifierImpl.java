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

package gda.device.currentamplifier.corba.impl;

import java.io.Serializable;

import org.omg.CORBA.Any;

import gda.device.CurrentAmplifier;
import gda.device.DeviceException;
import gda.device.corba.CorbaDeviceException;
import gda.device.currentamplifier.corba.CorbaCurrentAmplifierPOA;
import gda.device.currentamplifier.corba.CorbaStatus;
import gda.device.scannable.corba.impl.ScannableImpl;
import gda.factory.corba.CorbaFactoryException;

/**
 * A server side implementation for a distributed EnumPositioner class
 */

public class CurrentamplifierImpl extends CorbaCurrentAmplifierPOA {
	// reference to implementation objects.
	private CurrentAmplifier amp;
	private ScannableImpl scannableImpl;

	private org.omg.PortableServer.POA poa;

	/**
	 * Create server side implementation to the CORBA package.
	 *
	 * @param amp
	 *            the CurrentAmplifier implementation object
	 * @param poa
	 *            the portable object adapter
	 */
	public CurrentamplifierImpl(CurrentAmplifier amp, org.omg.PortableServer.POA poa) {
		this.amp = amp;
		this.poa = poa;
		scannableImpl = new ScannableImpl(amp, poa);
	}

	/**
	 * Get the implementation object
	 *
	 * @return the CurrentAmplifier implementation object
	 */
	public CurrentAmplifier _delegate() {
		return amp;
	}

	/**
	 * Set the implementation object.
	 *
	 * @param amp
	 *            set the CurrentAmplifier implementation object
	 */
	public void _delegate(CurrentAmplifier amp) {
		this.amp = amp;
	}

	@Override
	public org.omg.PortableServer.POA _default_POA() {
		return (poa != null) ? poa : super._default_POA();
	}

	@Override
	public void asynchronousMoveTo(Any arg0) throws CorbaDeviceException {
		try {
			amp.asynchronousMoveTo(arg0);
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public Any getPosition() throws CorbaDeviceException {
		org.omg.CORBA.Any any = org.omg.CORBA.ORB.init().create_any();
		try {
			java.lang.Object obj = amp.getPosition();
			any.insert_Value((Serializable) obj);
		} catch (DeviceException ex) {
			throw new CorbaDeviceException(ex.getMessage());
		}
		return any;
	}

	@Override
	public boolean isBusy() throws CorbaDeviceException {
		try {
			return amp.isBusy();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void atScanEnd() throws CorbaDeviceException {
		try {
			amp.atScanEnd();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void atScanStart() throws CorbaDeviceException {
		try {
			amp.atScanStart();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void setGain(String position) throws CorbaDeviceException {
		try {
			amp.setGain(position);
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void setMode(String mode) throws CorbaDeviceException {
		try {
			amp.setMode(mode);
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}

	}

	@Override
	public void configure() throws CorbaFactoryException {
		scannableImpl.configure();
	}

	@Override
	public boolean isConfigured() throws CorbaDeviceException {
		return scannableImpl.isConfigured();
	}

	@Override
	public void reconfigure() throws CorbaFactoryException {
		scannableImpl.reconfigure();
	}

	@Override
	public void close() throws CorbaDeviceException {
		scannableImpl.close();
	}

	@Override
	public void setAttribute(String attributeName, org.omg.CORBA.Any value) throws CorbaDeviceException {
		scannableImpl.setAttribute(attributeName, value);
	}

	@Override
	public org.omg.CORBA.Any getAttribute(String attributeName) throws CorbaDeviceException {
		return scannableImpl.getAttribute(attributeName);
	}

	@Override
	public String _toString() {
		return scannableImpl._toString();
	}

	@Override
	public void atPointEnd() throws CorbaDeviceException {
		scannableImpl.atPointEnd();
	}

	@Override
	public void atPointStart() throws CorbaDeviceException {
		scannableImpl.atPointStart();
	}

	@Override
	public void atScanLineEnd() throws CorbaDeviceException {
		scannableImpl.atScanLineEnd();
	}

	@Override
	public void atScanLineStart() throws CorbaDeviceException {
		scannableImpl.atScanLineStart();
	}

	@Override
	public String[] getExtraNames() {
		return scannableImpl.getExtraNames();
	}

	@Override
	public String[] getInputNames() {
		return scannableImpl.getInputNames();
	}

	@Override
	public int getLevel() {
		return scannableImpl.getLevel();
	}

	@Override
	public String[] getOutputFormat() {
		return scannableImpl.getOutputFormat();
	}

	@Override
	public Any checkPositionValid(Any arg0) throws CorbaDeviceException {
		return scannableImpl.checkPositionValid(arg0);
	}

	@Override
	public void moveTo(Any arg0) throws CorbaDeviceException {
		scannableImpl.moveTo(arg0);
	}

	@Override
	public void setExtraNames(String[] arg0) {
		scannableImpl.setExtraNames(arg0);
	}

	@Override
	public void setInputNames(String[] arg0) {
		scannableImpl.setInputNames(arg0);
	}

	@Override
	public void setLevel(int arg0) {
		scannableImpl.setLevel(arg0);
	}

	@Override
	public void setOutputFormat(String[] arg0) {
		scannableImpl.setOutputFormat(arg0);
	}

	@Override
	public void waitWhileBusy() throws CorbaDeviceException {
		scannableImpl.waitWhileBusy();
	}

	@Override
	public boolean isAt(Any arg0) throws CorbaDeviceException {
		try {
			return this.amp.isAt(arg0.extract_Value());
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void atEnd() throws CorbaDeviceException {
		scannableImpl.atEnd();
	}

	@Override
	public void atStart() throws CorbaDeviceException {
		scannableImpl.atStart();
	}

	@Override
	public double getCurrent() throws CorbaDeviceException {
		try {
			return amp.getCurrent();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public String getGain() throws CorbaDeviceException {
		try {
			return amp.getGain();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public String[] getGainPositions() throws CorbaDeviceException {
		try {
			return amp.getGainPositions();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void listGains() throws CorbaDeviceException {
		try {
			amp.listGains();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public String getMode() throws CorbaDeviceException {
		try {
			return amp.getMode();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public String[] getModePositions() throws CorbaDeviceException {
		try {
			return amp.getModePositions();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public CorbaStatus getStatus() throws CorbaDeviceException {
		try {
			return CorbaStatus.from_int(amp.getStatus().ordinal());
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public String[] getGainUnits() throws CorbaDeviceException {
		try {
			return amp.getGainUnits();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void setGainUnit(String unit) throws CorbaDeviceException {
		try {
			amp.setGainUnit(unit);
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public String getGainUnit() throws CorbaDeviceException {
		try {
			return amp.getGainUnit();
		} catch (DeviceException e) {
			throw new CorbaDeviceException(e.getMessage());
		}
	}

	@Override
	public void stop() throws CorbaDeviceException {
		scannableImpl.stop();
	}

	@Override
	public int getProtectionLevel() throws CorbaDeviceException {
		return scannableImpl.getProtectionLevel();
	}

	@Override
	public void setProtectionLevel(int newLevel) throws CorbaDeviceException {
		scannableImpl.setProtectionLevel(newLevel);
	}

	@Override
	public void atLevelMoveStart() throws CorbaDeviceException {
		scannableImpl.atLevelMoveStart();
	}

	@Override
	public void atCommandFailure() throws CorbaDeviceException {
		scannableImpl.atCommandFailure();
	}

	@Override
	public String toFormattedString() throws CorbaDeviceException {
		return scannableImpl.toFormattedString();
	}

	@Override
	public void atLevelStart() throws CorbaDeviceException {
		scannableImpl.atLevelStart();
	}

	@Override
	public void atLevelEnd() throws CorbaDeviceException {
		scannableImpl.atLevelEnd();
	}


}