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

package gda.device.currentamplifier;

import java.util.ArrayList;
import java.util.List;

import org.python.core.PyString;

import gda.device.CurrentAmplifier;
import gda.device.DeviceException;
import gda.device.scannable.ScannableBase;

/**
 * Base class for the CurrentAmplifier interface
 */
public abstract class CurrentAmplifierBase extends ScannableBase implements CurrentAmplifier {

	protected final List<String> gainPositions = new ArrayList<>();
	protected final List<String> gainUnits = new ArrayList<>();
	protected final List<String> modePositions = new ArrayList<>();

	@Override
	public String[] getGainPositions() throws DeviceException {
		String[] array = new String[gainPositions.size()];
		return gainPositions.toArray(array);
	}

	@Override
	public String[] getGainUnits() throws DeviceException {
		String[] array = new String[gainUnits.size()];
		return gainUnits.toArray(array);
	}

	@Override
	public String[] getModePositions() throws DeviceException {
		String[] array = new String[modePositions.size()];
		return modePositions.toArray(array);
	}

	@Override
	public void asynchronousMoveTo(Object position) throws DeviceException {
		throwExceptionIfInvalidTarget(position);

		this.setGain(position.toString());
	}

	@Override
	public Object getPosition() throws DeviceException {
		return this.getCurrent();
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public String checkPositionValid(Object position) {
		if (position instanceof String || position instanceof PyString) {
			if (gainPositions.contains(position))
				return null;
		}
		return position.toString() + "not in the list of gain positions";
	}
}
