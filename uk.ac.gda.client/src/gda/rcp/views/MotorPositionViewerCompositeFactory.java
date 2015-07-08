/*-
 * Copyright © 2011 Diamond Light Source Ltd.
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

package gda.rcp.views;

import gda.device.Scannable;
import gda.device.monitor.DummyMonitor;
import gda.device.motor.DummyMotor;
import gda.device.scannable.ScannableMotor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.springframework.beans.factory.InitializingBean;

import swing2swt.layout.BorderLayout;
import uk.ac.gda.ui.utils.SWTUtils;

public class MotorPositionViewerCompositeFactory implements CompositeFactory, InitializingBean {
	private Scannable scannable;
	private Boolean layoutHoriz=true;
	private String label=null;
	private Integer decimalPlaces = null;
	private static Boolean restoreValueWhenFocusLost;
	private Boolean hideLabel = true;

	public Scannable getScannable() {
		return scannable;
	}


	public void setScannable(Scannable scannable) {
		this.scannable = scannable;
	}


	public Boolean getLayoutHoriz() {
		return layoutHoriz;
	}


	public void setLayoutHoriz(Boolean layoutHoriz) {
		this.layoutHoriz = layoutHoriz;
	}


	public String getLabel() {
		return label;
	}


	public void setLabel(String label) {
		this.label = label;
	}

	public void setDecimalPlaces(Integer dp) {
		decimalPlaces = dp;
	}

	public Integer getDecimalPlaces() {
		return decimalPlaces;
	}

	public static Composite createComposite(Composite parent, int style, Scannable scannable, Boolean layoutHoriz,
			String label, Integer decimalPlaces){
		return new MotorPositionViewerComposite(parent, style, scannable, layoutHoriz, label, decimalPlaces, null, getRestoreValueWhenFocusLost(), false);
	}
	@Override
	public Composite createComposite(Composite parent, int style) {
		return new MotorPositionViewerComposite(parent, style, scannable, layoutHoriz, label,
				decimalPlaces, commandFormat, getRestoreValueWhenFocusLost(), hideLabel);
	}

	private String commandFormat;

	public void setCommandFormat(String commandFormat) {
		this.commandFormat = commandFormat;
	}

	public static Boolean getRestoreValueWhenFocusLost() {
		return restoreValueWhenFocusLost;
	}

	public void setHideLabel(Boolean hideLabel) {
		this.hideLabel = hideLabel;
	}

	public void setRestoreValueWhenFocusLost(Boolean restoreValueWhenFocusLost) {
		MotorPositionViewerCompositeFactory.restoreValueWhenFocusLost = restoreValueWhenFocusLost;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (scannable == null)
			throw new IllegalArgumentException("scannable is null");

	}

	public static void main(String... args) throws Exception {
		DummyMotor dummyMotor = new DummyMotor();
		dummyMotor.setName("dummyMotor");
		dummyMotor.configure();
		ScannableMotor scannableMotor = new ScannableMotor();
		scannableMotor.setMotor(dummyMotor);
		scannableMotor.setName("scannableMotor");
		scannableMotor.setLowerGdaLimits(0.);
		scannableMotor.setInitialUserUnits("mm");
		scannableMotor.configure();

		DummyMonitor dummy = new DummyMonitor();
		dummy.setName("dummy");
		dummy.configure();
		MotorPositionViewerCompositeFactory motorPositionViewFactory = new MotorPositionViewerCompositeFactory();
		motorPositionViewFactory.setScannable(scannableMotor);
		motorPositionViewFactory.afterPropertiesSet();

		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setLayout(new BorderLayout());

		final MotorPositionViewerComposite comp = new MotorPositionViewerComposite(shell, SWT.NONE, scannableMotor, true, "North", null, null, getRestoreValueWhenFocusLost(), false);
		comp.setLayoutData(BorderLayout.NORTH);
		comp.setVisible(true);
		final MotorPositionViewerComposite comp1 = new MotorPositionViewerComposite(shell, SWT.NONE, scannableMotor, false, null, null, null, getRestoreValueWhenFocusLost(), false);
		comp1.setLayoutData(BorderLayout.SOUTH);
		comp1.setVisible(true);
		shell.pack();
		shell.setSize(400, 400);
		SWTUtils.showCenteredShell(shell);
	}
}