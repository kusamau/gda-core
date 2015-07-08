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

package gda.scan;

import gda.scan.Scan.ScanStatus;

/**
 * An interface to allow child scans to access their parant.
 * <p>
 * Note: most scans will end up implementing this, even though a particular instance may be a child.
 */
interface ScanParent { // TODO: Ideally this should not extend Scan

	/**
	 * To allow nests of scans to share a common status with each other.
	 *
	 * @param status
	 */
	public void setStatus(ScanStatus status);

}
