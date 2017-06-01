/*-
 * Copyright © 2015 Diamond Light Source Ltd.
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

package uk.ac.diamond.daq.mapping.ui.experiment;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.CircularROI;
import org.eclipse.dawnsci.analysis.dataset.roi.LinearROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PolygonalROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.richbeans.api.generator.IGuiGeneratorService;
import org.eclipse.scanning.api.device.IScannableDeviceService;
import org.eclipse.scanning.api.device.models.ClusterProcessingModel;
import org.eclipse.scanning.api.device.models.IDetectorModel;
import org.eclipse.scanning.api.event.IEventService;
import org.eclipse.scanning.api.event.scan.DeviceInformation;
import org.eclipse.scanning.api.event.scan.ScanBean;
import org.eclipse.scanning.api.event.scan.ScanRequest;
import org.eclipse.scanning.api.points.MapPosition;
import org.eclipse.scanning.api.points.models.CompoundModel;
import org.eclipse.scanning.api.points.models.IMapPathModel;
import org.eclipse.scanning.api.points.models.IScanPathModel;
import org.eclipse.scanning.api.points.models.ScanRegion;
import org.eclipse.scanning.api.scan.ScanningException;
import org.eclipse.scanning.api.scan.models.ScanMetadata;
import org.eclipse.scanning.api.scan.models.ScanMetadata.MetadataType;
import org.eclipse.scanning.api.script.ScriptLanguage;
import org.eclipse.scanning.api.script.ScriptRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.configuration.properties.LocalProperties;
import uk.ac.diamond.daq.mapping.api.IClusterProcessingModelWrapper;
import uk.ac.diamond.daq.mapping.api.IDetectorModelWrapper;
import uk.ac.diamond.daq.mapping.api.IMappingExperimentBean;
import uk.ac.diamond.daq.mapping.api.IMappingScanRegion;
import uk.ac.diamond.daq.mapping.api.IMappingScanRegionShape;
import uk.ac.diamond.daq.mapping.api.ISampleMetadata;
import uk.ac.diamond.daq.mapping.api.IScanDefinition;
import uk.ac.diamond.daq.mapping.api.IScanPathModelWrapper;
import uk.ac.diamond.daq.mapping.api.IScriptFiles;
import uk.ac.diamond.daq.mapping.impl.ClusterProcessingModelWrapper;
import uk.ac.diamond.daq.mapping.impl.DetectorModelWrapper;
import uk.ac.diamond.daq.mapping.impl.MappingExperimentBean;
import uk.ac.diamond.daq.mapping.impl.MappingStageInfo;
import uk.ac.diamond.daq.mapping.impl.ScanPathModelWrapper;
import uk.ac.diamond.daq.mapping.impl.ScriptFiles;
import uk.ac.diamond.daq.mapping.impl.SimpleSampleMetadata;
import uk.ac.diamond.daq.mapping.region.CircularMappingRegion;
import uk.ac.diamond.daq.mapping.region.LineMappingRegion;
import uk.ac.diamond.daq.mapping.region.PointMappingRegion;
import uk.ac.diamond.daq.mapping.region.PolygonMappingRegion;
import uk.ac.diamond.daq.mapping.region.RectangularMappingRegion;

/**
 * A class to convert from a {@link IMappingExperimentBean} to a {@link ScanRequest} and
 * vica-versa, where:
 * <ul>
 *   <li>An {@link IMappingExperimentBean} is used to set-up a mapping bean and is directly
 *   manipulated in the Mapping Experiment Setup view, with many subcomponents modified with
 *   UI components generated by the {@link IGuiGeneratorService} for example;</li>
 *   <li> A {@link ScanRequest} used to define the scan to run by the GDA9 (aka solstice)
 *     scanning framework. Added to a {@link ScanBean} so that it can be run on the queue.</li>
 * </ul>
 */
public class ScanRequestConverter {

	private static final Logger logger = LoggerFactory.getLogger(ScanRequestConverter.class);

	public static final String FIELD_NAME_SAMPLE_NAME = "name";
	public static final String FIELD_NAME_SAMPLE_DESCRIPTION = "description";

	private MappingStageInfo mappingStageInfo;

	private IEventService eventService;

	private IScannableDeviceService scannableDeviceService;

	public void setMappingStageInfo(MappingStageInfo mappingStageInfo) {
		this.mappingStageInfo = mappingStageInfo;
	}

	/**
	 * Convert an IMappingExperimentBean to a ScanRequest so that it can be run by the
	 * GDA9 scanning framework.
	 * <p>
	 * This will include setting the mapping scan axes with the names from the mapping axis manager.
	 * <p>
	 * This method is made <code>public</code> to allow testing.
	 *
	 * @param mappingBean
	 *            the IMappingExperimentBean to be converted
	 * @return the ScanRequest
	 * @throws ScanningException
	 */
	public ScanRequest<IROI> convertToScanRequest(IMappingExperimentBean mappingBean) throws ScanningException {
		ScanRequest<IROI> scanRequest = new ScanRequest<IROI>();

		final IMappingScanRegion scanRegion = mappingBean.getScanDefinition().getMappingScanRegion();
		final IScanPathModel scanPath = scanRegion.getScanPath();

		if (!(scanPath instanceof IMapPathModel)) {
			final String message = "Could not set fast and slow axis. The scan path is not an instance of IMapPathModel.";
			logger.error(message);
			throw new IllegalArgumentException(message);
		}

		final String fastAxis;
		final String slowAxis;
		if (mappingStageInfo != null) {
			fastAxis = mappingStageInfo.getActiveFastScanAxis();
			slowAxis = mappingStageInfo.getActiveSlowScanAxis();
			// Change the axis on the scanPathModel
			IMapPathModel boxModel = (IMapPathModel) scanPath;
			boxModel.setFastAxisName(fastAxis);
			boxModel.setSlowAxisName(slowAxis);
		} else {
			logger.warn("No mapping axis manager is set - the scan request will use default axis names!");
			IMapPathModel boxModel = (IMapPathModel) scanPath;
			fastAxis = boxModel.getFastAxisName();
			slowAxis = boxModel.getSlowAxisName();
		}

		// Build the list of models for the scan
		final List<IScanPathModel> models = new ArrayList<>();
		// If there are outer scannables to be included, add them to the list
		// TODO currently there is no support for outer scannables ROIs
		for (IScanPathModelWrapper scanPathModelWrapper : mappingBean.getScanDefinition()
				.getOuterScannables()) {
			if (scanPathModelWrapper.isIncludeInScan()) {
				final IScanPathModel model = scanPathModelWrapper.getModel();
				if (model == null) {
					logger.warn("Outer scannables contained a null model for: {}. It wont be included in the scan!",
							scanPathModelWrapper.getName());
				} else {
					models.add(model);
				}
			}
		}

		// Add the actual map path model last, it's the inner most model
		models.add(scanRegion.getScanPath());

		// Convert the list of models into a compound model
		final CompoundModel<IROI> compoundModel = new CompoundModel<>(models);

		// Add the ROI for the mapping region
		ScanRegion<IROI> region = new ScanRegion<IROI>(scanRegion.getRegion().toROI(), slowAxis, fastAxis);

		// Convert to a List of ScanRegion<IROI> containing one item to avoid unsafe varargs warning
		compoundModel.setRegions(Arrays.asList(region));

		// Set the model on the scan request
		scanRequest.setCompoundModel(compoundModel);

		// set the scan start position (scannables not in the scan that are set to a certain value before the scan starts)
		Map<String, Object> beamlineConfiguration = mappingBean.getBeamlineConfiguration();
		if (beamlineConfiguration != null) {
			scanRequest.setStart(new MapPosition(beamlineConfiguration));
		}

		// add the required detectors to the scan
		for (IDetectorModelWrapper detectorWrapper : mappingBean.getDetectorParameters()) {
			if (detectorWrapper.isIncludeInScan()) {
				IDetectorModel detectorModel = detectorWrapper.getModel();
				scanRequest.putDetector(detectorModel.getName(), detectorModel);
			}
		}

		// add the required cluster processing steps
		if (mappingBean.getClusterProcessingConfiguration() != null) {
			for (IClusterProcessingModelWrapper processingWrapper : mappingBean.getClusterProcessingConfiguration()) {
				if (processingWrapper.isIncludeInScan()) {
					String name = processingWrapper.getName();
					if (scanRequest.getDetectors() != null && scanRequest.getDetectors().containsKey(name)) {
						throw new IllegalArgumentException(MessageFormat.format("A device or processing step with the name {0} is already included in the scan", name));
					}
					scanRequest.putDetector(processingWrapper.getName(), processingWrapper.getModel());
				}
			}
		}

		// add the activated monitors. Note: these do not come from the mapping bean
		Set<String> monitorNames = getMonitors();
		scanRequest.setMonitorNames(monitorNames);

		// set the scripts to run before and after the scan, if any
		if (mappingBean.getScriptFiles() != null) {
			IScriptFiles scriptFiles = mappingBean.getScriptFiles();
			scanRequest.setBefore(createScriptRequest(scriptFiles.getBeforeScanScript()));
			scanRequest.setAfter(createScriptRequest(scriptFiles.getAfterScanScript()));
		}

		// add the sample metadata
		if (mappingBean.getSampleMetadata() != null) {
			setSampleMetadata(mappingBean, scanRequest);
		}

		return scanRequest;
	}

	private void setSampleMetadata(IMappingExperimentBean mappingBean, ScanRequest<IROI> scanRequest) {
		final ISampleMetadata sampleMetadata = mappingBean.getSampleMetadata();
		String sampleName = sampleMetadata.getSampleName();
		if (sampleName == null || sampleName.trim().isEmpty()) {
			sampleName = "Unnamed Sample";
		}

		final ScanMetadata scanMetadata = new ScanMetadata(MetadataType.SAMPLE);
		scanMetadata.addField(FIELD_NAME_SAMPLE_NAME, sampleName);
		if (sampleMetadata instanceof SimpleSampleMetadata) {
			String description = ((SimpleSampleMetadata) sampleMetadata).getDescription();
			if (description == null || description.trim().isEmpty()) {
				description = "No description provided.";
			}
			scanMetadata.addField(FIELD_NAME_SAMPLE_DESCRIPTION, description);
		}
		scanRequest.setScanMetadata(Arrays.asList(scanMetadata));
	}

	private ScriptRequest createScriptRequest(String scriptFile) {
		if (scriptFile == null || scriptFile.isEmpty()) {
			return null;
		}

		final ScriptRequest scriptRequest = new ScriptRequest();
		scriptRequest.setLanguage(ScriptLanguage.SPEC_PASTICHE);
		scriptRequest.setFile(scriptFile);
		return scriptRequest;
	}

	/**
	 * Merge a scan request into an existing mapping bean so that it can be viewed
	 * and possibly modified in the Mapping Experiment Setup view.
	 * The reason for merging into an existing mapping bean is so that we don't remove
	 * detectors and processing steps that we not selected when this scan was run -
	 * when creating the scan request from the mapping bean a detector is only added if
	 * {@link IDetectorModelWrapper#isIncludeInScan()} is true. The mapping bean reconstructed
	 * from the scan request still needs to include this detector.
	 *
	 * @param scanRequest the {@link ScanRequest}
	 * @param mappingBean the {@link IMappingExperimentBean} to merge into
	 */
	public void mergeIntoMappingBean(ScanRequest<IROI> scanRequest, MappingExperimentBean mappingBean) {
		CompoundModel<IROI> compoundModel = scanRequest.getCompoundModel();
		Collection<ScanRegion<IROI>> regions = compoundModel.getRegions();
		if (regions.size() != 1) {
			throw new IllegalArgumentException("The scan request must have exactly one region, has " + regions.size());
		}
		ScanRegion<IROI> region = regions.iterator().next();
		List<String> scannableNames = region.getScannables();
		if (scannableNames.size() != 2) {
			throw new IllegalArgumentException("The scan region should have exactly two scannable names, was " +
					String.join(", ", scannableNames));
		}

		// Check that the scannable names in the scan region are the same as in the mapping stage
		if (mappingStageInfo != null) {
			List<String> expectedScannableNames = Arrays.asList(
					mappingStageInfo.getActiveSlowScanAxis(), mappingStageInfo.getActiveFastScanAxis());
			if (!scannableNames.equals(expectedScannableNames)) {
				throw new IllegalArgumentException("The axis names have changed. Expected : " +
						String.join(", ", expectedScannableNames) + "; was " + String.join(", ", scannableNames));
			}
		}

		// recreate the outer scannable wrappers from the scan request
		mergeOuterScannables(compoundModel, mappingBean);

		// set the scan path to the last child model of the compound model
		IScanPathModel mappingModel = (IScanPathModel) compoundModel.getModels().get(
				compoundModel.getModels().size() - 1);
		final IScanDefinition scanDefinition = mappingBean.getScanDefinition();
		IMappingScanRegion scanRegion = scanDefinition.getMappingScanRegion();
		scanRegion.setScanPath(mappingModel);

		// convert the ROI to a mapping scan region shape
		IMappingScanRegionShape shape = convertROItoRegionShape(region.getRoi());
		scanRegion.setRegion(shape);

		// recreate the beamline configuration from the scan start position
		if (scanRequest.getStart() != null) {
			mappingBean.setBeamlineConfiguration(new LinkedHashMap<>(scanRequest.getStart().getValues()));
		}

		// recreate the detector models and processing steps (included in the same map of detectors in the scan request)
		mergeDetectorAndProcessing(scanRequest, mappingBean);

		// recreate the scripts to run before and after the scan, if any
		if (scanRequest.getBefore() != null || scanRequest.getAfter() != null) {
			ScriptFiles scriptFiles = new ScriptFiles();
			if (scanRequest.getBefore() != null) {
				scriptFiles.setBeforeScanScript(scanRequest.getBefore().getFile());
			}
			if (scanRequest.getAfter() != null) {
				scriptFiles.setAfterScanScript(scanRequest.getAfter().getFile());
			}
			mappingBean.setScriptFiles(scriptFiles);
		}

		// recreate the sample metadata from the metadata in the scan request
		mergeSampleMetadata(scanRequest, mappingBean);
	}

	private IMappingScanRegionShape convertROItoRegionShape(IROI roi) {
		IMappingScanRegionShape regionShape;
		if (roi instanceof CircularROI) {
			regionShape = new CircularMappingRegion();
		} else if (roi instanceof LinearROI) {
			regionShape = new LineMappingRegion();
		} else if (roi instanceof PointROI) {
			regionShape = new PointMappingRegion();
		} else if (roi instanceof PolygonalROI) {
			regionShape = new PolygonMappingRegion();
		} else if (roi instanceof RectangularROI) {
			regionShape = new RectangularMappingRegion();
		} else {
			throw new IllegalArgumentException("Unable to convert ROI type " + roi.getClass());
		}

		regionShape.updateFromROI(roi);

		return regionShape;
	}

	private void mergeOuterScannables(CompoundModel<IROI> compoundModel, MappingExperimentBean mappingBean) {
		List<IScanPathModelWrapper> outerScannables = mappingBean.getScanDefinition().getOuterScannables();
		List<Object> models = compoundModel.getModels();
		List<Object> outerScannableModels = new ArrayList<>(models.subList(0, models.size() - 1));
		for (Object model : outerScannableModels) {
			if (!(model instanceof IScanPathModel)) {
				throw new IllegalArgumentException("Model is not an IScanPathModel: " + model);
			}
		}

		// We assume that models have the same name as the wrapper
		Iterator<Object> modelIter = outerScannableModels.iterator();
		IScanPathModel currentModel =  modelIter.hasNext() ? (IScanPathModel) modelIter.next() : null;
		// iterate through the list of outer scannable wrappers to find the wrapper for the
		// current model. If we find a match, then we move on to the next model
		for (IScanPathModelWrapper outerScannable : outerScannables) {
			if (currentModel == null || !outerScannable.getName().equals(currentModel.getName())) {
				// this outer scannable wrapper isn't in the scan request
				((ScanPathModelWrapper) outerScannable).setIncludeInScan(false);
			} else {
				// this is the wrapper for this model, set it enabled and overwrite its model
				// with the outer scannable model
				((ScanPathModelWrapper) outerScannable).setIncludeInScan(true);
				((ScanPathModelWrapper) outerScannable).setModel(currentModel);
				currentModel = modelIter.hasNext() ? (IScanPathModel) modelIter.next() : null;
			}
		}

		// We didn't find a wrapper for this model
		if (currentModel != null) {
			throw new IllegalArgumentException("No IScanPathModelWrapper found for model " + currentModel.getName());
		}
	}

	private void mergeDetectorAndProcessing(ScanRequest<?> scanRequest, MappingExperimentBean mappingBean) {
		// disable all the existing detectors in the mapping bean, also create a map of them by
		// detector name (note: the name in the IDetectorModel, not the name in the wrapper)
		final Map<String, IDetectorModelWrapper> detectorModelWrappers;
		if (mappingBean.getDetectorParameters() == null) {
			detectorModelWrappers = Collections.emptyMap();
		} else {
			detectorModelWrappers = new HashMap<>(mappingBean.getDetectorParameters().size());
			for (IDetectorModelWrapper detectorModelWrapper : mappingBean.getDetectorParameters()) {
				((DetectorModelWrapper) detectorModelWrapper).setIncludeInScan(false);
				detectorModelWrappers.put(detectorModelWrapper.getModel().getName(), detectorModelWrapper);
			}
		}

		// disable all the existing processing steps
		final Map<String, IClusterProcessingModelWrapper> processingWrappers;
		if (mappingBean.getClusterProcessingConfiguration() == null) {
			processingWrappers = Collections.emptyMap();
		} else {
			processingWrappers = new HashMap<>(mappingBean.getClusterProcessingConfiguration().size());
			for (IClusterProcessingModelWrapper processingWrapper : mappingBean.getClusterProcessingConfiguration()) {
				((ClusterProcessingModelWrapper) processingWrapper).setIncludeInScan(false);
				processingWrappers.put(processingWrapper.getModel().getName(),
						processingWrapper);
			}
		}

		// merge in the detectors and processing from the scan request. If there already is
		// a detector or processor with that name it is enabled and the model is replaced
		// with the model in the ScanRequest
		final Map<String, Object> detectorsAndProcessingMap = scanRequest.getDetectors();
		if (detectorsAndProcessingMap != null) {
			for (String name : detectorsAndProcessingMap.keySet()) {
				final Object model = detectorsAndProcessingMap.get(name);
				if (model instanceof IDetectorModel) {
					List<IDetectorModelWrapper> detectorParams = mappingBean.getDetectorParameters();
					if (detectorParams == null) { // create the list of detector wrapper in the bean if not present
						detectorParams = new ArrayList<>(4);
						mappingBean.setDetectorParameters(detectorParams);
					}

					if (detectorModelWrappers.containsKey(name)) {
						// Get the wrapper for the detector. Set it to be included in the scan
						// and overwrite the model with the model in the scan request
						DetectorModelWrapper wrapper = (DetectorModelWrapper) detectorModelWrappers.get(name);
						wrapper.setIncludeInScan(true);
						wrapper.setModel((IDetectorModel) model);
					} else {
						// The scan includes an unknown detector. This can only occur if the mapping bean has changed in spring
						throw new IllegalArgumentException("Unknown detector " + name);
					}
				} else if (model instanceof ClusterProcessingModel) {
					List<IClusterProcessingModelWrapper> processingConfigList = mappingBean.getClusterProcessingConfiguration();
					if (processingConfigList == null) { // create the list of processing configs if not present
						processingConfigList = new ArrayList<>(4);
						mappingBean.setClusterProcessingConfiguration(processingConfigList);
					}

					if (processingWrappers.containsKey(name)) {
						// The mapping bean already contains a processing config with this name
						// set it to be included in the scan an overwrite the model with the model
						// in the scan request
						ClusterProcessingModelWrapper wrapper = (ClusterProcessingModelWrapper) processingWrappers.get(name);
						wrapper.setIncludeInScan(true);
						wrapper.setModel((ClusterProcessingModel) model);
					} else {
						// A new processing step. Add it to the mapping bean at the end (the order doesn't matter)
						processingConfigList.add(new ClusterProcessingModelWrapper(name, (ClusterProcessingModel) model, true));
					}
				}
			}
		}
	}

	private void mergeSampleMetadata(ScanRequest<IROI> scanRequest, IMappingExperimentBean mappingBean) {
		List<ScanMetadata> scanMetadata = scanRequest.getScanMetadata();
		if (scanMetadata == null) return;
		Optional<ScanMetadata> sampleScanMetadataOpt = scanMetadata.stream().filter(
				metadata -> metadata.getType() == MetadataType.SAMPLE).findFirst();
		if (!sampleScanMetadataOpt.isPresent()) return;
		ScanMetadata sampleScanMetadata = sampleScanMetadataOpt.get();

		SimpleSampleMetadata sampleMetadata = (SimpleSampleMetadata) mappingBean.getSampleMetadata();
		if (sampleMetadata == null) {
			sampleMetadata = new SimpleSampleMetadata();
			mappingBean.setSampleMetadata(sampleMetadata);
		}

		sampleMetadata.setSampleName((String) sampleScanMetadata.getFieldValue(FIELD_NAME_SAMPLE_NAME));
		sampleMetadata.setDescription((String) sampleScanMetadata.getFieldValue(FIELD_NAME_SAMPLE_DESCRIPTION));
	}


	public void setEventService(IEventService eventService) {
		this.eventService = eventService;
	}

	public void setScannableDeviceService(IScannableDeviceService scannableDeviceService) {
		this.scannableDeviceService = scannableDeviceService;
	}

	private IScannableDeviceService getScannableDeviceService() throws ScanningException {
		if (scannableDeviceService == null) {
			try {
				URI jmsURI = new URI(LocalProperties.getActiveMQBrokerURI());
				scannableDeviceService = eventService.createRemoteService(jmsURI, IScannableDeviceService.class);
			} catch (Exception e) {
				throw new ScanningException("Could not get IScannableDeviceService", e);
			}
		}

		return scannableDeviceService;
	}

	private Set<String> getMonitors() throws ScanningException {
		final Collection<DeviceInformation<?>> scannableInfos = getScannableDeviceService().getDeviceInformation();
		return scannableInfos.stream().
			filter(info -> info.isActivated()).
			map(info -> info.getName()).
			collect(Collectors.toSet());
	}

}
