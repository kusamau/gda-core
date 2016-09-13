/*-
 * Copyright © 2016 Diamond Light Source Ltd.
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

package uk.ac.diamond.daq.mapping.ui.test;

import static org.eclipse.scanning.api.script.ScriptLanguage.SPEC_PASTICHE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.scanning.api.device.models.IDetectorModel;
import org.eclipse.scanning.api.event.scan.ScanBean;
import org.eclipse.scanning.api.event.scan.ScanRequest;
import org.eclipse.scanning.api.points.IPosition;
import org.eclipse.scanning.api.points.models.GridModel;
import org.eclipse.scanning.api.script.ScriptRequest;
import org.eclipse.scanning.example.detector.MandelbrotModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import uk.ac.diamond.daq.mapping.api.IMappingScanRegionShape;
import uk.ac.diamond.daq.mapping.api.IScriptFiles;
import uk.ac.diamond.daq.mapping.impl.DetectorModelWrapper;
import uk.ac.diamond.daq.mapping.impl.MappingExperimentBean;
import uk.ac.diamond.daq.mapping.impl.MappingStageInfo;
import uk.ac.diamond.daq.mapping.impl.ScriptFiles;
import uk.ac.diamond.daq.mapping.region.RectangularMappingRegion;
import uk.ac.diamond.daq.mapping.ui.experiment.ScanRequestConverter;

public class ScanRequestConverterTest {

	private static final String X_AXIS_NAME = "testing_stage_x";
	private static final String Y_AXIS_NAME = "testing_stage_y";

	private ScanRequestConverter scanRequestConverter;
	private MappingStageInfo mappingStageInfo;
	private MappingExperimentBean experimentBean;
	private GridModel scanPath;

	@Before
	public void setUp() throws Exception {
		mappingStageInfo = new MappingStageInfo();
		mappingStageInfo.setActiveFastScanAxis(X_AXIS_NAME);
		mappingStageInfo.setActiveSlowScanAxis(Y_AXIS_NAME);

		scanRequestConverter = new ScanRequestConverter();
		scanRequestConverter.setMappingStageInfo(mappingStageInfo);

		// Set up the experiment bean with some sensible defaults
		experimentBean = new MappingExperimentBean();

		scanPath = new GridModel();
		experimentBean.getScanDefinition().getMappingScanRegion().setScanPath(scanPath);

		IMappingScanRegionShape scanRegion = new RectangularMappingRegion();
		experimentBean.getScanDefinition().getMappingScanRegion().setRegion(scanRegion);

		experimentBean.setDetectorParameters(Collections.emptyList());
	}

	@After
	public void tearDown() throws Exception {
		mappingStageInfo = null;
		scanRequestConverter = null;
	}

	@Test
	public void testDetectorIsIncludedCorrectly() {
		String detName = "det1";
		IDetectorModel detModel = new MandelbrotModel();
		experimentBean.setDetectorParameters(Arrays.asList(new DetectorModelWrapper(detName, detModel, true)));

		ScanBean scanBean = scanRequestConverter.convertToScanBean(experimentBean);
		ScanRequest<?> scanRequest = scanBean.getScanRequest();

		assertEquals(scanRequest.getDetectors().get(detName), detModel);
	}

	@Test
	public void testDetectorIsExcludedCorrectly() {
		String detName = "det1";
		IDetectorModel detModel = new MandelbrotModel();
		experimentBean.setDetectorParameters(Arrays.asList(new DetectorModelWrapper(detName, detModel, false)));

		ScanBean scanBean = scanRequestConverter.convertToScanBean(experimentBean);
		ScanRequest<?> scanRequest = scanBean.getScanRequest();

		// This test relies on the implementation of ScanRequest, which lazily initialises its detectors field only
		// when a detector is added. If this fails in future because getDetectors() returns an empty map, this test
		// will need to be updated to match.
		assertThat(scanRequest.getDetectors(), is(nullValue()));
	}

	@Test
	public void testScanPathIsIncluded() {
		ScanBean scanBean = scanRequestConverter.convertToScanBean(experimentBean);
		ScanRequest<?> scanRequest = scanBean.getScanRequest();

		assertEquals(scanRequest.getCompoundModel().getModels().get(0), scanPath);
	}

	@Test
	public void testStageNamesAreSetCorrectly() {
		assertThat(scanPath.getFastAxisName(), is(not(equalTo(X_AXIS_NAME))));
		assertThat(scanPath.getSlowAxisName(), is(not(equalTo(Y_AXIS_NAME))));

		scanRequestConverter.convertToScanBean(experimentBean);

		assertThat(scanPath.getFastAxisName(), is(equalTo(X_AXIS_NAME)));
		assertThat(scanPath.getSlowAxisName(), is(equalTo(Y_AXIS_NAME)));
	}

	@Test
	public void testScriptFilesIncludedCorrectly() {
		IScriptFiles scriptFiles = new ScriptFiles();
		experimentBean.setScriptFiles(scriptFiles);
		scriptFiles.setBeforeScanScript("/tmp/before.py");
		scriptFiles.setAfterScanScript("/tmp/after.py");

		ScanBean scanBean = scanRequestConverter.convertToScanBean(experimentBean);

		ScanRequest<?> scanRequest = scanBean.getScanRequest();
		ScriptRequest beforeScriptReq = scanRequest.getBefore();
		assertThat(beforeScriptReq, is(notNullValue()));
		assertThat(beforeScriptReq.getLanguage(), is(SPEC_PASTICHE));
		assertThat(beforeScriptReq.getFile(), is(equalTo("/tmp/before.py")));
		ScriptRequest afterScriptReq = scanRequest.getAfter();
		assertThat(afterScriptReq, is(notNullValue()));
		assertThat(afterScriptReq.getLanguage(), is(SPEC_PASTICHE));
		assertThat(afterScriptReq.getFile(), is(equalTo("/tmp/after.py")));
	}

	@Test
	public void testBeamlineConfigurationIncludedCorrectly() {
		Map<String, Object> beamlineConfiguration = new HashMap<>();
		beamlineConfiguration.put("energy", 2675.3);
		beamlineConfiguration.put("attenuator_pos", "Gap");
		beamlineConfiguration.put("kb_mirror_pos", 7.0);
		experimentBean.setBeamlineConfiguration(beamlineConfiguration);

		ScanBean scanBean = scanRequestConverter.convertToScanBean(experimentBean);

		ScanRequest<?> scanRequest = scanBean.getScanRequest();
		IPosition startPos = scanRequest.getStart();
		assertThat(startPos.getNames().size(), is(3));
		assertThat(startPos.get("energy"), is(2675.3));
		assertThat(startPos.get("attenuator_pos"), is("Gap"));
		assertThat(startPos.get("kb_mirror_pos"), is(7.0));
	}

}
