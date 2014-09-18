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

package gda.analysis.utils;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IFunction;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;

import gda.analysis.utils.optimisation.FittingProblemDefinition;
import gda.analysis.utils.optimisation.Neldermead;
import gda.analysis.utils.optimisation.ProblemDefinition;

/**
 * The implementation of the Nelder-Mead optimisation for the fitting routines.
 * 
 * It is variously known as the down-hill simplex or amoeba method
 * @deprecated use {@link uk.ac.diamond.scisoft.analysis.optimize.NelderMead}
 */
@Deprecated
public class NelderMead implements IOptimizer {

	double accuracy = 0.1;

	/**
	 * @param accuracy
	 */
	public NelderMead(double accuracy) {
		this.accuracy = accuracy;
	}

	@Override
	public void optimize(IDataset[] coords, IDataset data, IFunction function) throws Exception {
		final int numCoords = coords.length;
		DoubleDataset[] newCoords = new DoubleDataset[numCoords];
		for (int i = 0; i < numCoords; i++) {
			newCoords[i] = (DoubleDataset) DatasetUtils.convertToDataset(coords[i]).cast(Dataset.FLOAT64);
		}

		DoubleDataset dataValues = (DoubleDataset) DatasetUtils.convertToDataset(data).cast(Dataset.FLOAT64);

		Optimize(newCoords, dataValues, function);
	}

	@Override
	public void Optimize(DoubleDataset[] coords, DoubleDataset dataValues, IFunction function) throws Exception {

		ProblemDefinition pd = new FittingProblemDefinition(coords, dataValues, function);

		Neldermead nm = new Neldermead();

		double[] bestParams = nm.optimise(function.getParameterValues(), pd, accuracy);

		function.setParameterValues(bestParams);

	}

}
