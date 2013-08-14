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

package uk.ac.gda.client.experimentdefinition.ui.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.action.IAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenameCommandHandler extends AbstractExperimentCommandHandler {

	private final static Logger logger = LoggerFactory.getLogger(RenameCommandHandler.class);

	@Override
	public void run(IAction action) {
		try {
			doRename();
		} catch (Exception e) {
			logger.error("Editor action failed " + getClass().getName(), e);
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		return doRename();
	}

	private Object doRename() {
		if (getEditorManager().getActiveRunEditor() != null) {
			getEditorManager().getActiveRunEditor().editSelectedElementName();
		} else {
			getEditorManager().editSelectedElement();
		}
		return null;
	}

}
