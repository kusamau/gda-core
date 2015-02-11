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

package gda.data.scan.datawriter.scannablewriter;

import gda.data.scan.datawriter.SelfCreatingLink;

import java.util.Collection;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.exception.NotANumberException;
import org.nexusformat.NeXusFileInterface;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NumberComponentWriter extends DefaultComponentWriter {

	/** Logger */
	private static final Logger LOGGER = LoggerFactory.getLogger(NumberComponentWriter.class);

	public NumberComponentWriter() {
		// no op
	}

	@Override
	protected double[] getComponentSlab(final Object pos) {
		if (!(pos instanceof Number)) {
			throw new NotANumberException();
		}
		return new double[] { ((Number) pos).doubleValue() };
	}

	@Override
	public Collection<SelfCreatingLink> makeComponent(final NeXusFileInterface file, final int[] dim,
			final String path, final String scannableName, final String componentName, final Object pos,
			final String unit) throws NexusException {

		final String name = enterLocation(file, path);

		try {
			file.opendata(name);
			LOGGER.info("found dataset " + path + " exists already when trying to create it for " + scannableName
					+ ". This may not be a problem provided the data written is the same");
			leaveLocation(file);
			return null;

		} catch (final NexusException e) {
			// this is normal case!
		}

		final int[] makedatadim = makedatadimfordim(dim);
		file.makedata(name, NexusFile.NX_FLOAT64, makedatadim.length, makedatadim);
		file.opendata(name);

		if (componentName != null) {
			file.putattr("local_name", (scannableName + "." + componentName).getBytes(UTF8), NexusFile.NX_CHAR);
		}

		final StringBuilder axislist = new StringBuilder(dim.length * 3 + 1).append('1');
		for (int j = 2; j <= dim.length; j++) {
			axislist.append(',').append(j);
		}
		file.putattr("axis", axislist.toString().getBytes(UTF8), NexusFile.NX_CHAR);

		if (StringUtils.isNotBlank(unit)) {
			file.putattr("units", unit.getBytes(UTF8), NexusFile.NX_CHAR);
		}

		addCustomAttributes(file, scannableName, componentName);
		file.putslab(getComponentSlab(pos), nulldimfordim(dim), slabsizedimfordim(dim));

		final SelfCreatingLink scl = new SelfCreatingLink(file.getdataID());

		file.closedata();
		leaveLocation(file);

		return Collections.singleton(scl);
	}
}
