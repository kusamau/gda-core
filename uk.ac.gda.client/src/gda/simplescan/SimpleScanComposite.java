/*-
 * Copyright © 2012 Diamond Light Source Ltd.
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

package gda.simplescan;

import gda.jython.JythonServerFacade;
import gda.rcp.GDAClientActivator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import uk.ac.gda.richbeans.components.FieldComposite;
import uk.ac.gda.richbeans.components.scalebox.ScaleBox;
import uk.ac.gda.richbeans.components.wrappers.ComboWrapper;
import uk.ac.gda.richbeans.event.ValueEvent;
import uk.ac.gda.richbeans.event.ValueListener;

/**
 *
 */
public final class SimpleScanComposite extends Composite {

	private ComboWrapper scannableName;
	private ScaleBox fromPos;
	private ScaleBox toPos;
	private ScaleBox stepSize;
	private ScaleBox acqTime;
	private Group grpScannable;
	private Composite composite;
	private Label lblScannable;
	private Label lblFrom;
	private Label lblTo;
	private Label lblStep;
	private SimpleScan bean;
	private Composite detComposite;
	private Label lblAcqTime_1;
	protected TableViewer viewer;
	private Image CHECKED;
	private Image UNCHECKED;
	private DescriptionEditingSupport des;
	private EnabledEditingSupport detEnabled;
	private List<ScannableManagerBean> scannables;

	public SimpleScanComposite(Composite parent, int style, Object editingBean) {
		super(parent, style);

		CHECKED = GDAClientActivator.getImageDescriptor("icons/checked.gif").createImage();
		UNCHECKED = GDAClientActivator.getImageDescriptor("icons/unchecked.gif").createImage();

		bean = (SimpleScan) editingBean;

		grpScannable = new Group(this, SWT.NONE);
		GridData gd_grpScannable = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_grpScannable.widthHint = 597;
		grpScannable.setLayoutData(gd_grpScannable);
		grpScannable.setText("Scan");
		grpScannable.setLayout(new GridLayout(1, false));

		composite = new Composite(grpScannable, SWT.NONE);
		GridData gd_composite = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_composite.widthHint = 580;
		composite.setLayoutData(gd_composite);
		composite.setLayout(new GridLayout(9, false));

		lblScannable = new Label(composite, SWT.NONE);
		lblScannable.setText("Scannable");

		createScannables(composite);

		updateScannables();

		lblFrom = new Label(composite, SWT.NONE);
		lblFrom.setText("From");

		setLayout(new GridLayout(1, false));
		this.fromPos = new ScaleBox(composite, SWT.NONE);
		fromPos.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		lblTo = new Label(composite, SWT.NONE);
		lblTo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblTo.setText("To");
		this.toPos = new ScaleBox(composite, SWT.NONE);
		toPos.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		try {
			setMotorLimits(bean.getScannableName(), fromPos);
			setMotorLimits(bean.getScannableName(), toPos);
		} catch (Exception e1) {
		}

		lblStep = new Label(composite, SWT.NONE);
		lblStep.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblStep.setText("Step");
		this.stepSize = new ScaleBox(composite, SWT.NONE);
		stepSize.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

		new Label(composite, SWT.NONE);

		detComposite = new Composite(grpScannable, SWT.NONE);
		GridLayout gl_detComposite = new GridLayout(1, false);
		gl_detComposite.marginHeight = 0;
		gl_detComposite.verticalSpacing = 0;
		gl_detComposite.marginWidth = 0;
		gl_detComposite.horizontalSpacing = 0;
		detComposite.setLayout(gl_detComposite);
		GridData gd_detComposite = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_detComposite.widthHint = 587;
		detComposite.setLayoutData(gd_detComposite);

		createDetectors(detComposite);

		updateDetectors();

		createScanButton(this);

		this.fromPos.setValue(bean.getFromPos());
		this.toPos.setValue(bean.getToPos());
		this.stepSize.setValue(bean.getStepSize());
		this.acqTime.setValue(bean.getAcqTime());

		scannables = bean.getScannables();
		boolean found = false;
		for (int i = 0; i < scannables.size(); i++) {
			if (scannables.get(i).getScannableName().equals(bean.getScannableName())) {
				this.scannableName.select(i + 1);
				found = true;
			}
		}
		if (!found)
			this.scannableName.select(0);
	}

	public void updateScannables() {
		List<String> names = new ArrayList<String>(bean.getScannables().size());
		String[] comboNames = new String[bean.getScannables().size() + 1];
		comboNames[0] = "";
		for (int i = 1; i < bean.getScannables().size() + 1; i++) {
			names.add(bean.getScannables().get(i - 1).getScannableName());
			comboNames[i] = bean.getScannables().get(i - 1).getScannableName();
		}
		scannableName.setItems(comboNames);

		List<ScannableManagerBean> scannables = bean.getScannables();
		boolean found = false;

		for (int i = 0; i < scannables.size(); i++) {
			if (scannables.get(i).getScannableName().equals(bean.getScannableName())) {
				scannableName.select(i + 1);
				found = true;
			}
		}
		if (!found)
			scannableName.select(0);
	}

	public void updateDetectors() {
		List<String> names = new ArrayList<String>(bean.getDetectors().size());
		String[] comboNames = new String[bean.getDetectors().size()];
		for (int i = 0; i < bean.getDetectors().size(); i++) {
			names.add(bean.getDetectors().get(i).getDetectorName());
			comboNames[i] = bean.getDetectors().get(i).getDetectorName();
		}
		if (names.size() > 0) {
			viewer.setInput(names);
			viewer.refresh();
		}
	}

	public void createScanButton(Composite comp) {
		Button scan = new Button(comp, SWT.NONE);
		GridData gd_scan = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_scan.widthHint = 92;
		scan.setLayoutData(gd_scan);
		scan.setText("Scan");
		scan.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				performScan();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});
	}

	private void performScan() {

		String scannable_name = scannableName.getItem(scannableName.getSelectionIndex());
		double from = fromPos.getNumericValue();
		double to = toPos.getNumericValue();
		double step = stepSize.getNumericValue();
		Double acq = acqTime.getNumericValue();

		if (bean.getDetectors().size() == 0) {
			String command = "scan " + scannable_name + " " + from + " " + to + " " + step;
			JythonServerFacade.getInstance().runCommand(command);
		} else {
			List<DetectorManagerBean> detectors = bean.getDetectors();
			String detList = "";
			for (int i = 0; i < detectors.size(); i++) {
				if (detectors.get(i).isEnabled())
					detList += detectors.get(i).getDetectorName() + " ";
			}
			if (!acq.isNaN() && !acqTime.getValue().toString().equals(""))
				detList += acq;
			String command = "scan " + scannable_name + " " + from + " " + to + " " + step + " " + detList;
			JythonServerFacade.getInstance().runCommand(command);
		}
	}

	public void setMotorLimits(String motorName, ScaleBox box) throws Exception {
		String lowerLimit = JythonServerFacade.getInstance().evaluateCommand(motorName + ".getLowerInnerLimit()");
		String upperLimit = JythonServerFacade.getInstance().evaluateCommand(motorName + ".getUpperInnerLimit()");
		if (!lowerLimit.equals("None"))
			box.setMinimum(Double.parseDouble(lowerLimit));
		else
			box.setMinimum(-99999);
		if (!upperLimit.equals("None"))
			box.setMaximum(Double.parseDouble(upperLimit));
		else
			box.setMaximum(99999);
	}

	public void createScannables(Composite comp) {
		this.scannableName = new ComboWrapper(comp, SWT.NONE);
		scannableName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		scannableName.addValueListener(new ValueListener() {
			@Override
			public void valueChangePerformed(ValueEvent e) {
				try {
					setMotorLimits(bean.getScannableName(), fromPos);
					setMotorLimits(bean.getScannableName(), toPos);
				} catch (Exception e1) {
				}
			}

			@Override
			public String getValueListenerName() {
				return null;
			}
		});
	}

	public void createDetectors(Composite comp) {

		Composite composite_2 = new Composite(comp, SWT.NONE);
		GridLayout gl_composite_2 = new GridLayout(2, false);
		gl_composite_2.marginWidth = 0;
		gl_composite_2.marginHeight = 0;
		gl_composite_2.verticalSpacing = 0;
		composite_2.setLayout(gl_composite_2);

		Composite composite_3 = new Composite(composite_2, SWT.NONE);
		composite_3.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		GridLayout gl_composite_3 = new GridLayout(1, false);
		gl_composite_3.verticalSpacing = 0;
		composite_3.setLayout(gl_composite_3);

		Composite composite_4 = new Composite(composite_2, SWT.NONE);
		GridLayout gl_composite_4 = new GridLayout(1, false);
		gl_composite_4.verticalSpacing = 0;
		composite_4.setLayout(gl_composite_4);

		lblAcqTime_1 = new Label(composite_3, SWT.CENTER);
		lblAcqTime_1.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false, 1, 1));
		lblAcqTime_1.setText("Acq Time");

		acqTime = new ScaleBox(composite_3, SWT.NONE);
		GridData gd_acqTime = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_acqTime.widthHint = 65;
		acqTime.setLayoutData(gd_acqTime);
		new Label(acqTime, SWT.NONE);

		viewer = new TableViewer(composite_4, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);

		final TableViewerColumn enabledCol = new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn enabledColumn = enabledCol.getColumn();
		enabledColumn.setText("Enabled");
		enabledColumn.setWidth(65);
		enabledColumn.setResizable(true);
		enabledColumn.setMoveable(true);

		detEnabled = new EnabledEditingSupport(viewer, bean);

		enabledCol.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				bean = detEnabled.getBean();
				for (int i = 0; i < bean.getDetectors().size(); i++) {
					if (bean.getDetectors().get(i).getDetectorName().equals(cell.getItem().getData().toString())) {
						if (bean.getDetectors().get(i).isEnabled())
							cell.setImage(CHECKED);
						else
							cell.setImage(UNCHECKED);
					}
				}

			}
		});
		enabledCol.setEditingSupport(detEnabled);

		final TableViewerColumn nameCol = new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn nameColumn = nameCol.getColumn();
		nameColumn.setText("Detector Name");
		nameColumn.setWidth(150);
		nameColumn.setResizable(true);
		nameColumn.setMoveable(true);
		nameCol.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				cell.setText(cell.getItem().getData().toString());
			}
		});

		final TableViewerColumn descriptionCol = new TableViewerColumn(viewer, SWT.NONE);
		final TableColumn descriptionColumn = descriptionCol.getColumn();
		descriptionColumn.setText("Detector Description");
		descriptionColumn.setWidth(150);
		descriptionColumn.setResizable(true);
		descriptionColumn.setMoveable(true);
		des = new DescriptionEditingSupport(viewer, bean);

		descriptionCol.setLabelProvider(new CellLabelProvider() {
			@Override
			public void update(ViewerCell cell) {
				bean = des.getBean();
				for (int i = 0; i < bean.getDetectors().size(); i++) {
					if (bean.getDetectors().get(i).getDetectorName().equals(cell.getItem().getData().toString())) {
						cell.setText(bean.getDetectors().get(i).getDetectorDescription());
					}
				}
			}
		});
		descriptionCol.setEditingSupport(des);

		final Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		viewer.setContentProvider(new ArrayContentProvider());
		GridData gridData = new GridData();
		gridData.grabExcessHorizontalSpace = false;
		gridData.heightHint = 200;
		gridData.widthHint = 465;
		viewer.getControl().setLayoutData(gridData);
	}

	public SimpleScan getBean() {
		return bean;
	}

	public ScaleBox getFromPos() {
		return fromPos;
	}

	public ScaleBox getToPos() {
		return toPos;
	}

	public ScaleBox getStepSize() {
		return stepSize;
	}

	public ScaleBox getAcqTime() {
		return acqTime;
	}

	public FieldComposite getScannableName() {
		return scannableName;
	}
}