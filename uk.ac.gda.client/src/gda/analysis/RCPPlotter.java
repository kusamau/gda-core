/*-
 * Copyright © 2009 Diamond Light Source Ltd., Science and Technology
 * Facilities Council
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

package gda.analysis;

import gda.data.PathConstructor;

import java.io.File;
import java.io.IOException;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.RawBinarySaver;

/**
 * This class is a wrapper for all the static methods which allow easy plotting to the new plot panel.
 */
public class RCPPlotter extends SDAPlotter {

	/**
	 * Setup the logging facilities
	 */
	private static final Logger logger = LoggerFactory.getLogger(RCPPlotter.class);

	protected static String saveTempFile(DataHolder tHolder,
			IDataset dataset) throws ScanFileHolderException {

		try {
			java.io.File tmpFile = File.createTempFile("tmp_img",".raw");
			String dirName = PathConstructor.createFromRCPProperties() + PlotServer.GRIDVIEWDIR;
			java.io.File directory = new java.io.File(dirName);
			if (!directory.exists())
				directory.mkdir();
			String rawFilename = PathConstructor.createFromRCPProperties() + PlotServer.GRIDVIEWDIR + tmpFile.getName();
			tHolder.setDataset("Data", dataset);
			new RawBinarySaver(rawFilename).saveFile(tHolder);
			return rawFilename;
		} catch (IOException e) {
			logger.error("Couldn't load file: {}", e);
			throw new ScanFileHolderException("Couldn't load file",e);
		}
	}


}
