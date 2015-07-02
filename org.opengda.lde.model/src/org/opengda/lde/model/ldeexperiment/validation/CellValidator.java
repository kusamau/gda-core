/*******************************************************************************
 * Copyright © 2009, 2015 Diamond Light Source Ltd
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
 *
 * Contributors:
 * 	Diamond Light Source Ltd
 *******************************************************************************/
/**
 *
 * $Id$
 */
package org.opengda.lde.model.ldeexperiment.validation;

import java.util.Date;

import org.eclipse.emf.common.util.EList;

import org.opengda.lde.model.ldeexperiment.Sample;
import org.opengda.lde.model.ldeexperiment.Stage;

/**
 * A sample validator interface for {@link org.opengda.lde.model.ldeexperiment.Cell}.
 * This doesn't really do anything, and it's not a real EMF artifact.
 * It was generated by the org.eclipse.emf.examples.generator.validator plug-in to illustrate how EMF's code generator can be extended.
 * This can be disabled with -vmargs -Dorg.eclipse.emf.examples.generator.validator=false.
 */
public interface CellValidator {
	boolean validate();

	boolean validateStage(Stage value);

	boolean validateSamples(EList<Sample> value);
	boolean validateCellID(String value);
	boolean validateName(String value);

	boolean validateVisitID(String value);
	boolean validateEmail(String value);
	boolean validateStartDate(Date value);
	boolean validateEndDate(Date value);
	boolean validateEnableAutoEmail(boolean value);
	boolean validateCalibrant(String value);
	boolean validateCalibrant_x(double value);
	boolean validateCalibrant_y(double value);
	boolean validateCalibrant_exposure(double value);
	boolean validateEnvSamplingInterval(double value);
	boolean validateEvnScannableNames(String value);
}
