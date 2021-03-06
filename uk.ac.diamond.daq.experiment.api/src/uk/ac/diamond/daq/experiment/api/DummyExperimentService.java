package uk.ac.diamond.daq.experiment.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.scanning.api.event.scan.ScanRequest;
import org.eclipse.scanning.api.points.models.GridModel;
import org.eclipse.scanning.api.points.models.IScanPathModel;
import org.eclipse.scanning.api.points.models.SinglePointModel;
import org.eclipse.scanning.api.points.models.StepModel;

import gda.factory.FindableBase;
import uk.ac.diamond.daq.experiment.api.driver.DriverProfileSection;
import uk.ac.diamond.daq.experiment.api.driver.ExperimentDriverModel;
import uk.ac.gda.api.remoting.ServiceInterface;

/**
 * For runtime testing and demoing until a real implementation is made
 */
@ServiceInterface(ExperimentService.class)
public class DummyExperimentService extends FindableBase implements ExperimentService {

	private final Map<String, ScanRequest<IROI>> scans;

	private final Map<String, ExperimentDriverModel> driverProfiles;

	public DummyExperimentService() {
		scans = new HashMap<>();
		scans.put("diff_5x5", getDiffractionScan());
		scans.put("tr6_tomo", getTomographyScan());
		scans.put("diff_spot", getDiffractionSpotScan());

		driverProfiles = new HashMap<>();
		driverProfiles.put("trapez_30s", getProfile1());
		driverProfiles.put("sombrero", getProfile2());
		driverProfiles.put("saw_10_pp", getProfile3());
		driverProfiles.put("Aluminium Plastic Deformation Profile", getUserWorkingGroupProfile());
	}

	private ScanRequest<IROI> getDiffractionScan() {
		IScanPathModel model = new GridModel("beam_x", "beam_y", 5, 5);
		IROI roi = new RectangularROI(0, 0, 5, 5, 0);
		return new ScanRequest<>(model, roi, null, null, null);
	}

	private ScanRequest<IROI> getTomographyScan() {
		IScanPathModel model = new StepModel("tr6_rot", 0, 180, 5);
		return new ScanRequest<>(model, null, null, null);
	}

	private ScanRequest<IROI> getDiffractionSpotScan() {
		SinglePointModel model = new SinglePointModel();
		model.setFastAxisName("beam_x");
		model.setSlowAxisName("beam_y");
		model.setX(12.5);
		model.setY(0.38);
		IROI roi = new PointROI(new double[] {12.5, 0.38});
		return new ScanRequest<>(model, roi, null, null, null);
	}

	private ExperimentDriverModel getProfile1() {
		ExperimentDriverModel profile = new ExperimentDriverModel();
		profile.setProfile(Arrays.asList(
				new DriverProfileSection(0, 5, 0.5),
				new DriverProfileSection(5, 5, 0.5),
				new DriverProfileSection(5, 0, 0.5)));
		profile.setName("trapez_30s");
		return profile;
	}

	private ExperimentDriverModel getProfile2() {
		ExperimentDriverModel profile = new ExperimentDriverModel();
		double tenSeconds = 10 / 60.0;
		profile.setProfile(Arrays.asList(
				new DriverProfileSection(0, 2.5, tenSeconds),
				new DriverProfileSection(2.5, 2.5, tenSeconds),
				new DriverProfileSection(2.5, 5, tenSeconds),
				new DriverProfileSection(5, 5, tenSeconds),
				new DriverProfileSection(5, 2.5, tenSeconds),
				new DriverProfileSection(2.5, 2.5, tenSeconds),
				new DriverProfileSection(2.5, 0, tenSeconds)));
		profile.setName("sombrero");
		return profile;
	}

	private ExperimentDriverModel getProfile3() {
		ExperimentDriverModel model = new ExperimentDriverModel();

		List<DriverProfileSection> singlePeriod = Arrays.asList(
													new DriverProfileSection(5, 10, 0.1),
													new DriverProfileSection(10, 5, 0.1));

		List<DriverProfileSection> wholeProfile = new ArrayList<>();

		for (int period = 0; period < 10; period++) {
			wholeProfile.addAll(singlePeriod);
		}

		model.setProfile(wholeProfile);
		model.setName("saw_10_pp");
		return model;
	}

	private ExperimentDriverModel getUserWorkingGroupProfile () {
		ExperimentDriverModel model = new ExperimentDriverModel();

		List<DriverProfileSection> profile = new ArrayList<>();

		//start experiment at 200 and hold for 15 seconds
		profile.add(new DriverProfileSection(200, 200, 0.25));
		//damp from 200 and drive to 304 in 1 minute
		profile.add(new DriverProfileSection(200,  304, 1.0));
		//hold at 304 for 30 seconds
		profile.add(new DriverProfileSection(304, 304, 0.5));
		//return to 0 in 30 seconds
		profile.add(new DriverProfileSection(304, 200, 1.0));

		model.setProfile(profile);

		model.setName("Aluminium Plastic Deformation Profile");

		return model;
	}

	@Override
	public void saveScan(ScanRequest<IROI> scanRequest, String scanName, String experimentId) {
		// no.
	}

	@Override
	public ScanRequest<IROI> getScan(String scanName, String experimentId) {
		return scans.get(scanName);
	}

	@Override
	public Set<String> getScanNames(String experimentId) {
		return new HashSet<>(scans.keySet());
	}

	@Override
	public void saveDriverProfile(ExperimentDriverModel profile, String profileName, String driverName,
			String experimentId) {
		profile.setName(profileName);
		driverProfiles.put(profileName, profile);
	}

	@Override
	public ExperimentDriverModel getDriverProfile(String driverName, String modelName, String experimentId) {
		return driverProfiles.get(modelName);
	}

	@Override
	public Set<String> getDriverProfileNames(String driverName, String experimentId) {
		return new HashSet<>(driverProfiles.keySet());
	}

}
