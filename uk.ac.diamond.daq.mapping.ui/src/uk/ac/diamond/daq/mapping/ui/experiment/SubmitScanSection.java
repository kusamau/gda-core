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

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.scanning.api.event.scan.ScanBean;
import org.eclipse.scanning.api.event.scan.ScanRequest;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.daq.mapping.api.IMappingExperimentBean;

/**
 * A section containing the button to launch a scan.
 */
public class SubmitScanSection extends AbstractMappingSection {

	private static final Logger logger = LoggerFactory.getLogger(SubmitScanSection.class);

	@Override
	public void createControls(Composite parent) {
		Composite submitScanComposite = new Composite(parent, SWT.NONE);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.BOTTOM).applyTo(submitScanComposite);
		GridLayoutFactory.swtDefaults().numColumns(4).applyTo(submitScanComposite);

		Button scanButton = new Button(submitScanComposite, SWT.NONE);
		scanButton.setText("Queue Scan");

		final ScanBeanSubmitter submitter = getService(ScanBeanSubmitter.class);
		scanButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				try {
					ScanBean scanBean = createScanBean();
					submitter.submitScan(scanBean);
				} catch (Exception e) {
					logger.error("Scan submission failed", e);
					MessageDialog.openError(getShell(), "Error Submitting Scan", "The scan could not be submitted. See the error log for more details.");
				}
			}
		});
	}

	@Override
	public boolean createSeparator() {
		return false;
	}

	private ScanBean createScanBean() {
		IMappingExperimentBean mappingBean = getMappingBean();
		ScanBean scanBean = new ScanBean();
		String sampleName = mappingBean.getSampleMetadata().getSampleName();
		if (sampleName == null || sampleName.length() == 0) {
			sampleName = "unknown sample";
		}
		String pathName = mappingBean.getScanDefinition().getMappingScanRegion().getScanPath().getName();
		scanBean.setName(String.format("%s - %s Scan", sampleName, pathName));
		scanBean.setProperty(MappingExperimentView.PROPERTY_NAME_MAPPING_SCAN, Boolean.TRUE.toString());

		final ScanRequestConverter converter = getService(ScanRequestConverter.class);
		ScanRequest<IROI> scanRequest = converter.convertToScanRequest(mappingBean);
		scanBean.setScanRequest(scanRequest);
		return scanBean;
	}

}
