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

package uk.ac.gda.server.ncd.subdetector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.FileConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.TreeFile;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DoubleDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeNode;
import gda.device.Detector;
import gda.device.DeviceBase;
import gda.device.DeviceException;
import gda.device.Timer;
import gda.device.detector.DataDimension;
import gda.device.detector.NXDetectorData;
import gda.factory.FactoryException;
import gda.jython.JythonServerFacade;
import gda.util.persistence.LocalParameters;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.gda.server.ncd.detectorsystem.NcdDetectorSystem;

public class NcdSubDetector extends DeviceBase implements INcdSubDetector {

	private static final Logger logger = LoggerFactory.getLogger(NcdSubDetector.class);
	private static final String CALIBRATRION_ATTRIBUTE = "calibrationFile";
	private static final String MASK_ATTRIBUTE = "maskFile";

	protected Detector detector;
	protected String detectorType;
	protected double pixelSize = 0.0;
	private ArrayList<DataDimension> supportedDimensions = null;
	protected Map<String, Object> attributeMap = new HashMap<String, Object>();
	protected String description;
	protected DoubleDataset mask = null;
	protected String interpretation = null;
	private DetectorProperties dp = null;
	protected FileConfiguration configuration;

	protected void restoreAttributeMap() {
		if (configuration != null) {
			return;
		}
		logger.trace("{} - configuration null - reloading", getName());
		try {
			configuration = LocalParameters.getXMLConfiguration(this.getClass().getCanonicalName()+":"+getName());
			for (Iterator<String> iterator = configuration.getKeys(); iterator.hasNext();) {
				String name = iterator.next();
				if (attributeMap.containsKey(name)) {
					logger.trace("{}.restore - {} already in map, ignoring later value ({})", getName(), name, attributeMap.get(name));
					continue; // don't overwrite
				}
				try {
					Object property = configuration.getProperty(name);
					if (property instanceof List) { // I get doubles I put in back in lists...
						property = ((List) property).get(0);
						logger.trace("{}.restore - {} returned list ({}) - using first value", getName(), name, property);
					}
					try {
						property = Double.parseDouble(property.toString());
						logger.trace("{}.restore - {} converted to double", getName(), name);
					} catch (Exception e) {
						// was worth a try (literally)
					}
					attributeMap.put(name, property);
					logger.trace("{}.restoring - {}: {}", getName(), name, configuration.getString(name));
				} catch (Exception e) {
					logger.info("Error restoring attribute '{}' for detector {}", name, getName());
				}
			}
			configuration.setAutoSave(true);
		} catch (Exception e) {
			logger.error("{} - error restoring attributes from LocalParameters", getName(), e);
		}
	}

	public Detector getDetector() {
		return detector;
	}

	public void setDetector(Detector detector) {
		this.detector = detector;
	}

	public String getTreeName() throws DeviceException {
		if (NcdDetectorSystem.SAXS_DETECTOR.equals(getDetectorType())) {
			return "detector";
		}
		return getName();
	}

	@Override
	public void configure() throws FactoryException {
		if (detector != null) {
			detector.reconfigure();
			configured = ((DeviceBase)detector).isConfigured();
		} else {
			configured = false;
			throw new FactoryException("no detector configured!");
		}
	}

	@Override
	public void reconfigure() throws FactoryException {
		configured = false;
		logger.debug("Reconfiguring " + getName());
		configure();
	}

	@Override
	public void clear() throws DeviceException {
		// N/A
	}

	@Override
	public void close() throws DeviceException {
		// N/A
	}

	@Override
	public int[] getDataDimensions() throws DeviceException {
		return detector.getDataDimensions();
	}

	@Override
	public String getDetectorType() throws DeviceException {
		return detectorType;
	}

	@Override
	public int getMemorySize() throws DeviceException {
		return 0;
	}

	/**
	 * The supported dimensions are obtained firstly from the XML Spring configuration and if not there will try a
	 * detector attribute "DataDimensions" else it will fallback to the currently selected data dimensions.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<DataDimension> getSupportedDimensions() throws DeviceException {
		if (supportedDimensions == null) {
			supportedDimensions = (ArrayList<DataDimension>) getAttribute("SupportedDimensions");
			if (supportedDimensions == null) {
				int[] d = getDataDimensions();
				supportedDimensions = new ArrayList<DataDimension>();
				supportedDimensions.add(new DataDimension(d[0], d[1]));
			}
		}
		return supportedDimensions;
	}

	@Override
	public void setDataDimensions(int[] detectorSize) throws DeviceException {
		detector.setAttribute("DataDimensions", detectorSize);
		dp = null;
	}

	/**
	 * @param detectorType
	 *            the detectorType to set
	 */
	public void setDetectorType(String detectorType) {
		for (String type : NcdDetectorSystem.detectorTypes) {
			if (type.equalsIgnoreCase(detectorType)) {
				this.detectorType = detectorType;
				return;
			}
		}
		throw new IllegalArgumentException("Attempt to set unrecognised detector type " + detectorType);
	}

	@Override
	public void start() throws DeviceException {
		detector.collectData();
	}

	@Override
	public void stop() throws DeviceException {
		detector.stop();
	}

	public void setCollectionTime(double time) throws DeviceException {
		detector.setCollectionTime(time);
	}

	@Override
	public void setAttribute(String attributeName, Object value) throws DeviceException {
		dp = null;
		logger.trace("{}.setAttribute - Setting {} to '{}'", getName(), attributeName, value);
		restoreAttributeMap();
		logger.trace("{}.setAttribute - Attribute map restored", getName());
		if (descriptionLabel.equals(attributeName)) {
			logger.trace("{}.setAttribute - Setting {} - not using xml", getName(), descriptionLabel);
			description = (String) value;
		} else if (value != null) {
			attributeMap.put(attributeName, value);
			if (configuration != null)
				logger.trace("{}.setAttribute - setting xml {} to {}", getName(), attributeName, value);
				configuration.setProperty(attributeName, value);
		} else if (attributeMap.containsKey(attributeName)) {
			logger.trace("{}.setAttribute - value is null, removing {} from attributes", getName(), attributeName);
			attributeMap.remove(attributeName);
			if (configuration != null)
				logger.trace("{} - value is null, removing from xml configuration", getName());
				configuration.clearProperty(attributeName);
		}
		logger.trace("{}.setAttribute - After set {} is {} in map, {} in configuration", getName(),
				attributeName,
				attributeMap.getOrDefault(attributeName, null),
				configuration == null ? "n/a" : configuration.getProperty(attributeName));
	}

	@Override
	public Object getAttribute(String attributeName) throws DeviceException {
		logger.trace("{}.getAttribute - getting {}", getName(), attributeName);
		restoreAttributeMap();
		if (descriptionLabel.equals(attributeName)) {
			return description;
		} else if (attributeMap.containsKey(attributeName)) {
			Object value = attributeMap.get(attributeName);
			logger.trace("{}.getAttribute - in map returning {}", getName(), value);
			logger.trace("{}.getAttribute - not using value in xml ({})",
					getName(),
					configuration == null ? "n/a" : configuration.getProperty(attributeName));
			return value;
		}
		logger.trace("{}.getAttribute - {} not present, returning null", getName(), attributeName);
		return null;
	}

	@Override
	public void writeout(int frames, NXDetectorData dataTree) throws DeviceException {
		int[] datadims = getDataDimensions();
		NexusGroupData ngd;
		datadims = ArrayUtils.add(datadims, 0, frames);

		Object data = detector.readout();
		if (data instanceof NexusGroupData) {
			ngd = (NexusGroupData) data;
		} else if (data instanceof double[]) {
			ngd = new NexusGroupData(datadims, (double[]) data);
		} else if (data instanceof float[]) {
			ngd = new NexusGroupData(datadims, (float[]) data);
		} else if (data instanceof int[]) {
			ngd = new NexusGroupData(datadims, (int[]) data);
		} else if (data instanceof short[]) {
			ngd = new NexusGroupData(datadims, (short[]) data);
		} else if (data instanceof byte[]) {
			ngd = new NexusGroupData(datadims, (byte[]) data);
		} else {
			throw new DeviceException("Detector readout type not supported: " + data);
		}
		ngd.isDetectorEntryData = true;
		dataTree.addData(getTreeName(), ngd, "counts", 1, getInterpretation());
		addMetadata(dataTree);
	}

	protected void addMetadata(NXDetectorData nxdata) throws DeviceException {
		NexusGroupData ngd;
		INexusTree detTree = nxdata.getDetTree(getTreeName());

		if (getName() != null) {
			ngd = new NexusGroupData(getName());
			ngd.isDetectorEntryData = false;

			NexusTreeNode type_node = new NexusTreeNode("name", NexusExtractor.SDSClassName, null, ngd);
			type_node.setIsPointDependent(false);

			detTree.addChildNode(type_node);
		}

		if (getDetectorType() != null) {
			ngd = new NexusGroupData(getDetectorType());
			ngd.isDetectorEntryData = false;

			NexusTreeNode type_node = new NexusTreeNode("sas_type", NexusExtractor.SDSClassName, null, ngd);
			type_node.setIsPointDependent(false);

			detTree.addChildNode(type_node);
		}

		if (description != null) {
			ngd = new NexusGroupData(description);
			ngd.isDetectorEntryData = false;

			NexusTreeNode type_node = new NexusTreeNode(descriptionLabel, NexusExtractor.SDSClassName, null, ngd);
			type_node.setIsPointDependent(false);

			detTree.addChildNode(type_node);
		}


		if (mask != null) {
			int[] devicedims = getDataDimensions();
			ngd = new NexusGroupData(new int[] { devicedims[0], devicedims[1] }, mask.getData());
			nxdata.addData(getName() + "mask", ngd, null, null);
		}
		String maskFile = (String) getAttribute(MASK_ATTRIBUTE);
		if (maskFile != null) {
			linkMaskFile(nxdata, maskFile);
		}
//		String calibration = (String) getAttribute("calibrationFile");
//		if (calibration != null && !calibration.isEmpty()) {
//			linkCalibrationFile(nxdata, calibration);
//		}

		if (attributeMap.containsKey(CALIBRATRION_ATTRIBUTE)) {
			linkCalibrationFile(nxdata, (String) attributeMap.get(CALIBRATRION_ATTRIBUTE));
		} else {
			if (getPixelSize() != 0.0) {
				ngd = new NexusGroupData(getPixelSize());
				ngd.isDetectorEntryData = false;

				for (String label : new String[] { "x_pixel_size", "y_pixel_size" }) {
					NexusTreeNode type_node = new NexusTreeNode(label, NexusExtractor.SDSClassName, null, ngd);
					type_node.setIsPointDependent(false);
					type_node.addChildNode(new NexusTreeNode("units", NexusExtractor.AttrClassName, type_node,
							new NexusGroupData("m")));

					detTree.addChildNode(type_node);
				}
			}
			for (String label : new String[] { "distance", "beam_center_x", "beam_center_y", "scaling_factor" }) {
				if (attributeMap.containsKey(label)) {
					try {
						ngd = new NexusGroupData((Double) attributeMap.get(label));
						ngd.isDetectorEntryData = "scaling_factor".equals(label);

						NexusTreeNode type_node = new NexusTreeNode(label, NexusExtractor.SDSClassName, null, ngd);
						type_node.setIsPointDependent(false);

						type_node.addChildNode(new NexusTreeNode("units", NexusExtractor.AttrClassName, type_node, label
								.equals("distance") ? new NexusGroupData("m") : null));

						detTree.addChildNode(type_node);
					} catch (Exception e) {
						logger.warn("{} - Error writing metadata {}: ", getName(), label, e);
					}
				}
			}
		}
	}

	private void linkMaskFile(NXDetectorData nxdata, String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			logger.info("{} - Not including mask data. No mask file set", getName());
			return;
		}
		if (!new File(filePath).exists()) {
			logger.error("{} - Could not include mask data. {} does not exist", getName(), filePath);
			JythonServerFacade.getInstance().print(String.format("%s - mask file '%s' does not exist", getName(), filePath));
			return;
		}
		TreeFile tree;
		try {
			tree = new HDF5Loader(filePath).loadTree();
			NodeLink maskNode = tree.findNodeLink("/entry/mask/mask");
			if (maskNode == null) {
				logger.error("{} - Mask file does not contain mask", getName());
				return;
			} else {
				long[] dims = ((DataNode) maskNode.getDestination()).getMaxShape();
				int[] detDims = getDataDimensions();
				if (dims[0] != detDims[0] || dims[1] != detDims[1]) {
					logger.error("{} - Mask dimensions not equal to detector dimensions", getName());
					JythonServerFacade.getInstance()
							.print(String.format("%s - mask wrong dimension (%s instead of %s)", getName(), Arrays.toString(dims), Arrays.toString(detDims)));
					return;
				}
			}
		} catch (ScanFileHolderException sfhe) {
			logger.error("{} - Could not open mask file tree ({})", getName(), filePath);
			return;
		} catch (DeviceException e) {
			logger.error("{} - Could not read detector dimensions", getName(), e);
			return;
		}
		try {
			nxdata.addExternalFileLink(getTreeName(), "pixel_mask", "nxfile://" + filePath + "#entry/mask/mask", false, true);
			logger.info("{} - Linked mask file {}", getName(), filePath);
		} catch (Exception e) {
			logger.error("{} - Could not link external mask", getName(), e);
		}
	}

	private void linkCalibrationFile(NXDetectorData nxdata, String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			logger.info("{} - Not including calibration data. No calibration file set", getName());
			return;
		}
		if (!new File(filePath).exists()) {
			logger.error("{} - Could not include calibration data. {} does not exist", getName(), filePath);
			JythonServerFacade.getInstance().print(String.format("%s - calibration file '%s' does not exist", getName(), filePath));
			return;
		}
		DataHolder loadFile;
		try {
			loadFile = new HDF5Loader(filePath).loadFile();
		} catch (ScanFileHolderException sfhe) {
			logger.error("{} - Could not open calibration file tree", getName(), sfhe);
			return;
		}

		//sample calibration

		Dataset dataset = loadFile.getDataset("/entry/instrument/detector/beam_center_x");

		NexusGroupData ngd;
		ngd = NexusGroupData.createFromDataset(dataset);
		NexusTreeNode type_node = new NexusTreeNode("beam_center_x", NexusExtractor.SDSClassName, null, ngd);
//		type_node.addChildNode(new NexusTreeNode("units", NexusExtractor.AttrClassName, type_node, "beam_center_x"
//				.equals("distance") ? new NexusGroupData("m") : null));
		try {
			nxdata.getDetTree(getTreeName()).addChildNode(type_node);
		} catch (DeviceException e) {
		}


	}


//		ngd = new NexusGroupData((Double) attributeMap.get(label));
//		ngd.isDetectorEntryData = "scaling_factor".equals(label);
//
//		NexusTreeNode type_node = new NexusTreeNode(label, NexusExtractor.SDSClassName, null, ngd);
//		type_node.setIsPointDependent(false);
//
//		type_node.addChildNode(new NexusTreeNode("units", NexusExtractor.AttrClassName, type_node, label
//				.equals("distance") ? new NexusGroupData("m") : null));
//
//		detTree.addChildNode(type_node);

	@Override
	public double getPixelSize() throws DeviceException {
		return pixelSize;
	}

	@SuppressWarnings("unused") // subclasses require the throws
	public void setPixelSize(double pixelSize) throws DeviceException{
		this.pixelSize = pixelSize;
	}

	public Dataset getMask() {
		return mask;
	}

	public void setMask(DoubleDataset mask) {
		try {
			if (mask == null || mask.getShape() == getDataDimensions()) {
				this.mask = mask;
				return;
			}
		} catch (Exception e) {
			//
		}
		logger.error("{} - cannot set mask due to dimensions problem", getName());
	}

	@Override
	public void atScanStart() throws DeviceException {
	}

	@Override
	public void atScanEnd() throws DeviceException {
	}

	@Override
	public void setTimer(Timer timer) {
	}

	public String getInterpretation() {
		return interpretation;
	}

	public void setInterpretation(String interpretation) {
		this.interpretation = interpretation;
	}

	@Override
	public DetectorProperties getDetectorProperties() throws DeviceException {
		if (dp == null) {
			if (getPixelSize() == 0.0 || getAttribute("distance") == null || getAttribute("beam_center_x") == null || getAttribute("beam_center_y") == null) {
				dp = new DetectorProperties();
			} else {
				dp = new DetectorProperties(((Double) getAttribute("distance"))*1000, ((Double) getAttribute("beam_center_x"))*getPixelSize()*1000, ((Double) getAttribute("beam_center_y"))*getPixelSize()*1000, getDataDimensions()[0], getDataDimensions()[1], getPixelSize()*1000, getPixelSize()*1000);
			}
		}
		return dp;
	}
}