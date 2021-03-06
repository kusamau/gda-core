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

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.dawnsci.analysis.api.persistence.IMarshallerService;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.scanning.api.event.scan.ScanBean;
import org.eclipse.scanning.api.scan.IFilePathService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.daq.mapping.api.IMappingExperimentBean;
import uk.ac.diamond.daq.mapping.impl.MappingExperimentBean;
import uk.ac.diamond.daq.mapping.impl.MappingStageInfo;
import uk.ac.diamond.daq.mapping.ui.MappingUIConstants;

/**
 * A section containing:<ul>
 * <li>a button to submit a scan to the queue;</li>
 * <li>a button to save a scan to disk;</li>
 * <li>a button to load a scan from disk.</li>
 * </ul>
 */
public class SubmitScanSection extends AbstractMappingSection {

	private static final String[] FILE_FILTER_NAMES = new String[] { "Mapping Scan Files", "All Files (*.*)" };
	private static final String[] FILE_FILTER_EXTENSIONS = new String[] { "*.map", "*.*" };
	private static final Logger logger = LoggerFactory.getLogger(SubmitScanSection.class);

	private Composite composite;

	private String description = "Mapping scan";

	private String buttonText = "Queue Scan";

	private RGB buttonColour = null;

	@Override
	public boolean createSeparator() {
		return false;
	}

	@Override
	public void createControls(Composite parent) {
		super.createControls(parent);
		composite = new Composite(parent, SWT.NONE);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.BOTTOM).applyTo(composite);
		GridLayoutFactory.swtDefaults().numColumns(5).applyTo(composite);

		// Button to submit a scan to the queue
		final Button submitScanButton = new Button(composite, SWT.PUSH);
		submitScanButton.setText(buttonText);
		if (buttonColour != null) {
			submitScanButton.setBackground(new Color(Display.getDefault(), buttonColour));
		}
		GridDataFactory.swtDefaults().applyTo(submitScanButton);
		submitScanButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				submitScan();
			}
		});

		// Button to copy a scan to the clipboard
		final Button copyScanCommandButton = new Button(composite, SWT.PUSH);
		copyScanCommandButton.setImage(MappingExperimentUtils.getImage("icons/copy.png"));
		copyScanCommandButton.setToolTipText("Copy the scan command to the system clipboard");
		GridDataFactory.fillDefaults().grab(true, false).align(SWT.RIGHT, SWT.CENTER).applyTo(copyScanCommandButton);
		copyScanCommandButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				copyScanToClipboard();
			}
		});

		// Button to load a scan from disk
		final Button loadButton = new Button(composite, SWT.PUSH);
		loadButton.setImage(MappingExperimentUtils.getImage("icons/open.png"));
		loadButton.setToolTipText("Load a scan from the file system");
		GridDataFactory.swtDefaults().applyTo(loadButton);
		loadButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				loadScan();
			}
		});

		// Button to save a scan to disk
		Button saveButton = new Button(composite, SWT.PUSH);
		saveButton.setImage(MappingExperimentUtils.getImage("icons/save.png"));
		saveButton.setToolTipText("Save a scan to the file system");
		GridDataFactory.swtDefaults().applyTo(saveButton);
		saveButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				saveScan();
			}
		});
	}

	private void loadScan() {
		final String fileName = chooseFileName(SWT.OPEN);
		if (fileName == null) return;

		try {
			byte[] bytes = Files.readAllBytes(Paths.get(fileName));
			final String json = new String(bytes, "UTF-8");

			final IMarshallerService marshaller = getService(IMarshallerService.class);
			IMappingExperimentBean mappingBean = marshaller.unmarshal(json, MappingExperimentBean.class);
			getMappingView().setMappingBean(mappingBean);
			loadStageInfoSnapshot();
			getMappingView().updateControls();
		} catch (Exception e) {
			final String errorMessage = "Could not load a mapping scan from file: " + fileName;
			logger.error(errorMessage, e);
			ErrorDialog.openError(getShell(), "Load Scan", errorMessage,
					new Status(IStatus.ERROR, MappingUIConstants.PLUGIN_ID, errorMessage, e));
		}
	}

	private void saveScan() {
		final String fileName = chooseFileName(SWT.SAVE);
		if (fileName == null) return;

		captureStageInfoSnapshot();
		final IMappingExperimentBean mappingBean = getMappingBean();
		final IMarshallerService marshaller = getService(IMarshallerService.class);
		try {
			logger.trace("Serializing the state of the mapping view to json");
			final String json = marshaller.marshal(mappingBean);
			logger.trace("Writing state of mapping view to file: {}", fileName);
			Files.write(Paths.get(fileName), json.getBytes(Charset.forName("UTF-8")), StandardOpenOption.CREATE);
		} catch (Exception e) {
			final String errorMessage = "Could not save the mapping scan to file: " + fileName;
			logger.error(errorMessage, e);
			ErrorDialog.openError(getShell(), "Save Scan", errorMessage,
					new Status(IStatus.ERROR, MappingUIConstants.PLUGIN_ID, errorMessage, e));
		}
	}

	private String chooseFileName(int fileDialogStyle) {
		final FileDialog dialog = new FileDialog(getShell(), fileDialogStyle);
		dialog.setFilterNames(FILE_FILTER_NAMES);
		dialog.setFilterExtensions(FILE_FILTER_EXTENSIONS);
		final String visitConfigDir = getService(IFilePathService.class).getVisitConfigDir();
		dialog.setFilterPath(visitConfigDir);
		dialog.setOverwrite(true);

		return dialog.open();
	}

	private void loadStageInfoSnapshot() {
		// push the saved stage info in the mapping bean to the OSGi component
		IMappingExperimentBean bean = getMappingBean();
		MappingStageInfo stage = getService(MappingStageInfo.class);
		stage.merge((MappingStageInfo) bean.getStageInfoSnapshot());
	}

	private void captureStageInfoSnapshot() {
		// capture the current MappingStageInfo in the mapping bean
		IMappingExperimentBean bean = getMappingBean();
		MappingStageInfo stage = getService(MappingStageInfo.class);
		((MappingStageInfo) bean.getStageInfoSnapshot()).merge(stage);
	}

	private void copyScanToClipboard() {
		try {
			final String scanCommand = createScanCommand();
			Clipboard clipboard = new Clipboard(Display.getDefault());
			clipboard.setContents(new Object[] { scanCommand }, new Transfer[] { TextTransfer.getInstance() });
			clipboard.dispose();
			logger.debug("Copied mapping scan command to clipboard: {}", scanCommand);
		} catch (Exception e) {
			logger.error("Copy to clipboard failed.", e);
			MessageDialog.openError(getShell(), "Error Copying Scan Command",
					"The scan command could not be copied to the clipboard. See the error log for more details.");
		}
	}

	protected void submitScan() {
		final ScanBeanSubmitter submitter = getService(ScanBeanSubmitter.class);
		try {
			ScanBean scanBean = createScanBean();
			submitter.submitScan(scanBean);
		} catch (Exception e) {
			logger.error("Scan submission failed", e);
			MessageDialog.openError(getShell(), "Error Submitting Scan", "The scan could not be submitted. See the error log for more details.");
		}
	}

	/**
	 * Called when this section is shown
	 * <p>
	 * This can be used for example to show controls allowing the user to define parameters specific to this submit
	 * section.
	 */
	protected void onShow() {
		// Nothing to do in this class: subclasses may override
	}

	/**
	 * Called when this section is no longer visible
	 * <p>
	 * This can be used for example to hide the controls made visible by {@link #onShow()}
	 */
	protected void onHide() {
		// Nothing to do in this class: subclasses may override
	}

	/**
	 * Return the composite created by this section
	 *
	 * @return the section composite
	 */
	Composite getComposite() {
		return composite;
	}

	/**
	 * Set the text to be shown on the Submit button<br>
	 * Typically set in Spring configuration
	 *
	 * @param buttonText
	 *            Text to be shown on the button
	 */
	public void setButtonText(String buttonText) {
		this.buttonText = buttonText;
	}

	/**
	 * Gets a user-friendly name for the section
	 *
	 * @return a description of the section
	 */
	String getDescription() {
		return description;
	}

	/**
	 * Set a user-friendly description for the section
	 * <p>
	 * Typically set in Spring and can be used for example in a list to give the user a choice of different Submit
	 * sections to give the user a choice of
	 *
	 * @param description
	 *            a description of this section e.g. "Mapping scan"
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Set the colour of the submit button
	 * <p>
	 * This can for example be used to make it more obvious to the user which type of scan they are about to submit.
	 *
	 * @param buttonColour
	 *            RGB value of the required colour
	 */
	protected void setButtonColour(RGB buttonColour) {
		this.buttonColour = buttonColour;
	}
}
