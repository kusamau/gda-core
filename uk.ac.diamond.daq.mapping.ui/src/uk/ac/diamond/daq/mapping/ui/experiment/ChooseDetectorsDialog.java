/*-
 * Copyright © 2017 Diamond Light Source Ltd.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.richbeans.widgets.shuffle.ShuffleConfiguration;
import org.eclipse.richbeans.widgets.shuffle.ShuffleViewer;
import org.eclipse.scanning.api.device.models.IDetectorModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import uk.ac.diamond.daq.mapping.api.IScanModelWrapper;
import uk.ac.diamond.daq.mapping.impl.DetectorModelWrapper;

/**
 * Lets user choose a subset from their configured detectors.
 */
public class ChooseDetectorsDialog extends Dialog {

	private List<IScanModelWrapper<IDetectorModel>> originalList;
	private List<IScanModelWrapper<IDetectorModel>> selectedList;
	private ShuffleConfiguration<String> data;
	private Map<String, IScanModelWrapper<IDetectorModel>> labelMap = new HashMap<>();

	/**
	 * @param parentShell
	 * @param availableDetectors - all detectors configured in Spring
	 * @param selectedDetectors - previously selected detectors; can be null
	 */
	protected ChooseDetectorsDialog(Shell parentShell, List<IScanModelWrapper<IDetectorModel>> availableDetectors, List<IScanModelWrapper<IDetectorModel>> selectedDetectors) {
		super(parentShell);
		setShellStyle(SWT.RESIZE | SWT.APPLICATION_MODAL);
		originalList = availableDetectors;
		selectedList = selectedDetectors == null ? new ArrayList<>() : selectedDetectors;
		originalList.forEach(model -> labelMap.put(model.getName(), model));
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Choose from available detectors");
	}

	@Override
	public Control createDialogArea(Composite parent) {
		parent.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
		parent.setBackgroundMode(SWT.INHERIT_FORCE);

		data = new ShuffleConfiguration<>();
		data.setFromLabel("Available");
		data.setToLabel("Selected");

		List<String> selectedDetectors = selectedList.stream()
				.map(IScanModelWrapper<IDetectorModel>::getName)
				.collect(Collectors.toList());

		List<String> availableDetectors = labelMap.keySet().stream()
				.filter(model -> !selectedDetectors.contains(model))
				.collect(Collectors.toList());

		ShuffleViewer<String> viewer = new ShuffleViewer<>(data);
		Control shuffleComposite = viewer.createPartControl(parent);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(shuffleComposite);

		data.setFromList(availableDetectors);
		data.setToList(selectedDetectors);

		return shuffleComposite;
	}

	@Override
	public void okPressed() {
		selectedList = new ArrayList<>();
		data.getToList().forEach(detector -> selectedList.add(labelMap.get(detector)));

		// To avoid unexpected behaviour, do not include in scan any detector that isn't selected here
		data.getFromList().forEach(detector -> ((DetectorModelWrapper) labelMap.get(detector)).setIncludeInScan(false));
		super.okPressed();
	}

	public List<IScanModelWrapper<IDetectorModel>> getSelectedDetectors() {
		return selectedList;
	}

}
