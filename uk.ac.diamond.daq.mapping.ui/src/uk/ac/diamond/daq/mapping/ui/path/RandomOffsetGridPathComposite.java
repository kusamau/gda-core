/*-
 * Copyright © 2017 Diamond Light Source Ltd.
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

package uk.ac.diamond.daq.mapping.ui.path;

import org.eclipse.scanning.api.points.models.RandomOffsetGridModel;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

public class RandomOffsetGridPathComposite extends AbstractPathComposite {

	public RandomOffsetGridPathComposite(Composite parent, RandomOffsetGridModel path) {
		super(parent, SWT.NONE);

		(new Label(this, SWT.NONE)).setText("Offset (%)");
		Text offset = new Text(this, SWT.BORDER);
		gdControls.applyTo(offset);
		bind(offset, "offset", path);

		(new Label(this, SWT.NONE)).setText("Seed");
		Text seed = new Text(this, SWT.BORDER);
		gdControls.applyTo(seed);
		bind(seed, "seed", path);

		(new Label(this, SWT.NONE)).setText(getFastAxisName() + " Points");
		Spinner fastPoints = new Spinner(this, SWT.BORDER);
		fastPoints.setMinimum(1);
		gdControls.applyTo(fastPoints);
		bind(fastPoints, "fastAxisPoints", path);

		(new Label(this, SWT.NONE)).setText(getSlowAxisName() + " Points");
		Spinner slowPoints = new Spinner(this, SWT.BORDER);
		slowPoints.setMinimum(1);
		gdControls.applyTo(slowPoints);
		bind(slowPoints, "slowAxisPoints", path);

		makeSnakeControl(this, path);
		makeContinuousControl(this, path);
	}

}
