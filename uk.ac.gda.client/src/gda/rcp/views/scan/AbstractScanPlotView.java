/*-
 * Copyright © 2012 Diamond Light Source Ltd.
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

package gda.rcp.views.scan;

import gda.rcp.util.ScanDataPointEvent;
import gda.rcp.util.ScanPlotListener;
import gda.rcp.util.UIScanDataPointEventService;
import gda.scan.IScanDataPoint;
import gda.scan.ScanDataPoint;

import java.awt.Color;
import java.util.List;

import javax.swing.Timer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.plotting.AxisValues;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.Plot1DAppearance;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.PlotException;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.enums.Plot1DStyles;
import uk.ac.diamond.scisoft.analysis.rcp.views.plot.AbstractPlotView;
import uk.ac.diamond.scisoft.analysis.rcp.views.plot.IPlotData;
import uk.ac.diamond.scisoft.analysis.rcp.views.plot.PlotBean;
import uk.ac.gda.ClientManager;

/**
 * This view can plot data from a scan. By default it plots a single value or expression from the scan, for instance
 * ln(I0/It). Therefore this view is not equivalent to a standard scan plot of all the values as used in generic GDA
 * client. However such a view could be constructed with this as the parent.
 */
public abstract class AbstractScanPlotView extends AbstractPlotView implements ScanPlotListener {

	public static enum GRAPH_MODE {
		CHECK_VISIBLE_AND_TIMER,
		CHECK_TIMER_ONLY,
		DIRECT_DRAW
	}

	private static final Logger logger = LoggerFactory.getLogger(AbstractScanPlotView.class);

	protected int sampleRate = 500;
	protected int scanNumber = 0;
	protected boolean scanning = false;
	protected boolean legendEntries = false;
	protected Timer timer;
	protected IPlotData x, y, xSaved, ySaved;

	protected AbstractScanPlotView() {
		try {
			UIScanDataPointEventService.getInstance().addScanPlotListener(this);
		} catch (Exception ne) {
			if (!ClientManager.isTestingMode()) {
				logger.error("Cannot connect to data point service", ne);
			}
		}
	}

	// Things you will be asked to implement.
	protected abstract IPlotData getX(IScanDataPoint... point);

	protected abstract IPlotData getY(IScanDataPoint... point);

	protected abstract String getCurrentPlotName(int scanNumber);

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);

		try {
			// Connect to the service and check if there is a stopped scan waiting there.
			// If there is, then plot it.
			final UIScanDataPointEventService service = UIScanDataPointEventService.getInstance();
			if (!service.isRunning()) {
				getSite().getShell().getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						try {
							plotPointsFromService();
						} catch (Exception e) {
							logger.error("Cannot plot points from last scan", e);
						}
					}
				});
			}
		} catch (Exception e) {
			if (!ClientManager.isTestingMode()) {
				logger.error("Cannot connect to ScanDataPointEventService", e);
			}
		}
	}

	@Override
	public void scanStopped() {

		this.scanning = false;

//		should not do any work at this point in a base class
//		// Plot last plot as we might have been
//		// invisible during the updates.
//		try {
//			plotPointsFromService();
//		} catch (Exception e) {
//			logger.error("Cannot plot final data when scan stopped.", e);
//		}

		legendEntries = false;

		// We do *NOT* clear the xAxisValues as this breaks zooming.
		// this.xAxisValues.clear();

		if (isPlotDataSavable()) {
			if (y != null)
				this.ySaved = y.clone();
			if (x != null)
				this.xSaved = x.clone();
		}

		if (y != null)
			y.clear();
		if (x != null)
			x.clear();
	}

	@SuppressWarnings("unused")
	// allow inheriting classes to throw an exception
	protected void plotPointsFromService() throws Exception {

		final List<IScanDataPoint> cp = UIScanDataPointEventService.getInstance().getCurrentDataPoints();
		if (cp.isEmpty())
			return;
		IScanDataPoint[] points = cp.toArray(new ScanDataPoint[cp.size()]);
		y = getY(points);
		x = getX(points);

		createPlot(x, y, GRAPH_MODE.DIRECT_DRAW);

		if (isPlotDataSavable()) {
			this.ySaved = y.clone();
			this.xSaved = x.clone();
		}
	}

	protected boolean isPlotDataSavable() {
		return true;
	}

	@Override
	public void scanStarted() {
		++scanNumber;
		xAxisValues.clear();
		// Prime numbers grow better.
		scanning = true;
		legendEntries = false;
		plotter.getColourTable().clearLegend();
	}

	/**
	 * Class which listens implements scanDataPointChanged(). Do not call programmatically, use plotPointsFromService()
	 * instead to create a plot from the last scan.
	 * 
	 * @param e
	 */
	@Override
	public void scanDataPointChanged(final ScanDataPointEvent e) {

		// This was slowing down everything, so calculate x and y in a separate thread
		Job updater = new Job("Updating graph for " + this.getClass().getName()) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					refreshXY(e);
				} catch (IllegalArgumentException e) {
					// ignore these errors as it simply means that a scan which is not XAS is being run and we do not
					// want to swamp the user in meaningless messages.
					return Status.OK_STATUS;
				}
				return Status.OK_STATUS;
			}
		};
		updater.setUser(false);
		updater.schedule();

	}

	protected void refreshXY(final ScanDataPointEvent e) {
		// Add all points.
		if (!scanning && e.getDataPoints().size() > 1) {
			// It started without us.
			scanStarted();
			// We might be being notified after a scan is active.
			// Process all the points and add them to a data set.
			IScanDataPoint[] points = e.getDataPoints().toArray(new ScanDataPoint[e.getDataPoints().size()]);
			y = getY(points);
			x = getX(points);

		} else { // Add a single point, the current point.
					// Having this duel operational mode allows
					// plots which are simple f(x,y) to cache
					// calculated value. See LnI0ItScanPlotView
			y = getY(e.getCurrentPoint());
			x = getX(e.getCurrentPoint());
		}

		// when finished, refresh the plot in the workbench thread
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				rebuildPlot();
			}

		});
	}

	protected void rebuildPlot() {
		createPlot(x, y, GRAPH_MODE.CHECK_VISIBLE_AND_TIMER);
	}

	/**
	 * Used for sending data to the plot programatically. Currently for testing only.
	 * 
	 * @param x
	 * @param y
	 * @param mode
	 * @return true if updated graph, false if too soon since last update.
	 */
	public boolean createPlot(final IPlotData x, final IPlotData y, final GRAPH_MODE mode) {

		if (timer == null && (mode == GRAPH_MODE.CHECK_VISIBLE_AND_TIMER || mode == GRAPH_MODE.CHECK_TIMER_ONLY)) {
			timer = new Timer(getSampleRate(), null);
			timer.setRepeats(false);
		}

		// Do not plot if graph not visible
		// Then plot if user selects it again
		// This saves CPU. If the user clicks on the graph during a scan, it will update again.
		// If the user stops the graph is updated with the final state.
		if (mode == GRAPH_MODE.CHECK_VISIBLE_AND_TIMER) {
			if (!getSite().getPage().isPartVisible(this)) {
				return false;
			}
		}

		// NOTE have to use timer otherwise too many points come through.
		// GDA plotting has similar problem and solves in similar way.
		if (mode == GRAPH_MODE.DIRECT_DRAW ||

		((mode == GRAPH_MODE.CHECK_VISIBLE_AND_TIMER || mode == GRAPH_MODE.CHECK_TIMER_ONLY) && !timer.isRunning())) {

			if (y == null)
				return false;
			if (y.size() < 2)
				return false;

			if (!x.isDataSetValid())
				return false;

			xAxisValues.setValues(x.getDataSet());
			final boolean plotted;
			if (y.isMulti()) {
				plotted = addMultiple(y);
			} else {
				plotted = addSingle(y);
			}
			if (!plotted)
				return false;

			plotter.setTitle(getGraphTitle());
			plotter.setYAxisLabel(getYAxis());
			plotter.refresh(false);
			if (timer != null)
				timer.start();
			return true;
		}

		return false;
	}

	/**
	 * Adds current data to another plotter, also create legends.
	 */
	@Override
	public PlotBean getPlotBean() {

		final PlotBean ret = new PlotBean();
		final IPlotData y = this.scanning ? this.y : this.ySaved;

		ret.setDataSets(y.getDataMap());
		ret.setCurrentPlotName(getCurrentPlotName(scanNumber));

		ret.setXAxisMode(getXAxisMode().asInt());
		ret.setYAxisMode(getYAxisMode().asInt());
		ret.setXAxisValues(getXAxisValues());

		ret.setXAxis(getXAxis());
		ret.setYAxis(getYAxis());

		return ret;
	}

	/**
	 * Method called to add single plot data, overide if required. By default all single plots are black as this is easy
	 * to see and looks boring and professional.
	 * 
	 * @param y
	 */
	protected boolean addSingle(IPlotData y) {

		if (!legendEntries) {
			final Plot1DAppearance app = new Plot1DAppearance(Color.BLACK, Plot1DStyles.SOLID, 1,
					getCurrentPlotName(scanNumber));
			plotter.getColourTable().addEntryOnLegend(app);
			legendEntries = true;
		}
		if (!y.isDataSetValid()) {
			logger.warn("Calculated datasets for '" + this.getTitle() + "' contains infinities or NaNs.");
			return false;
		}
		try {
			plotter.replaceCurrentPlot(y.getDataSet());
		} catch (PlotException ex) {
			logger.warn("Calculated dataset for '" + this.getTitle() + "'.", ex);
			return false;
		}

		return true;
	}

	/**
	 * Method called to add multiple plot data, overide if required.
	 * 
	 * @param y
	 */
	protected boolean addMultiple(IPlotData y) {

		if (!legendEntries) {
			AbstractPlotView.createMultipleLegend(plotter, y.getDataMap());
			legendEntries = true;
		}
		if (!y.isDataSetsValid()) {
			logger.info("Calculated datasets for '" + this.getTitle() + "' contains infinities or NaNs.");
			return false;
		}
		try {
			plotter.replaceAllPlots(y.getDataSets());
		} catch (PlotException e) {
			logger.info("Calculated datasets for '" + this.getTitle() + "'.", e);
			return false;
		}
		return true;
	}

	@Override
	public void scanPaused() {

	}

	/**
	 * @return Returns the sampleRate in ms.
	 */
	public int getSampleRate() {
		return sampleRate;
	}

	/**
	 * @param sampleRate
	 *            The sampleRate to set in ms.
	 */
	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
		if (timer != null)
			timer.setDelay(sampleRate);
	}

	public AxisValues getXAxisValues() {
		return scanning ? xAxisValues.clone() : new AxisValues(xSaved.getDataSet());
	}

	@Override
	public void dispose() {
		try {
			UIScanDataPointEventService.getInstance().removeScanPlotListener(this);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		super.dispose();
	}
}
