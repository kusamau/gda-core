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

package gda.jython.commandinfo;

import java.io.Serializable;

public class CommandThreadEvent implements Serializable {
	
	private CommandThreadEventType event;
	private ICommandThreadInfo info;
	
	public CommandThreadEvent(CommandThreadEventType event, ICommandThreadInfo info) {
		this.event = event;
		this.info = info;
	}

	public CommandThreadEventType getEventType() {
		return event;
	}
	
	public ICommandThreadInfo getInfo() {
		return info;
	}
	
	public void setEventType(CommandThreadEventType event) {
		this.event = event;
	}
	
	public void setInfo(ICommandThreadInfo info) {
		this.info = info;
	}
}
