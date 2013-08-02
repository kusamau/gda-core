/*-
 * Copyright © 2013 Diamond Light Source Ltd.
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

package gda.device.motor.simplemotor;

import gda.device.DeviceException;

public interface SimpleIndexedMotorController {

	void stop()throws DeviceException;

	boolean isBusy()throws DeviceException;

	void moveTo(double position, int index)throws DeviceException;

	double getPosition(int index) throws DeviceException;

	void setSpeed(double speed, int index) throws DeviceException;

	double getSpeed(int index) throws DeviceException;

}
