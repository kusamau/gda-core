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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.scanning.api.device.IRunnableDeviceService;
import org.eclipse.scanning.api.event.EventException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import uk.ac.diamond.daq.mapping.api.IMappingExperimentBean;

public abstract class AbstractMappingSection {

	/**
	 * Number of horizontal dialog units per character, value <code>4</code>.
	 */
	private static final int HORIZONTAL_DIALOG_UNIT_PER_CHAR = 4;

	private static FontMetrics fontMetrics = null;

	private MappingExperimentView mappingView;

	protected DataBindingContext dataBindingContext;

	protected void initialize(MappingExperimentView mappingView) {
		this.mappingView = mappingView;
	}

	protected Shell getShell() {
		return mappingView.getShell();
	}

	protected <S> S getService(Class<S> serviceClass) {
		return mappingView.getEclipseContext().get(serviceClass);
	}

	protected IRunnableDeviceService getRunnableDeviceService() throws EventException {
		return mappingView.getRunnableDeviceService();
	}

	protected IMappingExperimentBean getMappingBean() {
		return mappingView.getBean();
	}

	protected MappingExperimentView getMappingView() {
		return mappingView;
	}

	protected void relayoutMappingView() {
		mappingView.relayout();
		mappingView.recalculateMinimumSize();
	}

	protected IEclipseContext getEclipseContext() {
		return mappingView.getEclipseContext();
	}

	public boolean shouldShow() {
		return true;
	}

	public boolean createSeparator() {
		return true;
	}

	public abstract void createControls(Composite parent);

	protected void setFocus() {
		// do nothing, subclasses may override
	}

	protected void updateStatusLabel() {
		mappingView.getStatusPanel().updateStatusLabel();
	}

	protected void setStatusMessage(String message) {
		mappingView.getStatusPanel().setMessage(message);
	}

	protected void setButtonLayoutData(Button button) {
		// copied from org.eclipse.jface.dialogs.Dialog. Gives buttons a standard minimum width
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		int widthHint = convertHorizontalDLUsToPixels(IDialogConstants.BUTTON_WIDTH);
		Point minSize = button.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		data.widthHint = Math.max(widthHint, minSize.x);
		button.setLayoutData(data);
	}

	public int convertHorizontalDLUsToPixels(int dlus) {
		if (fontMetrics == null) {
			GC gc = new GC(mappingView.getMainComposite());
			gc.setFont(JFaceResources.getDialogFont());
			fontMetrics = gc.getFontMetrics();
			gc.dispose();
		}

		// round to the nearest pixel
		return (fontMetrics.getAverageCharWidth() * dlus + HORIZONTAL_DIALOG_UNIT_PER_CHAR / 2)
				/ HORIZONTAL_DIALOG_UNIT_PER_CHAR;
	}

	/**
	 * Updates this section based on the mapping bean.
	 */
	protected void updateControls() {
		// Default implementation does nothing. Subclasses may override.
	}

	/**
	 * Default implementation does nothing since most data is saved through the mapping bean
	 */
	protected void saveState(@SuppressWarnings("unused") Map<String, String> persistedState) {

	}

	/**
	 * Default implementation does nothing since most data is loaded through the mapping bean
	 */
	protected void loadState(@SuppressWarnings("unused") Map<String, String> persistedState) {

	}

	/**
	 * Remove all existing bindings in {@link AbstractMappingSection#dataBindingContext}
	 */
	protected void removeOldBindings() {
		if (dataBindingContext == null) {
			return;
		}

		// copy the bindings to prevent concurrent modification exception
		@SuppressWarnings("unchecked")
		final List<Binding> bindings = new ArrayList<>(dataBindingContext.getBindings());
		for (Binding binding : bindings) {
			dataBindingContext.removeBinding(binding);
			binding.dispose();
		}
	}
}
