/*-
 * Copyright © 2014 Diamond Light Source Ltd.
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

package gda.device.detector.nxdata;

import gda.device.DeviceException;
import gda.device.detector.NXDetectorData;

import java.util.List;

public class NXDetectorSerialAppender implements NXDetectorDataAppender {
	
	final private List<NXDetectorDataAppender> appenders;

	public NXDetectorSerialAppender(List<NXDetectorDataAppender> appenders) {
		this.appenders = appenders;
	}

	@Override
	public void appendTo(NXDetectorData data, String detectorName) throws DeviceException {
		for (NXDetectorDataAppender appender : appenders) {
			appender.appendTo(data, detectorName);
		}
	}

}