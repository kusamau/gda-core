package uk.ac.gda.client.closeactions;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import uk.ac.gda.client.closeactions.ClientCloseOption;

/**
 *  Wrapped by UserOptionsOnCloseDialog
 *
 *  Creates a popup menu which asks why the user is closing the client, then executes any required actions. 
 *  It encourages feedback by asking users to explain why they're restarting the client (maybe something
 *  is running slowly, or hanging, or has gotten stuck and we might be able to fix it)
 *  Also reminds users to call the EHC when done!
 *
 *  It is currently called from preShutdown, which runs whenever the client is closed
 */
public class UserOptionsMenuOnClose extends Composite {

	public final int niceWidth;

	private String restartReason = "";
	private ClientCloseOption selectedOption = ClientCloseOption.TEMP_ABSENCE;

	public UserOptionsMenuOnClose(Composite parent, int style, int niceWidth) {
		super(parent, style);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(this);
		this.niceWidth = niceWidth;
		createContents();
	}

	private void createContents() {
		GridLayoutFactory.fillDefaults().margins(5, 5).applyTo(this);

		// radial button group
		Composite selectionGroup = new Composite(this, SWT.NONE);
		GridLayoutFactory.fillDefaults().applyTo(selectionGroup);
		GridDataFactory.swtDefaults().hint(niceWidth, SWT.DEFAULT).applyTo(selectionGroup);

		Button option1 = optionButton(selectionGroup, "I'm finished for now - but I or a colleague will be back soon (no action)",
				niceWidth);
		option1.setSelection(true);
		option1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedOption = ClientCloseOption.TEMP_ABSENCE;
			}
		});

		Button option2 = optionButton(selectionGroup, "Need to restart the client (Please tell us why)",
				niceWidth);
		final Text option2feedback = reasonText(selectionGroup, niceWidth);
		option2.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedOption = ClientCloseOption.RESTART_CLIENT;
				option2feedback.setVisible(((Button)e.getSource()).getSelection());
			}
		});

		Button option3 = optionButton(selectionGroup, "I need to restart the client and the server (Please tell us why)",
				niceWidth);
		final Text option3feedback = reasonText(selectionGroup, niceWidth);
		option3.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedOption = ClientCloseOption.RESTART_CLIENT_AND_SERVER;
				option3feedback.setVisible(((Button)e.getSource()).getSelection());
			}
		});

		Button option4 = optionButton(selectionGroup, "I'm finished for this visit, the hutch is searched and locked (if on-site) and I have or am about to inform the EHC on +44 1235 77 87 87.",
				niceWidth);
		option4.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedOption = ClientCloseOption.FINISHED;
			}
		});
	}

	private Button optionButton(Composite parent, String text, int width) {
		Button button = new Button(parent, SWT.WRAP | SWT.RADIO);
		GridDataFactory.swtDefaults().hint(width, SWT.DEFAULT).applyTo(button);
		button.setText(text);
		return button;
	}

	private Text reasonText(Composite parent, int width) {
		Text feedbackBox = new Text(parent, SWT.V_SCROLL | SWT.BORDER);
		GridDataFactory.swtDefaults().hint(width-40, SWT.DEFAULT).indent(15, 0).applyTo(feedbackBox);
		feedbackBox.setVisible(false);

		feedbackBox.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				restartReason = ((Text)(e.getSource())).getText();
			}
		});

		return feedbackBox;
	}
	
	public ClientCloseOption selectedOption() {
		return selectedOption;
	}
	
	public String restartReason() {
		return restartReason;
	} 
}