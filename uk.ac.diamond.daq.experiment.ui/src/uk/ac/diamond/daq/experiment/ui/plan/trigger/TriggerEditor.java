package uk.ac.diamond.daq.experiment.ui.plan.trigger;
import static uk.ac.diamond.daq.experiment.ui.driver.DiadUIUtils.STRETCH;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import uk.ac.diamond.daq.experiment.api.plan.TriggerDescriptor;
import uk.ac.diamond.daq.experiment.api.remote.ExecutionPolicy;
import uk.ac.diamond.daq.experiment.api.remote.SignalSource;
import uk.ac.diamond.daq.experiment.api.ui.EditableWithListWidget;
import uk.ac.diamond.daq.experiment.ui.widget.ElementEditor;

public class TriggerEditor implements ElementEditor {
	
	private TriggerDescriptor model;

	private Composite composite;
	
	private TriggerDetailControl detailControl = new TriggerDetailControl();
	private Composite detailComposite;
	private Text nameText;
	private Combo executable;
	
	private SignalSource source = SignalSource.POSITION;
	private ExecutionPolicy mode = ExecutionPolicy.SINGLE;
	
	private Button sevSourceButton, timeSourceButton, oneShotButton, periodicButton;
	
	@Override
	public void createControl(Composite parent) {
		composite = new Composite(parent, SWT.NONE);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(composite);
		GridLayoutFactory.swtDefaults().applyTo(composite);
		
		//////// NAME ////////
		
		new Label(composite, SWT.NONE).setText("Name");
		nameText = new Text(composite, SWT.BORDER);
		STRETCH.applyTo(nameText);
		
		new Label(composite, SWT.NONE); // space
		
		//////// EXEC ////////
		
		new Label(composite, SWT.NONE).setText("Measurement");
		
		executable = new Combo(composite, SWT.READ_ONLY); // temporary...
		executable.setItems("radiog_5ms", "diffr_5ms");
		
		STRETCH.copy().applyTo(executable);
		
		new Label(composite, SWT.NONE); // space
		
		Composite sourceAndMode = new Composite(composite, SWT.NONE);
		STRETCH.applyTo(sourceAndMode);
		GridLayoutFactory.swtDefaults().numColumns(2).equalWidth(true).spacing(20, SWT.DEFAULT).applyTo(sourceAndMode);
		
		//////// SOURCE ////////
		
		Group sourceGroup = new Group(sourceAndMode, SWT.NONE);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(sourceGroup);
		GridLayoutFactory.swtDefaults().applyTo(sourceGroup);
		sourceGroup.setText("Triggering source");
		
		sevSourceButton = new Button(sourceGroup, SWT.RADIO);
		sevSourceButton.setText("Environment variable");
		sevSourceButton.addListener(SWT.Selection, e -> sourceSwitched(SignalSource.POSITION));
		sevSourceButton.setSelection(true);
		
		timeSourceButton = new Button(sourceGroup, SWT.RADIO);
		timeSourceButton.setText("Time");
		timeSourceButton.addListener(SWT.Selection, e -> sourceSwitched(SignalSource.TIME));
		
		
		//////// MODE ////////
		
		Group modeGroup = new Group(sourceAndMode, SWT.NONE);
		GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(modeGroup);
		GridLayoutFactory.swtDefaults().applyTo(modeGroup);
		modeGroup.setText("Mode"); // TODO need more meaningful label
		
		oneShotButton = new Button(modeGroup, SWT.RADIO);
		oneShotButton.setText("Single");
		oneShotButton.addListener(SWT.Selection, e -> modeSwitched(ExecutionPolicy.SINGLE));
		oneShotButton.setSelection(true);
		
		periodicButton = new Button(modeGroup, SWT.RADIO);
		periodicButton.setText("Periodic");
		periodicButton.addListener(SWT.Selection, e -> modeSwitched(ExecutionPolicy.REPEATING));
		
		
		updateDetailControl();
		
		nameText.setFocus();
	}
	
	private void removeListener(Widget widget, int type) {
		if (widget.isListening(type))
			widget.removeListener(type, widget.getListeners(type)[0]);
	}

	@Override
	public void load(EditableWithListWidget element) {
		model = (TriggerDescriptor) element;
		
		removeListener(nameText, SWT.Modify);
		nameText.setText(model.getName());
		nameText.addListener(SWT.Modify, e -> {
			if (model != null) {
				model.setName(nameText.getText());
			}
		});
		
		removeListener(executable, SWT.Selection);
		
		if (model.getScanName()== null || model.getScanName().isEmpty()) {
			executable.deselectAll();
		} else {
			executable.select(Arrays.asList(executable.getItems()).indexOf(model.getScanName()));
		}
		
		executable.addListener(SWT.Selection, e -> {
			if (model != null) {
				model.setScanName(executable.getText());
			}
		});
		
		source = model.getSignalSource();
		mode = model.getExecutionPolicy();
		
		if (source == SignalSource.POSITION) {
			sevSourceButton.setSelection(true);
			timeSourceButton.setSelection(false);
		} else {
			sevSourceButton.setSelection(false);
			timeSourceButton.setSelection(true);
		}
		
		if (mode == ExecutionPolicy.REPEATING) {
			periodicButton.setSelection(true);
			oneShotButton.setSelection(false);
		} else {
			periodicButton.setSelection(false);
			oneShotButton.setSelection(true);
		}
		
		updateDetailControl();
		nameText.setFocus();
	}

	@Override
	public void clear() {
		removeListener(nameText, SWT.Modify);
		nameText.setText("");
		executable.deselectAll();
		source = SignalSource.TIME;
		mode = ExecutionPolicy.SINGLE;
		updateDetailControl();
		detailControl.getTarget().setText("0");
	}
	
	private void sourceSwitched(SignalSource source) {
		this.source = source;
		if (model!= null) {
			model.setSignalSource(source);
		}
		updateDetailControl();
	}
	
	private void modeSwitched(ExecutionPolicy mode) {
		this.mode = mode;
		if (model != null) {
			model.setExecutionPolicy(mode);
		}
		updateDetailControl();
	}
	
	private void updateDetailControl() {
		if (detailComposite != null) {
			detailComposite.dispose();
			detailComposite = null;
		}
		
		detailComposite = new Composite(composite, SWT.NONE);
		STRETCH.applyTo(detailComposite);
		GridLayoutFactory.swtDefaults().applyTo(detailComposite);

		
		detailControl.update(detailComposite, source, mode, model);

		composite.layout(true);
		
		detailComposite.setFocus();
	}
	
	public void setSevNames(List<String> sevs) {
		detailControl.setSevNames(sevs);
	}

}
