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

package gda.data.scan.datawriter;

import org.nexusformat.NeXusFileInterface;
import org.nexusformat.NexusException;

public class ExternalNXlink extends SelfCreatingLink {
	String name, url;

	public ExternalNXlink(String name, String url) {
		super(null);
		this.name = name;
		this.url = url;
	}

	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public
	void create(NeXusFileInterface file) throws NexusException {
		file.linkexternaldataset(name, url);
	}
}