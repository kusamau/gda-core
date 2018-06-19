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

package gda.data.metadata;

import gda.factory.FindableBase;

/**
 * A NexusMetadataEntry references to a named metadata entry with an access name
 * comprising a concatenated string of colon-separated (":") groups with each group possessing
 * a name and an NXclass separated by a percentage sign ("%")
 */
public class NexusMetadataEntry extends FindableBase {
	private String accessName = "";

	/**
	 * Gets the access name.
	 *
	 * @return accessName
	 */
	public String getAccessName() {
		return accessName;
	}

	/**
	 * Sets to access name.
	 *
	 * @param accessName
	 */
	public void setAccessName(String accessName) {
		this.accessName = accessName;
	}
}
