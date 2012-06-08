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

package gda.analysis.functions.dataset;

import gda.analysis.DataSet;

import java.util.List;

import junit.framework.TestCase;

import org.junit.Test;

/**
 *
 */
public class MapToRotatedCartesianTest extends TestCase {
	DataSet d = new DataSet(500,500);

	/**
	 */
	@Override
	public void setUp() {
		d.fill(1.);
	}

	/**
	 * 
	 */
	@Test
	public void testMapToRotatedCartesian() {
		MapToRotatedCartesian mp = new MapToRotatedCartesian(100,70,50,30,45.);
		DataSet pd = d.exec(mp).get(0);
		
		Sum s = new Sum();
		List<DataSet> dsets = pd.exec(s);
		double answer = 50.*30;
		assertEquals(answer, dsets.get(0).get(0), answer*1e-4); // within 0.01% accuracy
	}

}
