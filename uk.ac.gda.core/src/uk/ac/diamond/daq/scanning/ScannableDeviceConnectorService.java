/*-
 * Copyright © 2016 Diamond Light Source Ltd.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.scanning.api.IScannable;
import org.eclipse.scanning.api.device.IScannableDeviceService;
import org.eclipse.scanning.api.points.IPosition;
import org.eclipse.scanning.api.scan.ScanningException;

import gda.data.scan.datawriter.NexusDataWriter;
import gda.data.scan.datawriter.scannablewriter.ScannableWriter;
import gda.device.Detector;
import gda.device.Scannable;
import gda.factory.Findable;
import gda.factory.Finder;
import gda.jython.InterfaceProvider;

/**
 * Implementation of {@link IScannableDeviceService} for GDA8 devices.
 * {@link #getScannable(String)} using {@link Finder} to find {@link Findable}
 * @author Matthew Gerring, Matthew Dickie
 */
public class ScannableDeviceConnectorService implements IScannableDeviceService {

	/**
	 * A simple class to adapt an GDA8 {@link Scannable} to the {@link IScannable} API.
	 * May be deleted if not used in future.
	 */
	public class ScannableAdapter implements IScannable<Object> {

		private Scannable scannable;

		public ScannableAdapter(Scannable scannable) {
			this.scannable = scannable;
		}

		@Override
		public void setLevel(int level) {
			scannable.setLevel(level);
		}

		@Override
		public int getLevel() {
			return scannable.getLevel();
		}

		@Override
		public String getName() {
			return scannable.getName();
		}

		@Override
		public void setName(String name) {
			scannable.setName(name);
		}

		@Override
		public Object getPosition() throws Exception {
			return scannable.getPosition();
		}

		@Override
		public void setPosition(Object value) throws Exception {
			scannable.moveTo(value);
		}

		@Override
		public void setPosition(Object value, IPosition position) throws Exception {
			scannable.moveTo(value);
		}
	}

	private Map<String, IScannable<?>> scannables = null;

	@Override
	public <T> IScannable<T> getScannable(String name) throws ScanningException {
		if (scannables == null)
			scannables = new HashMap<>();

		// first check whether this scannable exists in the cache
		if (scannables.containsKey(name)) {
			@SuppressWarnings("unchecked")
			IScannable<T> scannable = (IScannable<T>) scannables.get(name);
			if (scannable == null)
				throw new ScanningException("Cannot find scannable with name " + name);
			return scannable;
		}

		// if not, see if we can find it using the Finder mechanism
		Scannable scannable = null;
		Finder finder = Finder.getInstance();
		Findable found = finder.findNoWarn(name);
		if (found instanceof Scannable) {
			scannable = (Scannable) found;
		}

		if (scannable == null) {
			Object jythonObject = InterfaceProvider.getJythonNamespace().getFromJythonNamespace(name);
			if (jythonObject instanceof Scannable) {
				scannable = (Scannable) jythonObject;
			}
		}

		if (scannable == null) {
			throw new ScanningException("Cannot find scannable with name " + name);
		}

		@SuppressWarnings("unchecked")
		IScannable<T> s = (IScannable<T>) new ScannableNexusWrapper<>(scannable);
		scannables.put(name, s);
		return s;
	}

	@Override
	public List<String> getScannableNames() throws ScanningException {

		ArrayList<Findable> findableRefs = Finder.getInstance().listAllObjects(Scannable.class.getName());
		ArrayList<String> findableNames = new ArrayList<String>();
		for (Findable findable : findableRefs) {
			if (findable instanceof Detector) continue; // Not them
			String findableName = findable.getName();
			findableName = findableName.substring(findableName.lastIndexOf(".") + 1);
			findableNames.add(findableName);
		}
		return findableNames;
	}

	@Override
	public Set<String> getGlobalMetadataScannableNames() {
		return NexusDataWriter.getMetadatascannables();
	}

	@Override
	public Set<String> getRequiredMetadataScannableNames(String scannableName) {
		ScannableWriter writer = NexusDataWriter.getLocationmap().get(scannableName);
		if (writer != null) {
			Collection<String> requiredScannables = writer.getPrerequisiteScannableNames();
			if (requiredScannables != null) {
				return new HashSet<>(requiredScannables);
			}
		}

		return Collections.emptySet();
	}

}