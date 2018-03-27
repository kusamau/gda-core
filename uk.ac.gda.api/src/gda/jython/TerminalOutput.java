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

package gda.jython;

import java.io.Serializable;

public class TerminalOutput implements Serializable {

	private final String output;

	public TerminalOutput(String output) {
		this.output = output;
	}

	public String getOutput() {
		return output;
	}

	@Override
	public String toString() {
		// Strips trailing newlines
		return "TerminalOutput [" + output.replaceFirst("\n+$", "") + "]";
	}

}
