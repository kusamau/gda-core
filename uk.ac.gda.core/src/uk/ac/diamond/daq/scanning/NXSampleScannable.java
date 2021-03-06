/*-
 * Copyright © 2018 Diamond Light Source Ltd.
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

package uk.ac.diamond.daq.scanning;

import org.eclipse.dawnsci.nexus.INexusDevice;
import org.eclipse.dawnsci.nexus.NXsample;
import org.eclipse.dawnsci.nexus.NexusException;
import org.eclipse.dawnsci.nexus.NexusScanInfo;
import org.eclipse.dawnsci.nexus.builder.NexusObjectProvider;
import org.eclipse.scanning.api.IScannable;
import org.eclipse.scanning.api.points.IPosition;
import org.eclipse.scanning.api.scan.ScanningException;

/**
 * Where NXSampleSCannable is currently used, NXObjectScannable may now be used, since the generic form
 * works with any kind of NXobject, not just NXsample.
 *
 * For example:
 *
 *   xpdfNxSample = NXSampleScannable("xpdfNxSample","sample",self.sample.getNexus())
 *
 * can be replaced with
 *
 *   xpdfNxSample = NXObjectScannable("xpdfNxSample","sample",self.sample.getNexus())
 *
 */
@Deprecated
public class NXSampleScannable implements IScannable<Object>, INexusDevice<NXsample> {

	private String scannableName;
	private NXObjectProvider<NXsample> provider;

	public NXSampleScannable(String scannableName, String sampleName, NXsample sampleNode) {
		this.scannableName = scannableName;
		provider = new NXObjectProvider<NXsample>(sampleName, sampleNode);
	}

	public void updateNode(String sampleName, NXsample sampleNode) {
		provider = new NXObjectProvider<NXsample>(sampleName, sampleNode);
	}

	@Override
	public void setLevel(int level) {
		//level does nothing here
	}

	@Override
	public int getLevel() {
		return 0;
	}

	@Override
	public String getName() {
		return scannableName;
	}

	@Override
	public void setName(String name) {
		this.scannableName = name;

	}

	@Override
	public NexusObjectProvider<NXsample> getNexusProvider(NexusScanInfo info) throws NexusException {
		return provider;
	}

	@Override
	public Object getPosition() throws ScanningException {
		return null;
	}

	@Override
	public Object setPosition(Object value, IPosition position) throws ScanningException {
		return null;
	}
}
