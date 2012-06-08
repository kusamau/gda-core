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

package gda.analysis.utils.optimisation;

import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import gda.analysis.DataSet;
import gda.analysis.TerminalPrinter;
import gda.analysis.functions.IFunction;
import gda.analysis.functions.Parameter;

public class ProblemFunction implements IFunction {

	ProblemDefinition def = null;
	private double[] parameters;

	public ProblemFunction(final ProblemDefinition definition) {
		def = definition;
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public void setName(String newName) {
	}

	@Override
	public double val(double... values) {
		return 0;
	}

	@Override
	public Parameter getParameter(int index) {
		return parameters == null ? null : new Parameter(parameters[index]);
	}

	@Override
	public Parameter[] getParameters() {
		return null;
	}

	@Override
	public int getNoOfParameters() {
		return def.getNumberOfParameters();
	}

	@Override
	public int getNoOfFunctions() {
		return 0;
	}

	@Override
	public IFunction getFunction(int index) {
		return null;
	}

	@Override
	public double getParameterValue(int index) {
		return parameters == null ? 0 : parameters[index];
	}

	@Override
	public double[] getParameterValues() {
		return parameters;
	}

	@Override
	public void setParameterValues(double... params) {
		parameters = params;
	}

	@Override
	public double partialDeriv(int Parameter, double... position) {
		return 0;
	}

	@Override
	public DataSet makeDataSet(DoubleDataset... values) {
		return null;
	}

	@Override
	public DoubleDataset makeDataset(IDataset... values) {
		return null;
	}

	@Override
	public double residual(boolean allValues, IDataset data, IDataset... values) {
		try {
			return def.eval(parameters);
		} catch (Exception e) {
			return 0;
		}
	}

	@Override
	public void disp() {
//		TerminalPrinter.print(toString());
	}

}
