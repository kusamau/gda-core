/*-
 * Copyright © 2018 Diamond Light Source Ltd.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.jython.IScanDataPointObserver;

/**
 * This is a class designed to cache all the data from scan data points. It allows very quick retrieval of basic scan
 * data for use in Jython or by scan processing.
 *
 * @author James Mudd
 */
public class ScanDataPointCache implements IScanDataPointObserver {

	private static final Logger logger = LoggerFactory.getLogger(ScanDataPointCache.class);

	/** Cache holding the same data that would be printed to the terminal. Linked to ensure order as the map is iterated over*/
	private final Map<String, List<Double>> cache = new LinkedHashMap<>();

	@Override
	public void update(Object source, Object arg) {
		// TODO This check and cast can be removed once DAQ-928 is done
		if (arg instanceof IScanDataPoint) {
			final IScanDataPoint sdp = (IScanDataPoint) arg;

			if (sdp.getCurrentPointNumber() == 0) {
				initialiseScanCache(sdp);
			}

			// Get all the scannable and detector positions
			final List<Double> positions = Stream.concat(
					Arrays.stream(sdp.getPositionsAsDoubles()),
					Arrays.stream(sdp.getDetectorDataAsDoubles())).collect(Collectors.toList());

			if (positions.size() != cache.size()) {
				throw new IllegalArgumentException("Cache won't work SDP contains different number of positions than expected");
			}

			final Iterator<Double> positionIterator = positions.iterator();

			// Loop over the scannables adding their positions from this point
			for (List<Double> scannablePositions : cache.values()) {
				scannablePositions.add(positionIterator.next());
			}
			logger.trace("Added point {} of {} to cache", sdp.getCurrentPointNumber(), sdp.getNumberOfPoints());
		}
		else {
			// TODO This can be removed once DAQ-928 is done
			logger.trace("Received update that was not a SDP");
		}
	}

	private void initialiseScanCache(IScanDataPoint sdp) {
		logger.debug("Initalising cache...");
		// Remove cached data from previous scan
		cache.clear();

		final int scanPoints = sdp.getNumberOfPoints();

		// getNames returns the scannable and detector names in order
		for (String scannableName : sdp.getScannableHeader()) {
			cache.putIfAbsent(scannableName, new ArrayList<>(scanPoints));
		}

		for (String scannableName : sdp.getDetectorHeader()){
			cache.putIfAbsent(scannableName, new ArrayList<>(scanPoints));
		}

		logger.debug("Cache initalised. Size is {} scannables x {} points", cache.size(), scanPoints);
	}

	public List<Double> getPositionsFor(String scannableName) {
		logger.trace("Getting positions for: {}", scannableName);
		return cache.get(scannableName);
	}

}
