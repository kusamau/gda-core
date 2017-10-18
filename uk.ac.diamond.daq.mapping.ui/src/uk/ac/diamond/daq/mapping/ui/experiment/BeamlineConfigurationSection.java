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

import java.net.URI;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.window.Window;
import org.eclipse.scanning.api.device.IScannableDeviceService;
import org.eclipse.scanning.api.event.IEventService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.configuration.properties.LocalProperties;
import uk.ac.diamond.daq.mapping.api.IMappingExperimentBean;

/**
 * A section to edit the beamline configuration, i.e. the positions that certain
 * scannables should be set to before a scan in run.
 */
public class BeamlineConfigurationSection extends AbstractMappingSection {

	private static final Logger LOGGER = LoggerFactory.getLogger(BeamlineConfigurationSection.class);
	private static final int MAX_TXT_LINES = 1;
	private Text summaryText;

	@Override
	public void createControls(Composite parent) {
		Composite beamlineConfigComposite = new Composite(parent, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(3).equalWidth(false).applyTo(beamlineConfigComposite);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(beamlineConfigComposite);

		(new Label(beamlineConfigComposite, SWT.NONE)).setText("Configure Beamline");

		Composite configSummaryComposite = new Composite(beamlineConfigComposite, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(2).equalWidth(false).applyTo(configSummaryComposite);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(configSummaryComposite);
		summaryText = new Text(configSummaryComposite, SWT.MULTI | SWT.READ_ONLY);
		summaryText.setLayoutData(new GridData(SWT.FILL,SWT.FILL,true,true));
		summaryText.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));
		summaryText.setVisible(false);

		Button editBeamlineConfigButton = new Button(beamlineConfigComposite, SWT.PUSH);
		editBeamlineConfigButton.setImage(MappingExperimentUtils.getImage("icons/pencil.png"));
		editBeamlineConfigButton.addListener(SWT.Selection, event -> editBeamlineConfiguration());
		updateConfiguredScannableSummary();
	}

	private void editBeamlineConfiguration() {
		try {
			IMappingExperimentBean mappingBean = getMappingBean();
			EditBeamlineConfigurationDialog dialog = new EditBeamlineConfigurationDialog(getShell(),
					getScannableDeviceService());
			dialog.setInitialBeamlineConfiguration(mappingBean.getBeamlineConfiguration());
			dialog.create();
			if (dialog.open() == Window.OK) {
				Map<String, Object> configuredScannables = dialog.getModifiedBeamlineConfiguration();
				mappingBean.setBeamlineConfiguration(configuredScannables);
				updateConfiguredScannableSummary();
			}
		} catch (Exception e) {
			LOGGER.error("Could not edit beamline configuration", e);
			MessageDialog.openError(getShell(), "Beamline Configuration", "Could not edit beamline configuration: " + e.getMessage());
		}
	}

	private IScannableDeviceService getScannableDeviceService() throws Exception {
		IEventService eventService = getService(IEventService.class);
		URI jmsURI = new URI(LocalProperties.getActiveMQBrokerURI());
		return  eventService.createRemoteService(jmsURI, IScannableDeviceService.class);
	}

	private void updateConfiguredScannableSummary() {
		Map<String, Object> configured = getMappingBean().getBeamlineConfiguration();
		if (configured == null) {
			return; // Will be null on startup in a new workspace
		}

		List<String> txt = configured.entrySet().stream()
				.map(entry->entry.getKey() + " = " + formatScannablePosition(entry.getValue()))
				.collect(Collectors.toList());

		summaryText.setToolTipText(txt.stream().collect(Collectors.joining("\n")));

		if (txt.size() > MAX_TXT_LINES) {
			txt = txt.subList(0, MAX_TXT_LINES);
			txt.set(MAX_TXT_LINES - 1, txt.get(MAX_TXT_LINES - 1)+" ...");
		}

		summaryText.setText(txt.stream().collect(Collectors.joining("\n")));
		summaryText.setVisible(!txt.isEmpty());
	}

	private String formatScannablePosition(Object value) {
		DecimalFormat fourDecimalPlaces = new DecimalFormat("##########0.0###");
		return value instanceof Number ? fourDecimalPlaces.format(value) : value.toString();
	}

	@Override
	protected void updateControls() {
		updateConfiguredScannableSummary();
	}
}
