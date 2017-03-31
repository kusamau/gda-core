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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.PojoProperties;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.jface.databinding.fieldassist.ControlDecorationSupport;
import org.eclipse.jface.databinding.swt.WidgetProperties;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.scanning.api.points.models.ArrayModel;
import org.eclipse.scanning.api.points.models.IScanPathModel;
import org.eclipse.scanning.api.points.models.StepModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import uk.ac.diamond.daq.mapping.api.IScanPathModelWrapper;

/**
 * A section for configuring the outer scannables or a scan, e.g. temperature.
 */
class OuterScannablesSection extends AbstractMappingSection {

	private static class ScanPathToStringConverter extends Converter {

		public ScanPathToStringConverter() {
			super(IScanPathModel.class, String.class);
		}

		@Override
		public Object convert(Object fromObject) {
			if (fromObject == null) {
				return ""; // this is the case when the outer scannable is not specified
			} else if (fromObject instanceof StepModel) {
				return convertStepModel((StepModel) fromObject);
			} else if (fromObject instanceof ArrayModel) {
				return convertArrayModel((ArrayModel) fromObject);
			} else {
				// We only expect path model types that can be created from this GUI
				throw new IllegalArgumentException("Unknown model type: " + fromObject.getClass());
			}
		}

		private Object convertStepModel(StepModel stepModel) {
			StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append(doubleToString(stepModel.getStart()));
			stringBuilder.append(' ');
			stringBuilder.append(doubleToString(stepModel.getStop()));
			stringBuilder.append(' ');
			stringBuilder.append(doubleToString(stepModel.getStep()));

			return stringBuilder.toString();
		}

		private Object convertArrayModel(IScanPathModel scanPath) {
			ArrayModel arrayModel = (ArrayModel) scanPath;
			double[] positions = arrayModel.getPositions();
			StringBuilder stringBuilder = new StringBuilder();
			for (int i = 0; i < positions.length; i++) {
				stringBuilder.append(doubleToString(positions[i]));
				if (i < positions.length - 1) stringBuilder.append(",");
			}
			return stringBuilder.toString();
		}

		private String doubleToString(double doubleVal) {
			String stringVal = Double.toString(doubleVal);
			if (stringVal.endsWith(".0")) {
				return stringVal.substring(0, stringVal.length() - 2);
			}
			return stringVal;
		}
	}

	/**
	 * Converter for converting a string to a path model. The string specifies the path of the
	 * outer scannable. It can either be:
	 * <ul>
	 *   <li>A comma separated list of points, i.e. pos1,pos2,pos3,pos4,...</li>
	 *   <li>One or more step ranges separated by spaces, i.e. start stop step
	 * </ul>
	 * <p>If the string contains a comma, it is interpreted a sequence of points, otherwise
	 * as one or more ranges.
	 */
	private static final class StringToScanPathConverter extends Converter {
		private final String scannableName;

		private StringToScanPathConverter(String scannableName) {
			super(String.class, IScanPathModel.class);
			this.scannableName = scannableName;
		}

		@Override
		public Object convert(Object fromObject) {
			final String text = (String) fromObject;
			if (text.isEmpty()) return null;
			try {
				if (text.contains(",")) {
					return convertStringToArrayModel(text);
				} else {
					return convertStringToStepModel(text);
				}
			} catch (Exception e) {
				return null;
			}
		}

		private Object convertStringToStepModel(String text) {
			String[] startStopStep= text.split(" ");
			if (startStopStep.length == 3) {
				StepModel stepModel = new StepModel();
				stepModel.setName(scannableName);
				stepModel.setStart(Double.parseDouble(startStopStep[0]));
				stepModel.setStop(Double.parseDouble(startStopStep[1]));
				stepModel.setStep(Double.parseDouble(startStopStep[2]));
				return stepModel;
			}
			return null;
		}

		private Object convertStringToArrayModel(String text) {
			String[] strings = text.split(",");
			double[] positions = new double[strings.length];
			for (int index = 0; index < strings.length; index++) {
				positions[index] = Double.parseDouble(strings[index]);
			}
			ArrayModel arrayModel = new ArrayModel();
			arrayModel.setName(scannableName);
			arrayModel.setPositions(positions);
			return arrayModel;
		}
	}

	private DataBindingContext dataBindingContext;

	private Map<String, Binding> axisBindings;

	@Override
	public boolean shouldShow() {
		List<IScanPathModelWrapper> outerScannables = getMappingBean().getScanDefinition().getOuterScannables();
		return outerScannables != null && !outerScannables.isEmpty();
	}

	@Override
	public void createControls(Composite parent) {
		List<IScanPathModelWrapper> outerScannables = getMappingBean().getScanDefinition().getOuterScannables();
		Composite otherScanAxesComposite = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(otherScanAxesComposite);
		final int axesColumns = 2;
		GridLayoutFactory.swtDefaults().numColumns(axesColumns).spacing(8, 5).applyTo(otherScanAxesComposite);
		Label otherScanAxesLabel = new Label(otherScanAxesComposite, SWT.NONE);
		otherScanAxesLabel.setText("Other Scan Axes");
		GridDataFactory.fillDefaults().span(axesColumns, 1).applyTo(otherScanAxesLabel);

		dataBindingContext = new DataBindingContext();
		axisBindings = new HashMap<>();
		for (IScanPathModelWrapper scannableAxisParameters : outerScannables) {
			Button checkBox = new Button(otherScanAxesComposite, SWT.CHECK);
			checkBox.setText(scannableAxisParameters.getName());
			IObservableValue checkBoxValue = WidgetProperties.selection().observe(checkBox);
			IObservableValue activeValue = PojoProperties.value("includeInScan").observe(scannableAxisParameters);
			dataBindingContext.bindValue(checkBoxValue, activeValue);

			// FIXME make a proper widget for this?
			Text axisText = new Text(otherScanAxesComposite, SWT.BORDER);
			axisText.setToolTipText("<start stop step>");
			axisText.setToolTipText("<start stop step> or <pos1,pos2,pos3,pos4...>");
			GridDataFactory.fillDefaults().grab(true, false).applyTo(axisText);
			IObservableValue axisTextValue = WidgetProperties.text(SWT.Modify).observe(axisText);
			bindScanPathModelToTextField(scannableAxisParameters, axisTextValue);
		}
	}

	private void bindScanPathModelToTextField(IScanPathModelWrapper scannableAxisParameters,
			IObservableValue axisTextValue) {
		final String scannableName = scannableAxisParameters.getName();
		IObservableValue axisValue = PojoProperties.value("model").observe(scannableAxisParameters);

		UpdateValueStrategy axisTextToModelStrategy = new UpdateValueStrategy();
		axisTextToModelStrategy.setConverter(new StringToScanPathConverter(scannableName));
		axisTextToModelStrategy.setBeforeSetValidator(value -> {
			if (value instanceof IScanPathModel) {
				return ValidationStatus.ok();
			}
			String message = "Text is incorrectly formatted";
			if (scannableAxisParameters.isIncludeInScan()) {
				return ValidationStatus.error(message);
			} else {
				return ValidationStatus.warning(message);
			}
		});

		UpdateValueStrategy modelToAxisTextStrategy = new UpdateValueStrategy();
		modelToAxisTextStrategy.setConverter(new ScanPathToStringConverter());

		Binding axisBinding = dataBindingContext.bindValue(axisTextValue, axisValue,
				axisTextToModelStrategy, modelToAxisTextStrategy);
		ControlDecorationSupport.create(axisBinding, SWT.LEFT | SWT.TOP);
		axisBindings.put(scannableName, axisBinding);
	}

	@Override
	public void updateControls() {
		// update the bindings for exposure time as we may have new models
		for (IScanPathModelWrapper scannableAxisParameters : getMappingBean().getScanDefinition().getOuterScannables()) {
			// remove the binding between the text field and old model
			Binding oldBinding = axisBindings.get(scannableAxisParameters.getName());
			IObservableValue axisTextValue = (IObservableValue) oldBinding.getTarget();
			dataBindingContext.removeBinding(oldBinding);
			oldBinding.dispose();

			// create a new binding between
			bindScanPathModelToTextField(scannableAxisParameters, axisTextValue);
		}

		dataBindingContext.updateTargets();
	}

}
