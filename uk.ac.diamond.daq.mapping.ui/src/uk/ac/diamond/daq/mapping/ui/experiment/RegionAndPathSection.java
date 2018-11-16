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

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.dawnsci.analysis.api.persistence.IMarshallerService;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.ui.di.UISynchronize;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.scanning.api.INameable;
import org.eclipse.scanning.api.device.models.IDetectorModel;
import org.eclipse.scanning.api.device.models.IMalcolmModel;
import org.eclipse.scanning.api.points.models.IScanPathModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.device.DeviceException;
import gda.device.Scannable;
import gda.factory.Finder;
import uk.ac.diamond.daq.mapping.api.IMappingRegionManager;
import uk.ac.diamond.daq.mapping.api.IMappingScanRegion;
import uk.ac.diamond.daq.mapping.api.IMappingScanRegionShape;
import uk.ac.diamond.daq.mapping.api.IScanModelWrapper;
import uk.ac.diamond.daq.mapping.impl.MappingStageInfo;
import uk.ac.diamond.daq.mapping.ui.MultiFunctionButton;
import uk.ac.diamond.daq.mapping.ui.path.AbstractPathEditor;
import uk.ac.diamond.daq.mapping.ui.path.PathEditorProvider;
import uk.ac.diamond.daq.mapping.ui.region.RegionEditorProvider;

/**
 * A section for configuring the region to scan and the path of the mapping scan.
 */
public class RegionAndPathSection extends AbstractMappingSection {

	private class RegionSelectorListener implements ISelectionChangedListener {

		private final PropertyChangeListener regionBeanPropertyChangeListener;

		private RegionSelectorListener() {
			this.regionBeanPropertyChangeListener = evt -> {
				plotter.updatePlotRegionFrom(scanRegion);
				updatePoints();
			};
		}

		@Override
		public void selectionChanged(SelectionChangedEvent event) {
			logger.debug("Region selection event: {}", event);

			// Get the new selection.
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			IMappingScanRegionShape selectedRegion = (IMappingScanRegionShape) selection.getFirstElement();

			changeRegion(selectedRegion);
		}

		private void changeRegion(IMappingScanRegionShape newRegion) {
			// We're going to replace the scan region with a new one
			// If the existing one is non-null, remove the property change listener from it
			if (scanRegion != null) {
				scanRegion.removePropertyChangeListener(regionBeanPropertyChangeListener);
			}

			// Set the new scan region
			scanRegion = newRegion;
			getMappingBean().getScanDefinition().getMappingScanRegion().setRegion(scanRegion);

			// Update the path selector with paths valid for the new region type
			// (The listener on the path selector will take care of propagating the change appropriately, and updating the GUI)
			// Do this before starting drawing the region (+ path ) with the plotting system because changing path after breaks the region drawing
			List<IScanPathModel> scanPathList = mappingRegionManager.getValidPaths(scanRegion);
			pathSelector.setInput(scanPathList);
			if (scanPathList.contains(scanPathModel)) {
				pathSelector.setSelection(new StructuredSelection(scanPathModel), true);
			} else if (!scanPathList.isEmpty()) {
				// Select the first path by default
				pathSelector.setSelection(new StructuredSelection(scanPathList.get(0)), true);
			} else {
				pathSelector.setSelection(StructuredSelection.EMPTY, true);
			}

			// If new scan region is non-null, add it to the plot and add the property change listener
			if (scanRegion != null) {
				plotter.createNewPlotRegion(scanRegion);
				scanRegion.addPropertyChangeListener(regionBeanPropertyChangeListener);
			}
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(RegionAndPathSection.class);
	private static final String MAPPING_STAGE_KEY_JSON = "mappingStageAxes.json";

	private final PropertyChangeListener pathBeanPropertyChangeListener = evt -> updatePoints();

	private Composite regionAndPathComposite;
	private AbstractModelEditor<IScanPathModel> pathEditor;
	private IMappingScanRegionShape scanRegion = null;
	private IScanPathModel scanPathModel = null;
	private PathInfoCalculatorJob pathCalculationJob;
	private PlottingController plotter;
	private IMappingRegionManager mappingRegionManager;

	private AbstractModelEditor<IMappingScanRegionShape> regionEditor;
	private ComboViewer regionSelector;
	private ComboViewer pathSelector;
	private Optional<String> selectedMalcolmDeviceName = Optional.empty();

	@Override
	protected void initialize(AbstractSectionsView mappingView) {
		super.initialize(mappingView);
		plotter = getService(PlottingController.class);
		mappingRegionManager = getService(IMappingRegionManager.class);
		pathCalculationJob = createPathCalculationJob();
	}

	private PathInfoCalculatorJob createPathCalculationJob() {
		PathInfoCalculatorJob job = ContextInjectionFactory.make(PathInfoCalculatorJob.class, getEclipseContext());
		UISynchronize uiSync = getService(UISynchronize.class);
		job.addJobChangeListener(new JobChangeAdapter() {
			@Override
			public void running(IJobChangeEvent event) {
				uiSync.asyncExec(() -> {
					setStatusMessage("Scan path calculation in progress");
					plotter.removePath();
				});
			}
			@Override
			public void done(final IJobChangeEvent event) {
				uiSync.asyncExec(() -> {
					IStatus result = event.getResult();
					if (result.getSeverity() == IStatus.CANCEL) {
						setStatusMessage("Scan path calculation was cancelled");
					} else if (!result.isOK()) {
						setStatusMessage("Error in scan path calculation - see log for details");
						logger.warn("Error in scan path calculation", result.getException());
					}
					// else, calculation completed normally and the status text will be updated from the new PathInfo
				});
			}
		});

		return job;
	}

	@Override
	public void createControls(Composite parent) {
		// Make a custom section for handling the mapping region
		regionAndPathComposite = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(regionAndPathComposite);
		GridLayoutFactory.fillDefaults().numColumns(2).spacing(0, 0).applyTo(regionAndPathComposite);

		// Prepare a grid data factory for controls which will need to grab space horizontally
		GridDataFactory horizontalGrabGridData = GridDataFactory.swtDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false);


		// Make the region selection
		Composite regionComboComposite = new Composite(regionAndPathComposite, SWT.NONE);
		horizontalGrabGridData.span(1, 1).applyTo(regionComboComposite);
		GridLayoutFactory.swtDefaults().numColumns(3).applyTo(regionComboComposite);
		Label regionLabel = new Label(regionComboComposite, SWT.NONE);
		regionLabel.setText("Region shape:");
		regionSelector = new ComboViewer(regionComboComposite);
		horizontalGrabGridData.applyTo(regionSelector.getControl());
		regionSelector.getCombo().setToolTipText("Select a scan region shape. The shape can then be drawn on the map, or you can type numbers below.");

		MultiFunctionButton newRegion = new MultiFunctionButton();
		newRegion.addFunction("Draw region", "Draw region by dragging on map",
				new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/map--pencil.png")),
				()-> regionSelector.setSelection(regionSelector.getSelection()));
		newRegion.addFunction("Place default region", "Place the default region on current stage position",
				new Image(Display.getCurrent(), getClass().getResourceAsStream("/icons/map-pin.png")),
				this::createDefaultRegionAtStagePosition);
		newRegion.draw(regionComboComposite);

		// Make the path selection
		Composite pathComboComposite = new Composite(regionAndPathComposite, SWT.NONE);
		horizontalGrabGridData.applyTo(pathComboComposite);
		GridLayoutFactory.swtDefaults().numColumns(3).applyTo(pathComboComposite);
		Label pathLabel = new Label(pathComboComposite, SWT.NONE);
		pathLabel.setText("Scan path:");
		pathSelector = new ComboViewer(pathComboComposite);
		horizontalGrabGridData.applyTo(pathSelector.getControl());

		Button configureStageButton = new Button(pathComboComposite, SWT.PUSH);
		configureStageButton.setToolTipText("Configure mapping stage");
		configureStageButton.setImage(MappingExperimentUtils.getImage("icons/gear.png"));
		configureStageButton.addListener(SWT.Selection, event -> {
			MappingStageInfo mappingStage = getService(MappingStageInfo.class);
			EditMappingStageDialog dialog = new EditMappingStageDialog(getShell(), mappingStage, selectedMalcolmDeviceName);
			if (dialog.open() == Window.OK) {
				rebuildMappingSection();
			}
		});

		// Add logic
		regionSelector.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IMappingScanRegionShape) {
					IMappingScanRegionShape mappingRegion = (IMappingScanRegionShape) element;
					return mappingRegion.getName();
				}
				return super.getText(element);
			}
		});

		regionSelector.setContentProvider(ArrayContentProvider.getInstance());
		List<IMappingScanRegionShape> regionList = mappingRegionManager.getTemplateRegions();
		regionSelector.setInput(regionList.toArray());

		regionSelector.addSelectionChangedListener(new RegionSelectorListener());

		pathSelector.setContentProvider(ArrayContentProvider.getInstance());
		pathSelector.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof IScanPathModel) {
					IScanPathModel scanPath = (IScanPathModel) element;
					return scanPath.getName();
				}
				return super.getText(element);
			}
		});

		pathSelector.addSelectionChangedListener(event -> {
			logger.debug("Path selection event: {}", event);

			// Get the new selection.
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			IScanPathModel selectedPath = (IScanPathModel) selection.getFirstElement();
			changePath(selectedPath);
		});

		updateControls();
	}

	private void changePath(IScanPathModel newPath) {
		logger.debug("Changing path to {}", newPath);

		// We're going to replace the scan path with a new one
		// If the existing one is non-null, remove the property change listener from it
		if (scanPathModel != null) {
			scanPathModel.removePropertyChangeListener(pathBeanPropertyChangeListener);
		}

		// Set the new scan path. If non-null, add the property change listener
		scanPathModel = newPath;
		getMappingBean().getScanDefinition().getMappingScanRegion().setScanPath(scanPathModel);
		if (scanPathModel != null) {
			scanPathModel.addPropertyChangeListener(pathBeanPropertyChangeListener);
		}

		// Update the GUI to reflect the path changes
		rebuildMappingSection();
		updatePoints();
	}

	private void createDefaultRegionAtStagePosition() {
		final MappingStageInfo mappingStage = getService(MappingStageInfo.class);
		final double xAxisPosition = getAxisPosition(mappingStage.getActiveFastScanAxis());
		final double yAxisPosition = getAxisPosition(mappingStage.getActiveSlowScanAxis());

		scanRegion = mappingRegionManager.getTemplateRegion(scanRegion.getClass());
		scanRegion.centre(xAxisPosition, yAxisPosition);
		getMappingBean().getScanDefinition().getMappingScanRegion().setRegion(scanRegion);
		updateControls();
	}

	private double getAxisPosition(String axisName) {
		Scannable axis = Finder.getInstance().find(axisName);
		try {
			return (double) axis.getPosition();
		} catch (DeviceException e) {
			logger.error("Could not get position of axis {}", axisName, e);
			return 0;
		}
	}

	@Override
	protected void updateControls() {
		IMappingScanRegion mappingScanRegion = getMappingBean().getScanDefinition().getMappingScanRegion();
		scanRegion = mappingScanRegion.getRegion();
		scanPathModel = mappingScanRegion.getScanPath();

		// Replace the region model of the same class with the new region from the mapping bean
		List<IMappingScanRegionShape> regionList = mappingRegionManager.getTemplateRegions();
		if (scanRegion == null) {
			scanRegion = regionList.get(0);
		} else {
			for (int i = 0; i < regionList.size(); i++) {
				if (regionList.get(i).getClass().equals(scanRegion.getClass())) {
					regionList.set(i, scanRegion);
				}
			}
		}
		regionSelector.setInput(regionList.toArray());

		// Replace the scan path model of the same class with the new scan path model from the mapping bean
		List<IScanPathModel> scanPathList = mappingRegionManager.getValidPaths(scanRegion);
		if (scanPathModel == null) {
			scanPathModel = scanPathList.get(0);
		} else {
			for (int i = 0; i < scanPathList.size(); i++) {
				if (scanPathList.get(i).getClass().equals(scanPathModel.getClass())) {
					scanPathList.set(i, scanPathModel);
				}
			}
		}
		pathSelector.setInput(scanPathList);

		// Recreate the contents of the beans
		rebuildMappingSection();

		// Set the selection on the combo viewers (has to be done after the above)
		regionSelector.setSelection(new StructuredSelection(scanRegion));
		pathSelector.setSelection(new StructuredSelection(scanPathModel));

		// Plot the scan region. This will cancel the region drawing event in the plotting system to avoid user confusion at startup
		plotter.updatePlotRegionFrom(scanRegion);
	}

	/**
	 * Call this to rebuild the mapping section. Only required when the underlying beans are swapped.
	 */
	protected void rebuildMappingSection() {
		// Remove the old controls
		if (regionEditor != null) {
			regionEditor.dispose();
		}
		if (pathEditor != null) {
			pathEditor.dispose();
		}

		// Scan Region
		final IMappingScanRegionShape mappingScanRegion = getMappingBean().getScanDefinition().getMappingScanRegion().getRegion();
		if (mappingScanRegion == null) {
			return; // We can't build a UI to edit null
		}

		regionEditor = RegionEditorProvider.createRegionEditor(mappingScanRegion, getEclipseContext());
		regionEditor.createEditorPart(regionAndPathComposite);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false).applyTo(regionAndPathComposite);

		// Scan Path
		final IScanPathModel scanPath = getMappingBean().getScanDefinition().getMappingScanRegion().getScanPath();
		if (scanPath == null) {
			return; // We can't build a UI to edit null
		}
		pathEditor = PathEditorProvider.createPathComposite(scanPath, getEclipseContext());
		pathEditor.createEditorPart(regionAndPathComposite);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.BEGINNING).grab(true, false).applyTo(regionAndPathComposite);

		detectorsChanged(getMappingBean().getDetectorParameters().stream()
							.filter(IScanModelWrapper<IDetectorModel>::isIncludeInScan)
							.collect(Collectors.toList()));

		relayoutMappingView();
	}

	private void updatePoints() {
		pathCalculationJob.cancel();
		if (scanPathModel != null && scanRegion != null) {
			pathCalculationJob.setScanPathModel(scanPathModel);
			pathCalculationJob.setScanRegion(scanRegion);
			pathCalculationJob.schedule();
		}
	}

	@Override
	public void setFocus() {
		if (regionAndPathComposite != null) {
			regionAndPathComposite.setFocus();
		}
	}

	public void detectorsChanged(List<IScanModelWrapper<IDetectorModel>> selectedDetectors) {
		selectedMalcolmDeviceName = selectedDetectors.stream()
									.map(IScanModelWrapper<IDetectorModel>::getModel)
									.filter(IMalcolmModel.class::isInstance)
									.map(INameable::getName)
									.findFirst();
		((AbstractPathEditor) pathEditor).setContinuousEnabled(selectedMalcolmDeviceName.isPresent());
	}

	@Override
	protected void saveState(Map<String, String> persistedState) {
		final IMarshallerService marshaller = getService(IMarshallerService.class);
		final MappingStageInfo mappingStage = getService(MappingStageInfo.class);
		try {
			persistedState.put(MAPPING_STAGE_KEY_JSON, marshaller.marshal(mappingStage));
		} catch (Exception e) {
			logger.error("Error saving mapping stage axes selection", e);
		}
	}

	@Override
	protected void loadState(Map<String, String> persistedState) {
		String json = persistedState.get(MAPPING_STAGE_KEY_JSON);
		if (json == null || json.isEmpty()) return;
		IMarshallerService marshaller = getService(IMarshallerService.class);
		try {
			final MappingStageInfo savedStage = marshaller.unmarshal(json, MappingStageInfo.class);
			final MappingStageInfo mappingStage = getService(MappingStageInfo.class);
			mappingStage.setActiveFastScanAxis(savedStage.getActiveFastScanAxis());
			mappingStage.setActiveSlowScanAxis(savedStage.getActiveSlowScanAxis());
			mappingStage.setAssociatedAxis(savedStage.getAssociatedAxis());
		} catch (Exception e) {
			logger.error("Error restoring mapping stage axes selection", e);
		}
	}
}

