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

package uk.ac.diamond.daq.client.gui.exitslit.configuration;

import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;
import static uk.ac.diamond.daq.client.gui.exitslit.configuration.ConfigureExitSlitsUtils.COLOUR_RED;
import static uk.ac.diamond.daq.client.gui.exitslit.configuration.ConfigureExitSlitsUtils.COLOUR_WHITE;
import static uk.ac.diamond.daq.client.gui.exitslit.configuration.ConfigureExitSlitsUtils.createCheckBox;
import static uk.ac.diamond.daq.client.gui.exitslit.configuration.ConfigureExitSlitsUtils.createComposite;
import static uk.ac.diamond.daq.client.gui.exitslit.configuration.ConfigureExitSlitsUtils.createLabel;
import static uk.ac.diamond.daq.client.gui.exitslit.configuration.ConfigureExitSlitsUtils.displayError;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.device.DeviceException;
import gda.observable.IObserver;
import uk.ac.gda.api.camera.CameraControl;
import uk.ac.gda.api.camera.CameraState;
import uk.ac.gda.client.live.stream.LiveStreamConnection;
import uk.ac.gda.client.live.stream.LiveStreamException;
import uk.ac.gda.client.live.stream.view.CameraConfiguration;
import uk.ac.gda.client.live.stream.view.LivePlottingComposite;
import uk.ac.gda.client.live.stream.view.StreamType;

/**
 * Dialogue giving a wizard-like interface to guide the user through configuring exit slits
 * <p>
 * The dialogue consists of:
 * <ul>
 * <li>a title area, whose text varies as the user moves through the "wizard pages"</li>
 * <li>a live view of the stream from the exit slit diagnostic camera with the ability to change the exposure time</li>
 * <li>a stack of composites containing the controls for the current "page"</li>
 * <li>wizard-like Back, Next, Cancel & Finish buttons.</li>
 * </ul>
 */
public class ConfigureExitSlitsDialog extends TitleAreaDialog {
	private static final Logger logger = LoggerFactory.getLogger(ConfigureExitSlitsDialog.class);

	private static final String TITLE = "Configure exit slits";

	private static final int PLOT_SIZE_HORIZONTAL = 400;
	private static final int PLOT_SIZE_VERTICAL = 400;

	private final ConfigureExitSlitsParameters params;
	private final IObserver updateHandler = this::handleUpdates;

	private LiveStreamConnection liveStreamConnection;
	private LivePlottingComposite plottingComposite;
	private Text txtExposureTime;

	private StackLayout controlCompositeLayout;
	private Composite controlComposite;
	private int currentPage;

	private Button btnBack;
	private Button btnNext;
	private Button btnCancel;
	private Button btnFinish;
	private Button chkAcquire;

	private ConfigureExitSlitsComposite[] controlSections;

	public ConfigureExitSlitsDialog(Shell parent, ConfigureExitSlitsParameters params) {
		super(parent);
		this.params = params;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(TITLE);
	}

	@Override
    protected Control createDialogArea(Composite parent) {
		final CameraConfiguration cameraConfig = params.getCameraConfig();
		final StreamType streamType = cameraConfig.getUrl() == null ? StreamType.EPICS_ARRAY : StreamType.MJPEG;

		final Composite container = (Composite) super.createDialogArea(parent);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(container);
        GridLayoutFactory.fillDefaults().applyTo(container);
        container.setBackground(COLOUR_WHITE);

		// Live stream view
		final CameraControl cameraControl = params.getCameraControl();
		try {
	        cameraControl.startAcquiring();
			liveStreamConnection = new LiveStreamConnection(cameraConfig, streamType);
			createPlottingView(container);
			cameraControl.stopAcquiring();
		} catch (DeviceException e) {
			final String message = String.format("Error acquiring from camera %s", cameraControl.getName());
			displayError("Data acquisition error", message, e, logger);
		}

		createSeparator(container);

		// Variable content
		createControlSection(container);

        return container;
    }

	private void createSeparator(final Composite container) {
		final Label separator = new Label(container, SWT.HORIZONTAL | SWT.SEPARATOR);
		GridDataFactory.fillDefaults().applyTo(separator);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		btnBack = createButton(parent, IDialogConstants.BACK_ID, IDialogConstants.BACK_LABEL, false);
		btnBack.addSelectionListener(widgetSelectedAdapter(e -> goToPage(currentPage - 1)));

		btnNext = createButton(parent, IDialogConstants.NEXT_ID, IDialogConstants.NEXT_LABEL, false);
		btnNext.addSelectionListener(widgetSelectedAdapter(e -> goToPage(currentPage + 1)));

		btnCancel = createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);

		btnFinish = createButton(parent, IDialogConstants.FINISH_ID, IDialogConstants.FINISH_LABEL, false);
		btnFinish.addSelectionListener(widgetSelectedAdapter(e -> close()));

		updateButtons();
	}

	private void createPlottingView(Composite parent) {
		// Live view of the camera
		try {
			plottingComposite = new LivePlottingComposite(parent, SWT.NONE, TITLE, liveStreamConnection);
			GridDataFactory.fillDefaults().hint(PLOT_SIZE_HORIZONTAL, PLOT_SIZE_VERTICAL).applyTo(plottingComposite);
			plottingComposite.setShowAxes(true);
			plottingComposite.setCrosshairs(params.getCrosshairXPosition(), params.getCrosshairYPosition());
		} catch (Exception e) {
			logger.error("Error creating plotting view", e);
			return;
		}

		createSeparator(parent);

		// Control the camera & plot
		final Composite plottingControlComposite = createComposite(parent, 5);

		// Show/set exposure time
		createLabel(plottingControlComposite, "Exposure time");

		txtExposureTime = new Text(plottingControlComposite, SWT.BORDER);
		GridDataFactory.swtDefaults().hint(70, SWT.DEFAULT).applyTo(txtExposureTime);
		displayExposureTime();

		final Label lblExposureTimeMessage = new Label(plottingControlComposite, SWT.NONE);
		GridDataFactory.swtDefaults().hint(250, SWT.DEFAULT).applyTo(lblExposureTimeMessage);
		lblExposureTimeMessage.setForeground(COLOUR_RED);
		lblExposureTimeMessage.setBackground(COLOUR_WHITE);

		txtExposureTime.addModifyListener(e -> {
			if (exposureTimeValid()) {
				lblExposureTimeMessage.setText("");
				setExposureTime();
			} else {
				lblExposureTimeMessage.setText("Invalid exposure time");
			}
			updateButtons();
		});

		// Show/hide axes
		final Button chkShowAxes = createCheckBox(plottingControlComposite, "Show axes");
		chkShowAxes.setSelection(plottingComposite.isShowAxes());
		chkShowAxes.addSelectionListener(widgetSelectedAdapter(e -> plottingComposite.setShowAxes(chkShowAxes.getSelection())));

		// Start/stop acquiring
		chkAcquire = createCheckBox(plottingControlComposite, "Acquire data");
		final CameraControl cameraControl = params.getCameraControl();
		try {
			chkAcquire.setSelection(cameraControl.getAcquireState() == CameraState.ACQUIRING);
		} catch (DeviceException e) {
			final String message = String.format("Error getting acquire state state of camera %s", cameraControl.getName());
			displayError("Camera error", message, e, logger);
		}
		chkAcquire.addSelectionListener(widgetSelectedAdapter(e -> {
			try {
				if (chkAcquire.getSelection()) {
					cameraControl.startAcquiring();
				} else {
					cameraControl.stopAcquiring();
				}
			} catch (DeviceException ex) {
				final String message = String.format("Error accessing camera %s", cameraControl.getName());
				displayError("Camera error", message, ex, logger);
			}
		}));
	}

	/**
	 * Create a stack layout of composites corresponding to the various stages of the process
	 */
	private void createControlSection(Composite parent) {
		controlCompositeLayout = new StackLayout();
		controlComposite = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(controlComposite);
		controlComposite.setLayout(controlCompositeLayout);
		controlComposite.setBackground(COLOUR_WHITE);

		controlSections = new ConfigureExitSlitsComposite[] {
			new ConfigureExitSlitsMoveDiagnostic(controlComposite, SWT.NONE, "Move diagnostic into beam", "Press 'Move in' if the diagnostic stick is not already in the beam", params, true),
			new ConfigureExitSlitsMoveApertures(controlComposite, SWT.NONE, "Move apertures out", "Move exit slit apertures out of beam", params, false),
			new ConfigureExitSlitsNudgeMotor(controlComposite, SWT.NONE, "Tweak mirror pitch", "Tweak mirror pitch till the beam is on the cross hairs", params.getMirrorPitchMotor(), "Mirror pitch", params.getPitchTweakAmount()),
			new ConfigureExitSlitsMoveApertures(controlComposite, SWT.NONE, "Move apertures in", "Move exit slit apertures into beam", params, true),
			new ConfigureExitSlitsNudgeMotor(controlComposite, SWT.NONE, "Tweak slit position", "Tweak position of horizontal slits then stop acquisition", params.getApertureArrayXMotor(), "Slit position", params.getSlitPositionTweakAmount()),
			new ConfigureExitSlitsMoveDiagnostic(controlComposite, SWT.NONE, "Move diagnostic out of beam", "Press 'Move out' to move the diagnostic stick out of the beam", params, false),
			new ConfigureExitSlitsFinish(controlComposite, SWT.NONE)
		};

		createSeparator(parent);

		goToPage(0);
	}

	private void goToPage(int newPage) {
		final ConfigureExitSlitsComposite currentControlSection = controlSections[currentPage];
		final ConfigureExitSlitsComposite newControlSection = controlSections[newPage];

		currentControlSection.deleteIObserver(updateHandler);
		controlCompositeLayout.topControl = controlSections[newPage];
		newControlSection.addIObserver(updateHandler);
		controlComposite.pack();
		controlComposite.layout();

		currentPage = newPage;
		updateHeading();
		updateButtons();
	}

	/**
	 * Changes in a control section may require us to change the state of the navigation buttons
	 */
	private void handleUpdates(@SuppressWarnings("unused") Object source, @SuppressWarnings("unused") Object arg) {
		Display.getDefault().asyncExec(this::updateButtons);
	}

	private double parseExposureTime() {
		return Double.parseDouble(txtExposureTime.getText());
	}

	private boolean exposureTimeValid() {
		try {
			final double exposureTime = parseExposureTime();
			return exposureTime > 0.0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Set the exposure time on the camera to the value entered by the user
	 */
	private void setExposureTime() {
		try {
			final double exposureTime = parseExposureTime();
			params.getCameraControl().setAcquireTime(exposureTime);
		} catch (Exception ex) {
			logger.error("Error setting exposure time on camera {}", params.getCameraControl().getName(), ex);
		}
	}

	/**
	 * Get the current exposure time from the camera and display in the page
	 */
	private void displayExposureTime() {
		try {
			txtExposureTime.setText(Double.toString(params.getCameraControl().getAcquireTime()));
		} catch (DeviceException e) {
			logger.error("Error getting exposure time from camera {}", params.getCameraControl().getName(), e);
			txtExposureTime.setText("#ERR");
		}
	}

	/**
	 * Update the state of the navigation buttons depending on the state of the current control section and our progress
	 * through the control screens.
	 */
	private void updateButtons() {
		// Buttons may not be created when first called
		if (btnBack != null && btnNext != null && btnCancel != null && btnFinish != null) {
			final ConfigureExitSlitsComposite currentControl = controlSections[currentPage];
			btnBack.setEnabled(currentPage > 0 && currentControl.canGoToPreviousPage());
			btnNext.setEnabled(currentPage < controlSections.length - 1 && currentControl.canGoToNextPage());
			btnCancel.setEnabled(currentControl.canCancel());
			btnFinish.setEnabled(currentControl.canFinish());
			try {
				chkAcquire.setSelection(params.getCameraControl().getAcquireState() == CameraState.ACQUIRING);
			} catch (DeviceException e) {
				logger.error("Error updating 'Acquire data' button", e);
			}
		}
	}

	/**
	 * Update heading section based on the current control section
	 */
	private void updateHeading() {
		final ConfigureExitSlitsComposite currentControl = controlSections[currentPage];
		setTitle(currentControl.getTitle());
		setMessage(currentControl.getDescription());
	}

	@Override
	public boolean close() {
		plottingComposite.dispose();
		try {
			liveStreamConnection.disconnect();
		} catch (LiveStreamException e) {
			logger.error("Error closing live stream connection", e);
		}
		for (ConfigureExitSlitsComposite controlSection : controlSections) {
			controlSection.dispose();
		}
		return super.close();
	}
}