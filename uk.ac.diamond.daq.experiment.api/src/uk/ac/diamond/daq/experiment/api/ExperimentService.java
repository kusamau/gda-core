package uk.ac.diamond.daq.experiment.api;

import java.util.Set;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.scanning.api.event.scan.ScanRequest;

/**
 * This service allows us to save and retrieve configurations (e.g. defined scans)
 * associated with a given experiment ID.
 */
public interface ExperimentService {


	/**
	 * Save a scan request with the given name and associate it with the given experiment ID
	 */
	void saveScan(ScanRequest<IROI> scanRequest, String scanName, String experimentId);


	/**
	 * Get the scan request saved with the given scan name associated with the given experiment ID
	 */
	ScanRequest<IROI> getScan(String scanName, String experimentId);


	/**
	 * Get the names of all defined scans for the given experiment ID
	 */
	Set<String> getScanNames(String experimentId);

}