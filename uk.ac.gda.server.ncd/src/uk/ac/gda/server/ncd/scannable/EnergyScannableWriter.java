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

package uk.ac.gda.server.ncd.scannable;

import gda.data.scan.datawriter.SelfCreatingLink;
import gda.data.scan.datawriter.scannablewriter.NumberComponentWriter;
import gda.data.scan.datawriter.scannablewriter.SingleScannableWriter;

import java.util.Collection;

import org.nexusformat.NeXusFileInterface;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

public class EnergyScannableWriter extends SingleScannableWriter {

	private Double uncertaintyFraction = null;

	public Double getUncertaintyFraction() {
		return uncertaintyFraction;
	}

	public void setUncertaintyFraction(final Double uncertaintyFraction) {
		this.uncertaintyFraction = uncertaintyFraction;
	}

	protected class ComponentWriterWithUncertainty extends NumberComponentWriter {

		NumberComponentWriter trueEnergyWriter = new NumberComponentWriter() {
			@Override
			protected void addCustomAttributes(final NeXusFileInterface file, final String scannableName,
					final String componentName) throws NexusException {
				final String uncertaintiesName = uncertaintiesPath.substring(uncertaintiesPath.lastIndexOf("/") + 1);
				file.putattr("uncertainties", uncertaintiesName.getBytes(), NexusFile.NX_CHAR);
			}
		};

		private String uncertaintiesPath = "";

		@Override
		protected double[] getComponentSlab(final Object pos) {
			final double[] posArr = super.getComponentSlab(pos);
			posArr[0] *= uncertaintyFraction;
			return posArr;
		}

		@Override
		public Collection<SelfCreatingLink> makeComponent(final NeXusFileInterface file, final int[] dim,
				final String path, final String scannableName, final String componentName, final Object pos,
				final String unit) throws NexusException {
			uncertaintiesPath = path + "_error";
			super.makeComponent(file, dim, uncertaintiesPath, scannableName, null, pos, unit);
			return trueEnergyWriter.makeComponent(file, dim, path, scannableName, componentName, pos, unit);
		}

		@Override
		public void writeComponent(final NeXusFileInterface file, final int[] start, final String path,
				final String scannableName, final String componentName, final Object pos) throws NexusException {
			super.writeComponent(file, start, uncertaintiesPath, scannableName, null, pos);
			trueEnergyWriter.writeComponent(file, start, path, scannableName, componentName, pos);
		}
	}

	@Override
	protected void resetComponentWriters() {
		super.resetComponentWriters();
		if (uncertaintyFraction != null) {
			getCwriter().put("energy", new ComponentWriterWithUncertainty());
		}
	}
}