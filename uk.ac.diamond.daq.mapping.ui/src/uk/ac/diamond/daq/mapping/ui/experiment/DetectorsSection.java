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

package uk.ac.diamond.daq.mapping.ui.experiment;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.beans.PojoProperties;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.dawnsci.analysis.api.persistence.IMarshallerService;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.scanning.api.device.models.DeviceRole;
import org.eclipse.scanning.api.device.models.IDetectorModel;
import org.eclipse.scanning.api.device.models.IMalcolmModel;
import org.eclipse.scanning.api.event.scan.DeviceInformation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.daq.mapping.api.IDetectorModelWrapper;
import uk.ac.diamond.daq.mapping.impl.DetectorModelWrapper;

/**
 * A section for choosing which detectors should be included in the scan, and for
 * configuring their parameters.
 */
public class DetectorsSection extends AbstractMappingSection {

	private static final Logger logger = LoggerFactory.getLogger(DetectorsSection.class);

	private static final int DETECTORS_COLUMNS = 3;
	private static final String DETECTOR_SELECTION_KEY_JSON = "detectorSelection.json";

	private DataBindingContext dataBindingContext;

	private Map<String, Button> detectorSelectionCheckboxes;
	private List<IDetectorModelWrapper> chosenDetectors;
	private Composite sectionComposite; // parent composite for all controls in the section
	private Composite detectorsComposite;

	@Override
	public void createControls(Composite parent) {
		sectionComposite = new Composite(parent, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(sectionComposite);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(sectionComposite);
		Label detectorsLabel = new Label(sectionComposite, SWT.NONE);
		detectorsLabel.setText("Detectors");
		GridDataFactory.swtDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(detectorsLabel);

		// button to open the detector chooser dialog
		Button configure = new Button(sectionComposite, SWT.PUSH);
		configure.setImage(MappingExperimentUtils.getImage("icons/gear.png"));
		configure.setToolTipText("Select detectors to show");
		GridDataFactory.fillDefaults().align(SWT.RIGHT, SWT.CENTER).applyTo(configure);
		configure.addListener(SWT.Selection, event -> chooseDetectors());

		if (chosenDetectors == null) {
			// this will only be null if loadState() has not been called, i.e. on a workspace reset
			// in this case show all available detectors
			chosenDetectors = getDetectorParameters();
		}
		createDetectorControls(chosenDetectors);
	}

	private void chooseDetectors() {
		ChooseDetectorsDialog dialog = new ChooseDetectorsDialog(getShell(),getDetectorParameters(), chosenDetectors);

		if (dialog.open() == Window.OK) {
			chosenDetectors = dialog.getSelectedDetectors();
			createDetectorControls(chosenDetectors);
			relayoutMappingView();
			getMappingView().recalculateMinimumSize();
		}
	}

	private void createDetectorControls(List<IDetectorModelWrapper> detectorParametersList) {

		if (detectorsComposite != null) detectorsComposite.dispose();
		dataBindingContext = new DataBindingContext();
		detectorSelectionCheckboxes = new HashMap<>();

		detectorsComposite = new Composite(sectionComposite, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(detectorsComposite);
		GridLayoutFactory.swtDefaults().numColumns(DETECTORS_COLUMNS).margins(0, 0).applyTo(detectorsComposite);

		for (IDetectorModelWrapper detectorParameters : detectorParametersList) {
			// create the detector selection checkbox and bind it to the includeInScan property of the wrapper
			Button checkBox = new Button(detectorsComposite, SWT.CHECK);
			detectorSelectionCheckboxes.put(detectorParameters.getName(), checkBox);
			checkBox.setText(detectorParameters.getName());
			IObservableValue checkBoxValue = WidgetProperties.selection().observe(checkBox);
			IObservableValue activeValue = PojoProperties.value("includeInScan").observe(detectorParameters);
			dataBindingContext.bindValue(checkBoxValue, activeValue);
			checkBox.addListener(SWT.Selection, event -> {
				updateStatusLabel();

				if (detectorParameters.getModel() instanceof IMalcolmModel) {
					malcolmDeviceSelectionChanged(detectorParameters.getName());
				}
			});

			// create the exposure time text control and bind it the exposure time property of the wrapper
			Text exposureTimeText = new Text(detectorsComposite, SWT.BORDER);
			exposureTimeText.setToolTipText("Exposure time");
			GridDataFactory.fillDefaults().grab(true, false).applyTo(exposureTimeText);
			IObservableValue exposureTextValue = WidgetProperties.text(SWT.Modify).observe(exposureTimeText);
			IObservableValue exposureTimeValue = PojoProperties.value("exposureTime").observe(detectorParameters.getModel());
			dataBindingContext.bindValue(exposureTextValue, exposureTimeValue);
			exposureTimeText.addListener(SWT.Modify, event -> updateStatusLabel());

			// Edit configuration
			final Button configButton = new Button(detectorsComposite, SWT.PUSH);
			configButton.setImage(MappingExperimentUtils.getImage("icons/pencil.png"));
			configButton.setToolTipText("Edit parameters");
			configButton.addListener(SWT.Selection, event -> editDetectorParameters(detectorParameters));
		}
	}

	private void editDetectorParameters(final IDetectorModelWrapper detectorParameters) {
		final EditDetectorParametersDialog editDialog = new EditDetectorParametersDialog(getShell(), getEclipseContext(), detectorParameters);
		editDialog.create();
		if (editDialog.open() == Window.OK) {
			dataBindingContext.updateTargets();
		}
		relayoutMappingView();
	}

	/**
	 * Update detector checkboxes based on malcolm device selection being (un)checked.
	 * When the malcolm device is selected, all other detectors should be unchecked and disabled.
	 * @param selectionCheckBoxes
	 * @param malcolmDeviceCheckBox
	 */
	private void malcolmDeviceSelectionChanged(String name) {
		boolean malcolmDeviceSelected = detectorSelectionCheckboxes.get(name).getSelection();
		detectorSelectionCheckboxes.keySet().stream()
											.filter(detName -> !detName.equals(name))
											.map(detName -> detectorSelectionCheckboxes.get(detName))
											.forEach(cb -> {
												cb.setEnabled(!malcolmDeviceSelected);
												if (malcolmDeviceSelected) cb.setSelection(false);
											});

		// set all other detectors as not included in scan? TODO why doesn't jface binding do this automatically?
		if (malcolmDeviceSelected) {
			getMappingBean().getDetectorParameters().stream()
				.filter(detParams -> !detParams.getName().equals(name))
				.forEach(detParams -> ((DetectorModelWrapper) detParams).setIncludeInScan(false));
		}
	}

	private List<IDetectorModelWrapper> getDetectorParameters() {
		// a function to convert DeviceInformations to IDetectorModelWrappers
		Function<DeviceInformation<?>, IDetectorModelWrapper> malcolmInfoToWrapper =
				info -> new DetectorModelWrapper(info.getLabel(), (IDetectorModel) info.getModel(), false);

		// get the DeviceInformation objects for the malcolm devices and apply the function
		// above to create DetectorModelWrappers for them.
		Map<String, IDetectorModelWrapper> malcolmParams = getMalcolmDeviceInfo().stream()
				.map(malcolmInfoToWrapper::apply)
				.collect(toMap(IDetectorModelWrapper::getName, identity()));

		// a function to collect Malcolm model names
		final Function<IDetectorModelWrapper, String> getMalcolmModel =	param -> param.getModel().getName();

		// get the set of Malcolm models
		final Set<String> malcolmModels = malcolmParams.values().stream()
				.map(getMalcolmModel::apply)
				.collect(Collectors.toSet());

		// a predicate to filter out malcolm devices which no longer exist
		Predicate<IDetectorModelWrapper> nonExistantMalcolmFilter =
				wrapper -> !(wrapper.getModel() instanceof IMalcolmModel) || malcolmModels.contains(wrapper.getModel().getName());

		// create a name-keyed map from the existing detector parameters in the bean, filtering out those for
		// malcolm devices which no longer exist using the predicate above
		Map<String, IDetectorModelWrapper> detectorParams = getMappingBean().getDetectorParameters().stream().
				filter(nonExistantMalcolmFilter). // filter out malcolm device which no longer exist
				collect(Collectors.toMap(IDetectorModelWrapper::getName, // key by name
						identity(), // the value is the wrapper itself
						(v1, v2) -> v1, // merge function not used as there should be no duplicate keys
						LinkedHashMap::new)); // create a linked hash map to maintain the order

		// merge in the wrappers for the malcolm devices. The merge function here keeps the original
		// wrapper if the mapping bean already contained one for a device with this name
		malcolmParams.forEach((name, params) -> detectorParams.merge(name, params, (v1, v2) -> v1));

		// convert to a list and set this as the detector parameters in the bean
		List<IDetectorModelWrapper> detectorParamList = new ArrayList<>(detectorParams.values());
		getMappingBean().setDetectorParameters(detectorParamList);

		return detectorParamList;
	}

	private Collection<DeviceInformation<?>> getMalcolmDeviceInfo() {
		try {
			return getRunnableDeviceService().getDeviceInformation(DeviceRole.MALCOLM);
		} catch (Exception e) {
			logger.error("Could not get malcolm devices.", e);
			return Collections.emptyList();
		}
	}

	@Override
	protected void updateControls() {
		// add any detectors in the bean to the list of chosen detectors if not present
		// first create a map of detectors in the mapping bean keyed by name
		final Map<String, IDetectorModelWrapper> wrappersByName =
				getMappingBean().getDetectorParameters().stream().collect(toMap(
				IDetectorModelWrapper::getName, identity()));

		// take the list of chosen detectors and replace them with the ones in the mapping bean
		chosenDetectors = chosenDetectors.stream().
			map(wrapper -> wrappersByName.containsKey(wrapper.getName()) ? // replace the wrapper with the one
					wrappersByName.get(wrapper.getName()) : wrapper). // from the map with the same name, if exists
					collect(toCollection(ArrayList::new));

		// add any detectors that are in the bean but not in the list of chosen detectors
		final Set<String> detectorNames = chosenDetectors.stream().map(IDetectorModelWrapper::getName).collect(toSet());
		chosenDetectors.addAll(
				getMappingBean().getDetectorParameters().stream().
					filter(wrapper -> wrapper.isIncludeInScan()).
					filter(wrapper -> !detectorNames.contains(wrapper.getName())).
					collect(Collectors.toList()));

		// update the detector controls
		createDetectorControls(chosenDetectors);
	}

	@Override
	protected void saveState(Map<String, String> persistedState) {
		IMarshallerService marshaller = getEclipseContext().get(IMarshallerService.class);
		try {
			persistedState.put(DETECTOR_SELECTION_KEY_JSON, marshaller.marshal(chosenDetectors));
		} catch (Exception e) {
			logger.error("Error saving detector selection", e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void loadState(Map<String, String> persistedState) {
		String json = persistedState.get(DETECTOR_SELECTION_KEY_JSON);
		if (json == null || json.isEmpty()) { // This happens when client is reset || if no detectors are configured.
			return;
		}
		IMarshallerService marshaller = getEclipseContext().get(IMarshallerService.class);
		try {
			chosenDetectors = marshaller.unmarshal(json, List.class);
		} catch (Exception e) {
			logger.error("Error loading detector selection", e);
		}
	}

}
