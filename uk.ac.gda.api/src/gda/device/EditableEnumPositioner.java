/*-
 * Copyright © 2011 Diamond Light Source Ltd.
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

package gda.device;

import java.util.Collection;

/**
 * For positioners who wish to allow the users to dynamically change the position labels at runtime.
 */
public interface EditableEnumPositioner extends EnumPositioner {

	/**
	 * Sets the positions of this positioner.
	 *
	 * @param positions
	 *            the positions
	 */
	public void setPositions(String[] positions);

	/**
	 * Sets the positions from a collection
	 *
	 * @param positions
	 *            the positions
	 */
	public void setPositions(Collection<String> positions);
}
