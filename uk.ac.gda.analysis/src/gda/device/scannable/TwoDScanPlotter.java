/*-
 * Copyright © 2010 Diamond Light Source Ltd.
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

package gda.device.scannable;

import gda.device.DeviceException;
import gda.jython.IAllScanDataPointsObserver;
import gda.jython.IScanDataPointProvider;
import gda.jython.InterfaceProvider;
import gda.scan.ScanDataPoint;

import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;

/**
 * Plots a 2D graph of the current scan into an RCP plot window as the scan progresses.
 * <p>
 * To use this scannable, give it the names of the x,y and z columns and the plot view to send the plot to ("Plot 1" by
 * default. Then simply include in the scan command you wish to plot.
 */
public class TwoDScanPlotter extends ScannableBase implements IAllScanDataPointsObserver {

	private static final Logger logger = LoggerFactory.getLogger(TwoDScanPlotter.class);

	private DoubleDataset x;
	private DoubleDataset y;
	private DoubleDataset intensity;

	private String x_colName;
	private String y_colName;

	private String z_colName;  // Currently, this *must* be a detector as this class looks only in the 
	private String plotViewname = "Plot 1";

	private Double xStart;

	private Double xStop;

	private Double xStep;

	private Double yStart;

	private Double yStop;

	private Double yStep;

	public TwoDScanPlotter() {
		this.inputNames = new String[] {};
		this.extraNames = new String[] {};
		this.outputFormat = new String[] {};
	}

	public void setXArgs(Double xStart, Double xStop, Double xStep) {
		this.xStart = xStart;
		this.xStop = xStop;
		this.xStep = xStep;
	}

	public void setYArgs(Double yStart, Double yStop, Double yStep) {
		this.yStart = yStart;
		this.yStop = yStop;
		this.yStep = yStep;
	}

	@Override
	public void atScanStart() throws DeviceException {
		// clear datasets and re-register with datapoint provider
		x = createTwoDset(xStart, xStop, xStep,false);
		y = createTwoDset(yStart, yStop, yStep,true);
		intensity = null;
		InterfaceProvider.getScanDataPointProvider().addIScanDataPointObserver(this);
	}

	private DoubleDataset createTwoDset(Double start, Double stop, Double step, Boolean reverse) {
		int numPoints = ScannableUtils.getNumberSteps(start, stop, step) + 1; // why + 1?
		double[] values = new double[numPoints];
		Double value = start;
		for (int index = 0; index < numPoints; index++){
			values[index] = value;
			value += step;
		}
		if (reverse){
			ArrayUtils.reverse(values);
		}
		
		return new DoubleDataset(values);
	}

	@Override
	public void atScanEnd() throws DeviceException {
		// Do not deregister as this may be called before all SDPs have been completed in the pipeline and broadcast;
		// The update methid will deregister once the final point has been received. If the scan fials or is stopped
		// atCommandFailure() or stop() will deregister.
	}

	@Override
	public void atCommandFailure() {
		deregisterAsScanDataPointObserver();
	}
	
	@Override
	public void stop() throws DeviceException {
		deregisterAsScanDataPointObserver();
	}
	
	private void deregisterAsScanDataPointObserver() {
		InterfaceProvider.getScanDataPointProvider().deleteIScanDataPointObserver(this);
	}
	
	@Override
	public void update(Object source, Object arg) {
		if (source instanceof IScanDataPointProvider && arg instanceof ScanDataPoint) {
			int currentPoint = ((ScanDataPoint) arg).getCurrentPointNumber();
			int totalPoints = ((ScanDataPoint) arg).getNumberOfPoints();
			try {
				unpackSDP((ScanDataPoint) arg);
				plot();
				if (currentPoint == (totalPoints - 1)) {
					logger.info(getName() + " - last point recevied; deregistering as SDP listener.");
					deregisterAsScanDataPointObserver();
				}
			} catch (Exception e) {
				logger.error("exception while plotting 2D data: " + e.getMessage(), e);
				if (currentPoint == (totalPoints -1)) {
					logger.info(getName() + " - last point recevied; deregistering as SDP listener.");
					deregisterAsScanDataPointObserver();
				}
			}
		}
	}

	@Override
	public void rawAsynchronousMoveTo(Object position) throws DeviceException {
	}

	@Override
	public Object rawGetPosition() throws DeviceException {
		return null;
	}

	private void unpackSDP(ScanDataPoint sdp) {
		// NB: ScanDataPoint scan dimensions work are an array working from outside to inside in the nested scans
		// NB: here we are plotting the inner as the x, and the outer as the y.
		if (intensity == null) {
			intensity = new DoubleDataset(sdp.getScanDimensions()[0], sdp.getScanDimensions()[1]);
//			int scanSize = sdp.getScanDimensions()[0] * sdp.getScanDimensions()[1];
//			x = new DoubleDataset(scanSize);
//			y = new DoubleDataset(scanSize);
		}

		Double inten = getIntensity(sdp);
		int[] locationInDataSets = getSDPLocation(sdp);
		int xLoc = locationInDataSets[0];
		// y has to be reversed as otherwise things are plotted from the top left, not bottom right as the plotting system 2d plotting was originally for images which work this way.
		int yLoc = sdp.getScanDimensions()[0] - locationInDataSets[1] -1;
		intensity.set(inten, yLoc, xLoc);
	}

	private int[] getSDPLocation(ScanDataPoint sdp) {
		int yLoc = 0;
		int xLoc = sdp.getCurrentPointNumber();

		if (sdp.getCurrentPointNumber() >= sdp.getScanDimensions()[1]) {
			yLoc = sdp.getCurrentPointNumber() / sdp.getScanDimensions()[1];
			xLoc = sdp.getCurrentPointNumber() - (yLoc * sdp.getScanDimensions()[1]);
		}

		return new int[] { xLoc, yLoc };
	}

	private Double getIntensity(ScanDataPoint sdp) {
		return sdp.getDetectorDataAsDoubles()[getPositionOfDetector(z_colName, sdp)];
	}

	private int getPositionOfDetector(String columnName, ScanDataPoint sdp) {
		Object[] headers = sdp.getDetectorHeader().toArray();
		return org.apache.commons.lang.ArrayUtils.indexOf(headers, columnName);
	}

	public void plot() throws Exception {
		// SDAPlotter.surfacePlot(plotViewname, x, y, intensity);
		SDAPlotter.imagePlot(plotViewname, x, y, intensity);
	}

	@Override
	public boolean isBusy() throws DeviceException {
		return false;
	}

	public String getX_colName() {
		return x_colName;
	}

	public void setX_colName(String xColName) {
		x_colName = xColName;
	}

	public String getY_colName() {
		return y_colName;
	}

	public void setY_colName(String yColName) {
		y_colName = yColName;
	}

	public String getZ_colName() {
		return z_colName;
	}

	public void setZ_colName(String zColName) {
		z_colName = zColName;
	}

	public void setPlotViewname(String plotViewname) {
		this.plotViewname = plotViewname;
	}

	public String getPlotViewname() {
		return plotViewname;
	}

}
