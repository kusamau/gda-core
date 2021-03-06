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

package gda.jython;

import java.util.Collection;

public interface AliasedCommandProvider {
	/**
	 * Returns the list of alias in the GDA command server
	 * @return the aliased commands
	 */
	public Collection<String> getAliasedCommands();

	/**
	 * Returns the list of variable argument alias in the GDA command server
	 * @return the list of variable argument alias
	 */
	public Collection<String> getAliasedVarargCommands();

	/**
	 * Checks if the the given command is aliased (either vararg or not)
	 * @param command the string to check
	 * @return whether the command is aliased or not
	 */
	public boolean hasAlias(String command);
}
