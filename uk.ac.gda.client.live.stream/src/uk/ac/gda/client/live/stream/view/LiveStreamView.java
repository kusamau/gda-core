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

package uk.ac.gda.client.live.stream.view;

import static uk.ac.gda.client.live.stream.Activator.getService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.eclipse.dawnsci.analysis.api.io.IRemoteDatasetService;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.dawnsci.plotting.api.IPlottingService;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.axis.IAxis;
import org.eclipse.dawnsci.plotting.api.region.IROIListener;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegionListener;
import org.eclipse.dawnsci.plotting.api.region.ROIEvent;
import org.eclipse.dawnsci.plotting.api.region.RegionEvent;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace.DownsampleType;
import org.eclipse.january.dataset.DataEvent;
import org.eclipse.january.dataset.IDataListener;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IDatasetConnector;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.device.detector.nxdetector.roi.ImutableRectangularIntegerROI;
import gda.factory.Finder;
import uk.ac.diamond.scisoft.analysis.plotclient.ScriptingConnection;
import uk.ac.gda.client.live.stream.LiveStreamConnection;
import uk.ac.gda.client.live.stream.LiveStreamConnection.IAxisChangeListener;
import uk.ac.gda.client.live.stream.LiveStreamException;
import uk.ac.gda.client.live.stream.handlers.SnapshotData;

/**
 * A RCP view for connecting to and displaying a live MJPEG stream. The intension is to provide a easy way for cameras
 * to be integrated into GDA, with minimal Spring configuration.
 * <p>
 * To setup this view in Spring create a {@link CameraConfiguration} in client Spring
 * <p>
 * For additional docs see <a href="http://confluence.diamond.ac.uk/x/1wWKAg">Setup Live Stream Camera View</a>
 *
 * @author James Mudd
 */
public class LiveStreamView extends ViewPart {

	public static final String LIVE_CAMERA_STREAM = "Live camera stream";

	public static final String ID = "uk.ac.gda.client.live.stream.view.LiveStreamView";

	private static final Logger logger = LoggerFactory.getLogger(LiveStreamView.class);

	private IPlottingSystem<Composite> plottingSystem;
	private IImageTrace iTrace;
	private CameraConfiguration camConfig;
	private Composite parent;
	private LiveStreamConnection liveStreamConnection;
	private Text errorText;
	private String cameraName;
	private long frameCounter = 0;
	private ScriptingConnection scriptingConnection;

	private final IAxisChangeListener axisChangeListener = this::updateAxes;

	private final IDataListener shapeListener = new IDataListener() {

		private int[] oldShape;

		@Override
		public void dataChangePerformed(DataEvent evt) {
			final Display display = PlatformUI.getWorkbench().getDisplay();
			// Check if the shape has changed, if so rescale
			if (!Arrays.equals(evt.getShape(), oldShape)) {
				oldShape = evt.getShape();
				// Need to be in the UI thread to do rescaling
				display.asyncExec(() -> {
					plottingSystem.autoscaleAxes();
					iTrace.rehistogram();
				});
				updateAxes();
			}
			// Update the frame count in the UI thread
			display.asyncExec(() -> plottingSystem.setTitle(cameraName + ": " + liveStreamConnection.getStreamType() + " - Frame: " + Long.toString(frameCounter++)));
		}
	};

	@Override
	public void createPartControl(final Composite parent) {
		this.parent = parent;

		if (getService(IRemoteDatasetService.class) == null) {
			displayAndLogError(parent, "Cannot create Live Stream: no remote dataset service is available");
			return;
		}

		if (getService(IPlottingService.class) == null) {
			displayAndLogError(parent, "Cannot create Live Stream: no plotting service is available");
			return;
		}

		// Check if the secondary id is set if so open the view else ask the user to choose a camera
		if (getViewSite().getSecondaryId() != null) {
			createLivePlot(parent, getViewSite().getSecondaryId());
		} else {
			createCameraSelector(parent);
		}
	}

	private void createCameraSelector(final Composite parent) {
		// Find all the implemented cameras. This is currently using the finder but could use OSGi instead.
		List<CameraConfiguration> cameras = Finder.getInstance().listLocalFindablesOfType(CameraConfiguration.class);
		final Map<String, CameraConfiguration> cameraMap = new TreeMap<>();
		for (CameraConfiguration cam : cameras) {
			if (cam.getDisplayName() != null) {
				cameraMap.put(cam.getDisplayName(), cam);
			} else {
				logger.warn("No display name was set for camera id: {}. Using id instead", cam.getName());
				cameraMap.put(cam.getName(), cam);
			}
		}
		if (!cameraMap.isEmpty()) {
			logger.debug("Found {} cameras", cameras.size());

			// Setup composite layout
			parent.setLayout(new GridLayout(1, false));
			parent.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));

			Label cameraSelectorLabel = new Label(parent, SWT.NONE);
			cameraSelectorLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
			cameraSelectorLabel.setText("Select camera:");

			final org.eclipse.swt.widgets.List cameraSelector = new org.eclipse.swt.widgets.List(parent, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
			cameraSelector.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			cameraSelector.setItems(cameraMap.keySet().toArray(new String[0]));
			cameraSelector.setSelection(0);
			cameraSelector.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					reopenViewWithSecondaryId(cameraMap.get(cameraSelector.getItem(cameraSelector.getSelectionIndex())).getName());
				}
			});

			Button connectButton = new Button(parent, SWT.DEFAULT);
			connectButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, false));
			connectButton.setText("Connect");
			connectButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					// Get the cameras ID for the secondary ID
					reopenViewWithSecondaryId(cameraMap.get(cameraSelector.getItem(cameraSelector.getSelectionIndex())).getName());
				}
			});

		} else { // No cameras found
			displayAndLogError(parent, "No cameras were found");
		}
		return;
	}

	private String cameraIdFromSecondaryId(String secondaryId) {
		if (secondaryId.endsWith(StreamType.MJPEG.secondaryIdSuffix())) {
			return secondaryId.substring(0, secondaryId.lastIndexOf(StreamType.MJPEG.secondaryIdSuffix()));
		} else if (secondaryId.endsWith(StreamType.EPICS_ARRAY.secondaryIdSuffix())) {
			return secondaryId.substring(0, secondaryId.lastIndexOf(StreamType.EPICS_ARRAY.secondaryIdSuffix()));
		} else {
			return secondaryId;
		}
	}

	private StreamType streamTypeFromSecondaryId(String secondaryId) {
		if (secondaryId.endsWith(StreamType.MJPEG.secondaryIdSuffix())) {
			return StreamType.MJPEG;
		} else if (secondaryId.endsWith(StreamType.EPICS_ARRAY.secondaryIdSuffix())) {
			return StreamType.EPICS_ARRAY;
		} else {
			return null;
		}
	}

	/**
	 * This is the method that actually creates a MJPEG stream and sets up the plotting system.
	 * <p>
	 * To get here the secondary id of the view need to be set (hopefully to a valid camera id)
	 *
	 * @param parent
	 *            Composite to draw on
	 * @param secondaryId
	 *            The name of the camera to use and type of stream to display
	 */
	private void createLivePlot(final Composite parent, final String secondaryId) {
		logger.debug("Creating live stream plot with secondary ID: {}", secondaryId);
		final String cameraId = cameraIdFromSecondaryId(secondaryId);
		StreamType streamType = streamTypeFromSecondaryId(secondaryId);

		// Get the camera config from the finder
		camConfig = Finder.getInstance().find(cameraId);

		if (camConfig == null) {
			displayAndLogError(parent, "Camera configuration could not be found for camera ID " + cameraId);
			return;
		}

		if (streamType == null) {
			streamType = camConfig.getUrl() == null ? StreamType.EPICS_ARRAY : StreamType.MJPEG;
		}

		// Use camera ID (, i.e. camera device name) and stream type for the tab text, to keep it short
		setPartName(cameraId + ": " + streamType);

		if (camConfig.getDisplayName() != null) {
			cameraName = camConfig.getDisplayName();
		} else {
			cameraName = cameraId;
		}

		IActionBars actionBars = getViewSite().getActionBars();

		// Setup the plotting system
		try {
			plottingSystem = getService(IPlottingService.class).createPlottingSystem();
			plottingSystem.createPlotPart(parent, getPartName(), actionBars, PlotType.IMAGE, this);
			createScriptingConnection(getPartName());
		} catch (Exception e) {
			displayAndLogError(parent, "Could not create plotting system", e);
			return;
		}

		// hide the axes if we don't have a calibration bean
		for (IAxis axis : plottingSystem.getAxes()) {
			axis.setVisible(camConfig.getCameraCalibration() != null);
		}

		// Use the full camera name from the camera configuration, if available, for the plot title as it should better
		// describe the camera and we should have plenty of space for it.
		plottingSystem.setTitle(cameraName + ": " + streamType + " - No data yet");

		// Add useful plotting system actions
		configureActionBars(actionBars);

		// Fix the aspect ratio as is typically required for visible cameras
		plottingSystem.setKeepAspect(true);
		// Disable auto rescale as the live stream is constantly refreshing
		plottingSystem.setRescale(false);

		// Create a new trace.
		iTrace = plottingSystem.createImageTrace(LIVE_CAMERA_STREAM);

		// Attach the IDatasetConnector of the MJPEG stream to the trace.
		if (streamType == StreamType.MJPEG && camConfig.getUrl() == null) {
			displayAndLogError(parent, "MJPEG stream requested but no url defined for " + cameraName);
		}
		if (streamType == StreamType.EPICS_ARRAY && camConfig.getArrayPv() == null) {
			displayAndLogError(parent, "EPICS stream requested but no array PV defined for " + cameraName);
		}
		setupStream(streamType);

		// Try and make the stream run faster
		iTrace.setDownsampleType(DownsampleType.POINT);
		iTrace.setRescaleHistogram(false);
		// Plot the new trace.
		plottingSystem.addTrace(iTrace);
	}

	private void createScriptingConnection(String partName) {
		if( plottingSystem != null){
			scriptingConnection = new ScriptingConnection(partName);
			scriptingConnection.setPlottingSystem(plottingSystem);
		}
	}

	private void configureActionBars(IActionBars actionBars) {
		IToolBarManager toolBarManager = actionBars.getToolBarManager();

		// Setup the plotting system toolbar options
		List<String> requiredToolBarIds = Arrays.asList(
				"org.csstudio.swt.xygraph.autoscale",
				"org.dawb.common.ui.plot.tool",
				"org.dawb.workbench.plotting.histo",
				"org.dawnsci.plotting.system.preference.export",
				"org.eclipse.nebula.visualization.xygraph.figures.ZoomType",
				"org.dawb.workbench.ui.editors.plotting.swtxy.addRegions",
				"org.dawb.workbench.ui.editors.plotting.swtxy.removeRegions");

		// Remove all ToolBar contributions with Ids which are either undefined or not required
		Arrays.stream(toolBarManager.getItems())
			.filter(ci -> ci.getId() == null || requiredToolBarIds.stream().noneMatch(ci.getId()::contains))
			.forEach(toolBarManager::remove);
			// If getId() returns null then the match will not be performed as the || short circuits it, this
			// also prevents the NPE which would result from trying to match on a null Id.

		// Remove all Menu contributions
		IMenuManager menuManager = actionBars.getMenuManager();
		Arrays.stream(menuManager.getItems()).forEach(menuManager::remove);

		// Add the Reset button to restart the view
		toolBarManager.insertBefore(toolBarManager.getItems()[0].getId(), new Action() {
			@Override
			public void run() {
				reopenViewWithSecondaryId(getViewSite().getSecondaryId());
			}
			@Override
			public String getText() {
				return "Reset";
			}
		});

		actionBars.updateActionBars();
	}

	@Override
	public void setFocus() {
		if (plottingSystem != null) {
			plottingSystem.setFocus();
		}
	}

	private void displayAndLogError(final Composite parent, final String errorMessage) {
		displayAndLogError(parent, errorMessage, null);
	}

	private void displayAndLogError(final Composite parent, final String errorMessage, final Throwable throwable) {
		logger.error(errorMessage, throwable);
		if (errorText == null) {
			errorText = new Text(parent, SWT.LEFT | SWT.WRAP | SWT.BORDER);
			errorText.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseDoubleClick(MouseEvent e) {
					errorText.dispose();
					parent.layout(true);
					errorText=null;
				}
			});
			errorText.setToolTipText("Double click this message to remove it.");
			parent.layout(true);
		}
		StringBuilder s = new StringBuilder(errorText.getText());
		s.append("\n").append(errorMessage);
		if (throwable != null) {
			s.append("\n\t").append(throwable.getMessage());
		}
		errorText.setText(s.toString());
	}

	@Override
	// This method is required for the plotting tools to work.
	public <T> T getAdapter(final Class<T> clazz) {
		if (plottingSystem != null) {
			T adapter = plottingSystem.getAdapter(clazz);
			if (adapter != null) {
				return adapter;
			}
		}
		return super.getAdapter(clazz);
	}

	@Override
	public void dispose() {
		if (plottingSystem != null) {
			plottingSystem.dispose();
			plottingSystem = null;
		}
		if (liveStreamConnection != null) {
			try {
				liveStreamConnection.getStream().removeDataListener(shapeListener);
				liveStreamConnection.removeAxisMoveListener(axisChangeListener);
				liveStreamConnection.disconnect();
			} catch (Exception e) {
				logger.error("Error disconnecting remote data stream", e);
			} finally {
				liveStreamConnection = null;
			}
		}
		super.dispose();
	}

	/**
	 * Close this view and open again with the secondary ID specified
	 */
	private void reopenViewWithSecondaryId(final String secondaryId) {
		IWorkbenchPage page = getSite().getPage();
		page.hideView(this);
		try {
			page.showView(LiveStreamView.ID, secondaryId, IWorkbenchPage.VIEW_ACTIVATE);
		} catch (PartInitException e) {
			logger.error("Error activating Live MJPEG view with secondary ID {}", secondaryId, e);
		}
	}

	private void updateAxes() {
		final Display display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(() -> iTrace.setAxes(liveStreamConnection.getAxes(), false));
	}

	/**
	 * This should be called when starting a new stream. It also takes care of disconnecting old streams (if any)
	 *
	 * @param streamType The type of stream to use
	 */
	private void setupStream(StreamType streamType) {
		if (liveStreamConnection != null) {
			try {
				liveStreamConnection.disconnect();
			} catch (LiveStreamException e) {
				logger.error("Error disconnecting from live stream", e);
			}
		}

		liveStreamConnection = new LiveStreamConnection(camConfig, streamType);

		final IDatasetConnector dataset;
		try {
			dataset = liveStreamConnection.connect();
			dataset.addDataListener(shapeListener);
		} catch (LiveStreamException e) {
			displayAndLogError(parent, e.getMessage(), e);
			return;
		}

		// Connect the stream to the trace
		iTrace.setDynamicData(dataset);

		// Add the axes to the trace
		List<IDataset> axes = liveStreamConnection.getAxes();
		if (axes != null && axes.size() == 2) {
			iTrace.setAxes(liveStreamConnection.getAxes(), false);
			liveStreamConnection.addAxisMoveListener(axisChangeListener);
		}

		// Add the listener for updating the frame counter
		dataset.addDataListener(shapeListener);

		// Reset the frame counter
		frameCounter = 0;

		// Setup the ROI provider if configured
		if (camConfig.getRoiProvider() != null) {

			final IROIListener roiListener = new IROIListener.Stub() {
				@Override
				public void roiChanged(ROIEvent evt) {
					updateServerRois();
				}
			};

			plottingSystem.addRegionListener(new IRegionListener.Stub() {

				// Note regionS method
				@Override
				public void regionsRemoved(RegionEvent evt) {
					evt.getRegions().stream().forEach(region -> region.removeROIListener(roiListener));
					updateServerRois();
				}

				@Override
				public void regionRemoved(RegionEvent evt) {
					evt.getRegion().removeROIListener(roiListener);
					updateServerRois();
				}

				@Override
				public void regionAdded(RegionEvent evt) {
					evt.getRegion().addROIListener(roiListener);
					updateServerRois();
				}
			});
		}
	}

	private  void updateServerRois() {
		final Collection<IRegion> regions = plottingSystem.getRegions();

		// Check if any regions are non rectangular and warn if not
		if (regions.stream()
				.map(IRegion::getROI)
				.anyMatch(roi -> !(roi instanceof RectangularROI))) {
			logger.warn("{} contains non rectangular regions", camConfig.getDisplayName());
		}

		// Get the rectangular ROIs
		List<gda.device.detector.nxdetector.roi.RectangularROI<Integer>> rois = regions.stream()
				.map(IRegion::getROI)
				.filter(RectangularROI.class::isInstance) // Only use rectangular ROIs
				.map(RectangularROI.class::cast) // Cast to RectangularROI
				.map(ImutableRectangularIntegerROI::valueOf) // Create ImutableRectangularIntegerROI
				.collect(Collectors.toList());

		// Send the new ROIs to the server
		camConfig.getRoiProvider().updateRois(rois);
	}

	public IPlottingSystem<Composite> getPlottingSystem() {
		return plottingSystem;
	}

	public CameraConfiguration getActiveCameraConfiguration() {
		return camConfig;
	}

	public StreamType getActiveStreamType() {
		if (liveStreamConnection != null) {
			return liveStreamConnection.getStreamType();
		}
		return null;
	}

	public SnapshotData getSnapshot() {
		final SnapshotData snapshotData = new SnapshotData(plottingSystem.getTitle(), iTrace.getData().clone());
		final List<IDataset> axes = iTrace.getAxes();
		if (axes != null && !axes.isEmpty()) {
			snapshotData.setxAxis(axes.get(0));
			snapshotData.setyAxis(axes.get(1));
		}

		return snapshotData;
	}
}
