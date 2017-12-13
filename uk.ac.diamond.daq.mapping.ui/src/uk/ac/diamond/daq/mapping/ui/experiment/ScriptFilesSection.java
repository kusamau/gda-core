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

import java.io.File;
import java.util.Objects;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.richbeans.api.generator.IGuiGeneratorService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import uk.ac.diamond.daq.mapping.api.IScriptFiles;

/**
 * A section to configure the script files to run before and/or after a scan.
 */
public class ScriptFilesSection extends AbstractMappingSection {

	private Text summaryText;

	@Override
	public boolean shouldShow() {
		// script files section only shown if bean is non null. Create an empty script files bean
		// in your spring configuration to allow script files to be set
		return getMappingBean().getScriptFiles() != null;
	}

	@Override
	public void createControls(Composite parent) {
		final Composite scriptsComposite = new Composite(parent, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(3).equalWidth(false).applyTo(scriptsComposite);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(scriptsComposite);

		new Label(scriptsComposite, SWT.NONE).setText("Script Files");

		Composite scriptsSummaryComposite = new Composite(scriptsComposite, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(2).equalWidth(false).applyTo(scriptsSummaryComposite);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(scriptsSummaryComposite);
		summaryText = new Text(scriptsSummaryComposite, SWT.MULTI | SWT.READ_ONLY);
		GridDataFactory.swtDefaults().grab(true, false).applyTo(summaryText);
		summaryText.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));

		final Button editScriptsButton = new Button(scriptsComposite, SWT.PUSH);
		editScriptsButton.setImage(MappingExperimentUtils.getImage("icons/pencil.png"));
		editScriptsButton.setToolTipText("Select Script Files");
		GridDataFactory.swtDefaults().align(SWT.TRAIL, SWT.CENTER).applyTo(editScriptsButton);

		final IGuiGeneratorService guiGenerator = getService(IGuiGeneratorService.class);
		editScriptsButton.addListener(SWT.Selection, event -> {
			guiGenerator.openDialog(getMappingBean().getScriptFiles(), parent.getShell(), "Select Script Files");
			updateSummaryText();
		});

		updateSummaryText();
	}

	private void updateSummaryText() {
		IScriptFiles scripts = getMappingBean().getScriptFiles();
		StringBuilder summary = new StringBuilder();
		if (Objects.nonNull(scripts.getBeforeScanScript()) && !scripts.getBeforeScanScript().isEmpty()) {
			summary.append("before: " + getScriptName(scripts.getBeforeScanScript())+";");
		}
		if (Objects.nonNull(scripts.getAfterScanScript()) && !scripts.getAfterScanScript().isEmpty()) {
			summary.append("after: " + getScriptName(scripts.getAfterScanScript()));
		}
		summaryText.setText(summary.toString());
	}

	private String getScriptName(String path) {
		return new File(path).getName();
	}

}
