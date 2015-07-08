/*-
 * Copyright © 2010 Diamond Light Source Ltd.
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

package gda.factory.corba.util;

/**
 * Interface that indicates that a CORBA adapter can be wrapped for RBAC.
 */
public interface RbacEnabledAdapter {

	/**
	 * Returns the underlying CORBA {@link org.omg.CORBA.Object Object} for the adapter.
	 */
	public org.omg.CORBA.Object getCorbaObject();

	/**
	 * Returns the {@link NetService} used by the adapter.
	 */
	public NetService getNetService();

}
