/*-
 * Copyright © 2009 Diamond Light Source Ltd., Science and Technology
 * Facilities Council
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

package gda.scan;

import java.io.Serializable;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScanPlotSettings implements Serializable {
	
	public static enum UnlistedColumnBehaviour {
		PLOT,
		PLOT_NOT_VISIBLE,
		IGNORE,
	}
	
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(ScanPlotSettings.class);
	private String xAxisName;
	private String[] yAxesShown, yAxesNotShown;
	private Double xMin, xMax;
	private boolean ignore = false;
	private UnlistedColumnBehaviour unlistedColumnBehaviour = UnlistedColumnBehaviour.IGNORE;

	public String getXAxisName() {
		return xAxisName;
	}

	public void setXAxisName(String axisName) {
		xAxisName = axisName;
	}

	public Double getXMin() {
		return xMin;
	}

	public void setXMin(Double min) {
		xMin = min;
	}

	public Double getXMax() {
		return xMax;
	}

	public void setXMax(Double max) {
		xMax = max;
	}

	public void setYAxesShown(String[] yAxesShown) {
		this.yAxesShown = yAxesShown;
	}

	public void setYAxesNotShown(String[] yAxesNotShown) {
		this.yAxesNotShown = yAxesNotShown;
	}

	public String[] getYAxesShown() {
		return yAxesShown;
	}

	public String[] getYAxesNotShown() {
		return yAxesNotShown;
	}
	public boolean isIgnore() {
		return ignore;
	}

	public void setIgnore(boolean ignore) {
		this.ignore = ignore;
	}

	public UnlistedColumnBehaviour getUnlistedColumnBehaviour() {
		return unlistedColumnBehaviour;
	}

	public void setUnlistedColumnBehaviour(UnlistedColumnBehaviour unlistedColumnBehaviour) {
		this.unlistedColumnBehaviour = unlistedColumnBehaviour;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (ignore ? 1231 : 1237);
		result = prime * result + ((unlistedColumnBehaviour == null) ? 0 : unlistedColumnBehaviour.hashCode());
		result = prime * result + ((xAxisName == null) ? 0 : xAxisName.hashCode());
		result = prime * result + ((xMax == null) ? 0 : xMax.hashCode());
		result = prime * result + ((xMin == null) ? 0 : xMin.hashCode());
		result = prime * result + Arrays.hashCode(yAxesNotShown);
		result = prime * result + Arrays.hashCode(yAxesShown);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScanPlotSettings other = (ScanPlotSettings) obj;
		if (ignore != other.ignore)
			return false;
		if (unlistedColumnBehaviour != other.unlistedColumnBehaviour)
			return false;
		if (xAxisName == null) {
			if (other.xAxisName != null)
				return false;
		} else if (!xAxisName.equals(other.xAxisName))
			return false;
		if (xMax == null) {
			if (other.xMax != null)
				return false;
		} else if (!xMax.equals(other.xMax))
			return false;
		if (xMin == null) {
			if (other.xMin != null)
				return false;
		} else if (!xMin.equals(other.xMin))
			return false;
		if (!Arrays.equals(yAxesNotShown, other.yAxesNotShown))
			return false;
		if (!Arrays.equals(yAxesShown, other.yAxesShown))
			return false;
		return true;
	}

	private String listToString(String[] list) {
		if (list == null)
			return null;
		String s = "";
		for (String l : list) {
			s += l + ",";
		}
		return s;
	}

	@Override
	public String toString() {
		return "xAxis = " + (xAxisName != null ? xAxisName.toString() : "null") + ", yAxesShown = "
				+ listToString(yAxesShown) + ", yAxesNotShown = " + listToString(yAxesNotShown) + ", xMin = "
				+ (xMin != null ? xMin.toString() : "null") + ", xMax = "
				+ (xMax != null ? xMax.toString() : "null, unlisted columns: " + unlistedColumnBehaviour);
	}

}
