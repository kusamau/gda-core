/*-
 * Copyright © 2009 Diamond Light Source Ltd.
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

package gda.jython.commands;

/**
 * Input Class - Deprecated. Use InputCommands instead. But used in Scripts so must remain until all scripts are changed.
 */
public class Input {
	/**
	 * For use within scripts to request input from the Jython terminal
	 *
	 * @param promptString *
	 * @return Object
	 * @throws InterruptedException
	 */
	public static Object requestInput(String promptString) throws InterruptedException {
		return InputCommands.requestInput(promptString);
	}
}
