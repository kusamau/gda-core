/*-
 * Copyright © 2009 Diamond Light Source Ltd.
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

package uk.ac.gda.client.scripting;

import gda.jython.ICommandRunner;
import gda.jython.InterfaceProvider;

import java.io.File;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;

import uk.ac.gda.common.rcp.util.EclipseUtils;

public class ReloadScriptActionDelegate implements IEditorActionDelegate {

	private IEditorPart ePart;
	@Override
	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
		if (targetEditor != null){
			ePart = targetEditor;		
		}
	}

	@Override
	public void run(IAction action) {
		ICommandRunner commandRunner = InterfaceProvider.getCommandRunner();
		if (ePart != null){
			final IEditorInput input = ePart.getEditorInput();
			
			final File fileToRun = EclipseUtils.getFile(input);
			final String name = fileToRun.getName();
			final String module = name.substring(0, name.indexOf('.'));
			commandRunner.evaluateCommand("reload(" + module + ")");
			
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
	}
}
