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

package gda.scan;

import gda.device.DeviceException;

import java.text.MessageFormat;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.python.core.PyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScanDataPointPopulatorAndPublisher implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(ScanDataPointPopulatorAndPublisher.class);

	private MultithreadedScanDataPointPipeline pipeline; // for access to setException() and the queue;

	private IScanDataPoint point;

	private ScanDataPointPublisher broadcaster;

	public ScanDataPointPopulatorAndPublisher(ScanDataPointPublisher broadcaster, IScanDataPoint point,
			MultithreadedScanDataPointPipeline pipeline) {
		this.broadcaster = broadcaster;
		this.pipeline = pipeline;
		this.point = point;
		if( logger.isDebugEnabled())
			logger.debug("'{}': created", point.toString());
	}

	@Override
	public void run() {
		if( logger.isDebugEnabled())
			logger.debug("'{}': running", point.toString());
	
		try {
			convertPositionFuturesToPositions(point);
		} catch (Exception e) {
			logger.error("Exception populating data point", e);
			pipeline.setExceptionAndShutdownNow(e);
			return ; // don't move on to publish
		}
		
		if( logger.isDebugEnabled())
			logger.debug("'{}': futures converted", point.toString());

		try {
			logger.debug("'{}' publishing", point.getUniqueName());
			broadcaster.publish(point);
		} catch (Throwable e) {
			logger.error("Exception broadcasting data point", e);
			pipeline.setExceptionAndShutdownNow(e);
		}
		if( logger.isDebugEnabled())
			logger.debug("'{}' published", point.toString());
	}

	private void convertPositionFuturesToPositions(IScanDataPoint point) throws Exception {
		convertDevices(point.getScannableNames(), point.getPositions());
		convertDevices(point.getDetectorNames(), point.getDetectorData());
	}
	
	private void convertDevices(Vector<String> names, Vector<Object> positions) throws Exception {
		for (int i = 0; i < positions.size(); i++) {
			Object possiblyFuture = positions.get(i);
			String name = names.get(i);
			
			if( logger.isDebugEnabled())
				logger.debug("'{}' converting '{}'", point.toString(), name);
			Object pos = convertPositionFutureToPosition(name, possiblyFuture);
			if( logger.isDebugEnabled())
				logger.debug("'{}' converted '{}'", point.toString(), name);
			positions.set(i, pos);
		}
	}
	
	private Object convertPositionFutureToPosition(String name, Object possiblyFuture) throws Exception {
		if (!(possiblyFuture instanceof Future<?>)) return possiblyFuture;
		
		try {
			return ((Future<?>) possiblyFuture).get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof DeviceException) {
				throw new DeviceException(String.format(
						"DeviceException while computing point %d %s position: %s", point.getCurrentPointNumber(), name, cause.getMessage()), cause);
			} else if (cause instanceof PyException) {
				throw new DeviceException(String.format(
						"PyException while computing point %d %s position: %s", point.getCurrentPointNumber(), name, cause.toString()) , cause);
			} //else
			throw new Exception(String.format(
					"Exception while computing point %d %s position: %s",point.getCurrentPointNumber(), name, cause.getMessage()), cause);
		} catch (InterruptedException e) {
			logger.warn(String.format(
					"Interrupted while waiting for point %d %s position computation to complete: %s", point.getCurrentPointNumber(), name, e.getMessage()), e);
			throw e;
		}
	}
}
