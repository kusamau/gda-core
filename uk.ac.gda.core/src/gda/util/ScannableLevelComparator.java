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

package gda.util;

import gda.device.Scannable;

import java.util.Comparator;

/**
 * A {@link Comparator} for {@link Scannable}s that allows sorting by level.
 */
public class ScannableLevelComparator implements Comparator<Scannable> {

	@Override
	public int compare(Scannable scannable1, Scannable scannable2) {
		return scannable1.getLevel() - scannable2.getLevel();
	}

}
