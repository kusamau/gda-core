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
import gda.device.DeviceException;
import gda.device.Scannable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;

import org.apache.commons.lang.ArrayUtils;
import org.nexusformat.NeXusFileInterface;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleStringScannableWriter extends SimpleSingleScannableWriter {
	private static final Logger logger = LoggerFactory.getLogger(SimpleSingleScannableWriter.class);

	int stringlength;
	int rank;
	
	@Override
	public Collection<? extends SelfCreatingLink> makeScannable(NeXusFileInterface file, Scannable s, Object position,
			int[] dim) throws NexusException {
		
		String name = enterLocation(file);
		
		try {
			stringlength = 127;
			byte[] slab = positionToWriteableSlab(position, s);
			
			if (Arrays.equals(dim, new int[] {1})) {
				stringlength = slab.length;
			} else if (slab.length > (stringlength - 10)) {
				stringlength = slab.length+10;
			}
			
			if (dim[dim.length-1] == 1) {
				dim[dim.length-1] = stringlength;
			} else {
				dim = ArrayUtils.add(dim, stringlength);
			}
			rank = dim.length;
			
			file.makedata(name, NexusFile.NX_CHAR, rank, dim);
			file.opendata(name);
		
			file.putslab(slab, nulldimfordim(dim), onedimfordim(dim));
		} catch (DeviceException e) {
			logger.error("error converting scannable data", e);
		}
		
		file.closedata();

		leaveLocation(file);
		return new Vector<SelfCreatingLink>();
	}

	@Override
	protected byte[] positionToWriteableSlab(Object position, Scannable s) throws DeviceException {
		return ArrayUtils.add(((String) position).getBytes(Charset.forName("UTF-8")),(byte) 0);
	}
	
	@Override
	protected int[] onedimfordim(int[] dim) {
		int[] onedimfordim = super.onedimfordim(dim);
		if (onedimfordim.length < rank) 
			return ArrayUtils.add(onedimfordim, stringlength);
		onedimfordim[onedimfordim.length-1] = stringlength;
		return onedimfordim;
	}
}
