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

package uk.ac.diamond.daq.mapping.region;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.LinearROI;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;

import uk.ac.diamond.daq.mapping.api.ILineMappingRegion;
import uk.ac.diamond.daq.mapping.api.IMappingScanRegionShape;

/**
 * A line region that snaps to horizontal or vertical.
 */
public class SnappedLineMappingRegion implements ILineMappingRegion {

	private enum Orientation {
		HORIZONTAL,
		VERTICAL
	}

	private Orientation orientation = Orientation.HORIZONTAL;

	private double start = 0;
	private double stop = 1;
	private double constant = 0;

	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	@Override
	public String getName() {
		return "Snapped Line";
	}

	@Override
	public IROI toROI() {
		LinearROI roi = new LinearROI();
		roi.setPoint(getxStart(), getyStart());
		roi.setEndPoint(getxStop(), getyStop());
		return roi;
	}

	@Override
	public String whichPlottingRegionType() {
		return RegionType.LINE.toString();
	}

	/**
	 * Overrides {@link IMappingScanRegionShape} to also snap the given ROI to
	 * horizontal or vertical, depending on the longest axis.
	 *
	 * @see IMappingScanRegionShape#updateFromROI(IROI)
	 */
	@Override
	public void updateFromROI(IROI newROI) {
		if (!(newROI instanceof LinearROI)) {
			throw new IllegalArgumentException("Snapped line mapping region can only update from a LinearROI");
		}

		LinearROI roi = (LinearROI) newROI;

		// First save the old values
		Orientation oldOrientation = orientation;
		double oldConstantAxis = constant;
		double oldStart = start;
		double oldStop = stop;

		// update the roi, snapping the line to the vertical/horizontal axis only
		// by setting the shortest axis to have 0 size
		double xStart = roi.getPoint()[0];
		double xStop = roi.getEndPoint()[0];
		double yStart = roi.getPoint()[1];
		double yStop = roi.getEndPoint()[1];

		if (Math.abs(xStop - xStart) > Math.abs(yStop - yStart)) {
			orientation = Orientation.HORIZONTAL;
			constant = yStart;
			start = xStart;
			stop = xStop;
			roi.setEndPoint(xStop, constant);
		} else {
			orientation = Orientation.VERTICAL;
			constant = xStart;
			start = yStart;
			stop = yStop;
			roi.setEndPoint(constant, yStop);
		}

		this.pcs.firePropertyChange("orientation", oldOrientation, orientation);
		this.pcs.firePropertyChange("constant", oldConstantAxis, constant);
		this.pcs.firePropertyChange("start", oldStart, start);
		this.pcs.firePropertyChange("stop", oldStop, stop);

	}

	@Override
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		this.pcs.addPropertyChangeListener(listener);
	}

	@Override
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		this.pcs.removePropertyChangeListener(listener);
	}

	@Override
	public double getxStart() {
		return orientation == Orientation.HORIZONTAL ? start : constant;
	}

	@Override
	public double getyStart() {
		return orientation == Orientation.HORIZONTAL ? constant : start;
	}

	@Override
	public double getxStop() {
		return orientation == Orientation.HORIZONTAL ? stop : constant;
	}

	@Override
	public double getyStop() {
		return orientation == Orientation.HORIZONTAL ? constant : stop;
	}

}
