/*-
 * Copyright © 2009 Diamond Light Source Ltd., Science and Technology
 * Facilities Council
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

package gda.jython.gui;

/**
 * Class to contain the response from jython interpreter autocompletion command
 */
public class AutoCompletionParts {
	/**
	 * Name of object
	 */
	public String name;
	/**
	 * Type of object 
	 */
	public int type;
	/**
	 * 
	 */
	public String helpDoc;
	/**
	 * 
	 */
	public String args;

	AutoCompletionParts(String name, int type, String helpDoc, String args) {
		this.name = name;
		this.type = type;
		this.helpDoc = helpDoc;
		this.args = args;
	}
}
