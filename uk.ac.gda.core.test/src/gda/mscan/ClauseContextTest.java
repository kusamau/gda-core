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

package gda.mscan;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import gda.device.detector.DummyDetector;
import gda.device.monitor.DummyMonitor;
import gda.device.scannable.ScannableMotor;
import gda.mscan.element.AreaScanpath;
import gda.mscan.element.Mutator;
import gda.mscan.element.Roi;


@RunWith(MockitoJUnitRunner.class)
public class ClauseContextTest {

	@Mock
	private ScannableMotor scannable;

	@Mock
	private DummyDetector detector;

	@Mock
	private DummyMonitor monitor;

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private ClauseContext clauseContext;
	private ClauseContext unvalidatedClauseContext;    // For test cases where we want to use the getters before
	                                                   // completing the whole context to confirm metadata was set

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		clauseContext = new ClauseContext();
		unvalidatedClauseContext = new ClauseContext().withoutValidation();
	}

	/**
	 * Test methods for {@link gda.mscan.ClauseContext#addScannable(gda.device.Scannable)}.
	 */

	@Test
	public void scannableToAddMustNotBeNull() {
		expectIllegalArgumentWithMessageContents("The supplied Scannable was null");
		clauseContext.addScannable(null);
	}

	@Test
	public void cannotAddDetectorsToClause() {
		expectUnsupportedOperationWithMessageContents("DummyDetector", "cannot be present");
		clauseContext.addScannable(detector);
	}

	@Test
	public void cannotAddMonitorsToClause() {
		expectUnsupportedOperationWithMessageContents("DummyMonitor", "cannot be present");
		clauseContext.addScannable(monitor);
	}

	@Test
	public void cannotAddMoreThanMaximumScannablesPerClause() throws Exception {
		expectUnsupportedOperationWithMessageContents("Too many scannables in scan clause");
		unvalidatedClauseContext.addScannable(scannable);
		assertThat(unvalidatedClauseContext.getScannables().size(), is(1));
		unvalidatedClauseContext.addScannable(scannable);
		assertThat(unvalidatedClauseContext.getScannables().size(), is(2));
		unvalidatedClauseContext.addScannable(scannable);
	}

	@Test
	public void addScannableSetsMetadataAndStoresScannable() throws Exception {
		assertThat(unvalidatedClauseContext.addScannable(scannable), is(true));
		assertThat(unvalidatedClauseContext.getScannables(), contains(scannable));
		assertThat(unvalidatedClauseContext.getScannables().size(), is(1));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("gda.device.Scannable"));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertNull(unvalidatedClauseContext.paramsToFill());
	}

	/**
	 * Test methods for {@link gda.mscan.ClauseContext#setRoi(gda.mscan.element.Roi)}.
	 */

	@Test
	public void roiToSetMustNotBeNull() {
		expectIllegalArgumentWithMessageContents("The supplied Roi was null");
		prepareForRoiTest(clauseContext);
		clauseContext.setRoi(null);
	}

	@Test
	public void roiCannotBeSetIfNoScannables() {
		expectUnsupportedOperationWithMessageContents("requires 2 scannables");
		clauseContext.setRoi(Roi.RECTANGLE);
	}

	@Test
	public void roiCannotBeSetIfOnlyOneScannable() {
		expectUnsupportedOperationWithMessageContents("requires 2 scannables");
		clauseContext.addScannable(scannable);
		clauseContext.setRoi(null);
	}

	@Test
	public void roiCannotBeSetTwiceBecauseRoiParamsWillHaveBeenInitialised() {
		expectIllegalStateWithMessageContents("Roi must be the specified before any parameters");
		prepareForRoiTest(clauseContext);
		clauseContext.setRoi(Roi.RECTANGLE);
		clauseContext.setRoi(Roi.CIRCLE);
	}

	@Test
	public void roiCannotBeSetIfAreaScanpathAlreadyHasBeen() {
		expectUnsupportedOperationWithMessageContents("Roi must be set before AreaScanpath");
		prepareForRoiTest(clauseContext);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
		clauseContext.setRoi(Roi.CIRCLE);
	}

	@Test
	public void setRoiSetsMetadataAndRoiIfScannablesCorrectlySet() throws Exception {
		prepareForRoiTest(unvalidatedClauseContext);
		unvalidatedClauseContext.setRoi(Roi.CIRCLE);
		assertThat(unvalidatedClauseContext.getRoi(), is(Roi.CIRCLE));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("gda.mscan.element.Roi"));
		assertThat(unvalidatedClauseContext.paramsToFill(), is(unvalidatedClauseContext.getRoiParams()));
		assertThat(unvalidatedClauseContext.getRequiredParamCount(), is(3));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getParamCount(), is(0));
	}

	/**
	 * Test methods for {@link gda.mscan.ClauseContext#setAreaScanpath(gda.mscan.element.AreaScanpath)}.
	 */

	@Test
	public void areaScanpathToSetMustNotBeNull() {
		expectIllegalArgumentWithMessageContents("The supplied AreaScanpath was null");
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.setAreaScanpath(null);
	}

	@Test
	public void areaScanpathCannotBeSetIfNoScannables() {
		expectUnsupportedOperationWithMessageContents("requires 2 scannables");
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
	}

	@Test
	public void areaScanpathCannotBeSetIfOnlyOneScannable() {
		expectUnsupportedOperationWithMessageContents("requires 2 scannables");
		clauseContext.addScannable(scannable);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
	}

	@Test
	public void areaScanpathCannotBeSetTwice() {
		expectUnsupportedOperationWithMessageContents("AreaScanpath already set");
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
		clauseContext.setAreaScanpath(AreaScanpath.RASTER);
	}

	@Test
	public void setAreaScanpathSetsMetadataAndStoresScanpathIfScannablesAndRoiCorrectlySet() throws Exception {
		prepareForAreaScanpathTest(unvalidatedClauseContext);
		unvalidatedClauseContext.setAreaScanpath(AreaScanpath.SPIRAL);
		assertThat(unvalidatedClauseContext.getAreaScanpath(), is(AreaScanpath.SPIRAL));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("gda.mscan.element.AreaScanpath"));
		assertThat(unvalidatedClauseContext.paramsToFill(), is(unvalidatedClauseContext.getPathParams()));
		assertThat(unvalidatedClauseContext.getRequiredParamCount(), is(1));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getParamCount(), is(0));
	}

	/**
	 * Test methods for {@link gda.mscan.ClauseContext#addMutator(gda.mscan.element.Mutator)}.
	 */

	@Test
	public void mutatorToAddMustNotBeNull() {
		expectIllegalArgumentWithMessageContents("The supplied Mutator was null");
		clauseContext.addMutator(null);
	}

	@Test
	public void addMutatorSetsMetadataAndStoresSnakeMutatorCorrectlyForGrid() throws Exception {
		prepareForMutatorTest(unvalidatedClauseContext, AreaScanpath.GRID, 3, 3);
		unvalidatedClauseContext.addMutator(Mutator.SNAKE);
		assertThat(unvalidatedClauseContext.getMutatorUses(), hasEntry(Mutator.SNAKE, new ArrayList<Number>()));
		assertThat(unvalidatedClauseContext.getMutatorUses().size(), is(1));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("gda.mscan.element.Mutator"));
		assertThat(unvalidatedClauseContext.paramsFull(), is(true));
		assertNotNull(unvalidatedClauseContext.paramsToFill());
	}

	@Test
	public void addMutatorSetsMetadataAndStoresSnakeMutatorCorrectlyForRaster() throws Exception {
		prepareForMutatorTest(unvalidatedClauseContext, AreaScanpath.RASTER, 3, 3);
		unvalidatedClauseContext.addMutator(Mutator.SNAKE);
		assertThat(unvalidatedClauseContext.getMutatorUses(), hasEntry(Mutator.SNAKE, new ArrayList<Number>()));
		assertThat(unvalidatedClauseContext.getMutatorUses().size(), is(1));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("gda.mscan.element.Mutator"));
		assertThat(unvalidatedClauseContext.paramsFull(), is(true));
		assertNotNull(unvalidatedClauseContext.paramsToFill());
	}

	@Test
	public void addMutatorRejectsSnakeMutatorForSpiral() throws Exception {
		expectUnsupportedOperationWithMessageContents("Snake may only be applied");
		prepareForMutatorTest(clauseContext, AreaScanpath.SPIRAL, 1);
		clauseContext.addMutator(Mutator.SNAKE);
	}

	@Test
	public void addMutatorRejectsSnakeMutatorForLissajous() throws Exception {
		expectUnsupportedOperationWithMessageContents("Snake may only be applied");
		prepareForMutatorTest(clauseContext, AreaScanpath.LISSAJOUS, 1, 2, 3, 4, 5);
		clauseContext.addMutator(Mutator.SNAKE);
	}

	@Test
	public void addMutatorRejectsParametersForSnakeMutator() throws Exception {
		expectIllegalStateWithMessageContents("Too many parameters");
		prepareForMutatorTest(clauseContext);
		clauseContext.addMutator(Mutator.SNAKE);
		clauseContext.addParam(2);
	}

	@Test
	public void addMutatorSetsMetadataAndStoresRandomOffsetGridMutatorCorrectlyForGrid() throws Exception {
		prepareForMutatorTest(unvalidatedClauseContext);
		unvalidatedClauseContext.addMutator(Mutator.RANDOM_OFFSET);
		unvalidatedClauseContext.addParam(20);
		unvalidatedClauseContext.addParam(2);
		assertThat(unvalidatedClauseContext.getMutatorUses(), hasKey(Mutator.RANDOM_OFFSET));
		assertThat(unvalidatedClauseContext.getMutatorUses().get(Mutator.RANDOM_OFFSET), contains(20, 2));
		assertThat(unvalidatedClauseContext.getMutatorUses().size(), is(1));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("java.lang.Number"));
		assertThat(unvalidatedClauseContext.paramsFull(), is(true));
		assertNotNull(unvalidatedClauseContext.paramsToFill());
	}

	@Test
	public void addMutatorSetsMetadataAndStoresRandomOffsetGridMutatorCorrectlyForMinimumNumberOfParameters() throws Exception {
		prepareForMutatorTest(unvalidatedClauseContext);
		unvalidatedClauseContext.addMutator(Mutator.RANDOM_OFFSET);
		unvalidatedClauseContext.addParam(20);
		assertThat(unvalidatedClauseContext.getMutatorUses(), hasKey(Mutator.RANDOM_OFFSET));
		assertThat(unvalidatedClauseContext.getMutatorUses().get(Mutator.RANDOM_OFFSET), contains(20));
		assertThat(unvalidatedClauseContext.getMutatorUses().size(), is(1));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("java.lang.Number"));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertNotNull(unvalidatedClauseContext.paramsToFill());
	}

	@Test
	public void addMutatorRejectsTooManyParametersForRandomOffsetGridMutator() throws Exception {
		expectIllegalStateWithMessageContents("Too many parameters");
		prepareForMutatorTest(unvalidatedClauseContext);
		unvalidatedClauseContext.addMutator(Mutator.RANDOM_OFFSET);
		unvalidatedClauseContext.addParam(20);
		unvalidatedClauseContext.addParam(2);
		unvalidatedClauseContext.addParam(2);
	}

	@Test
	public void addMutatorRejectsNegativeValuesOfPercentageOffsetForRandomOffsetGridMutator() throws Exception {
		expectIllegalArgumentWithMessageContents("must be positive");
		prepareForMutatorTest(unvalidatedClauseContext);
		unvalidatedClauseContext.addMutator(Mutator.RANDOM_OFFSET);
		unvalidatedClauseContext.addParam(-20);
	}

	@Test
	public void addMutatorRejectsTooFewParametersForRandomOffsetGridMutator() throws Exception {
		expectUnsupportedOperationWithMessageContents("Too few parameters");
		prepareForMutatorTest(clauseContext);
		clauseContext.addMutator(Mutator.RANDOM_OFFSET);
		clauseContext.validateAndAdjust();
	}

	@Test
	public void addMutatorRejectsRandomOffsetGridMutatorForRaster() throws Exception {
		expectUnsupportedOperationWithMessageContents("Random offsets may only be applied");
		prepareForMutatorTest(clauseContext, AreaScanpath.RASTER, 1);
		clauseContext.addMutator(Mutator.RANDOM_OFFSET);
	}

	@Test
	public void addMutatorRejectsRandomOffsetGridMutatorForSpiral() throws Exception {
		expectUnsupportedOperationWithMessageContents("Random offsets may only be applied");
		prepareForMutatorTest(clauseContext, AreaScanpath.SPIRAL, 1);
		clauseContext.addMutator(Mutator.RANDOM_OFFSET);
	}

	@Test
	public void addMutatorRejectsRandomOffsetGridMutatorForLissajous() throws Exception {
		expectUnsupportedOperationWithMessageContents("Random offsets may only be applied");
		prepareForMutatorTest(clauseContext, AreaScanpath.LISSAJOUS, 1, 2, 3, 4, 5);
		clauseContext.addMutator(Mutator.RANDOM_OFFSET);
	}

	/**
	 * Test method for {@link gda.mscan.ClauseContext#addParam(java.lang.Number)}.
	 */

	@Test
	public void paramToAddMustNotBeNull() {
		prepareForNumericParamTest(clauseContext, Roi.CENTRED_RECTANGLE);
		expectIllegalArgumentWithMessageContents("The supplied Number was null");
		clauseContext.addParam(null);
	}

	@Test
	public void cannotAddParamsIfNoScannables() throws Exception {
		expectIllegalStateWithMessageContents("at least 1 scannable");
		clauseContext.addParam(2);
	}

	@Test
	public void roiIsBeDefaultedIfAddParamsWhenNoRoiSetButScannablesAdded() throws Exception {
		prepareForRoiTest(unvalidatedClauseContext);
		assertThat(unvalidatedClauseContext.addParam(2), is(true));
		assertThat(unvalidatedClauseContext.getRoi(), is(Roi.defaultValue()));
		assertThat(unvalidatedClauseContext.getRoiParams(), contains(2));
		assertThat(unvalidatedClauseContext.getParamCount(), is(1));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("java.lang.Number"));
	}

	@Test
	public void areaScanpathIsDefaultedIfAddParamsWhenRoiAndItsParamsSetAndScannablesAdded() throws Exception {
		prepareForAreaScanpathTest(unvalidatedClauseContext);
		assertThat(unvalidatedClauseContext.addParam(2), is(true));
		assertThat(unvalidatedClauseContext.getAreaScanpath(), is(AreaScanpath.defaultValue()));
		assertThat(unvalidatedClauseContext.getPathParams(), contains(2));
		assertThat(unvalidatedClauseContext.getParamCount(), is(1));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("java.lang.Number"));
	}

	@Test
	public void cannotSetAreaScanpathOnceDefaultedBecauseItsParamsWillHaveStartedToFill() throws Exception {
		expectIllegalStateWithMessageContents("specified before its parameters");
		prepareForAreaScanpathTest(clauseContext);
		assertThat(clauseContext.addParam(2), is(true));
		clauseContext.setAreaScanpath(AreaScanpath.SPIRAL);
	}

	@Test
	public void addParamUpdatesMetadataAndStoresParam() throws Exception {
		prepareForNumericParamTest(unvalidatedClauseContext, Roi.RECTANGLE);
		assertThat(unvalidatedClauseContext.addParam(5), is(true));
		assertThat(unvalidatedClauseContext.getRoiParams(), contains(5));
		assertThat(unvalidatedClauseContext.getParamCount(), is(1));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getPreviousType().getTypeName(), is("java.lang.Number"));
	}

	/**
	 * Test method for {@link gda.mscan.ClauseContext#paramsFull()}.+
	 */
	@Test
	public void boundedRoiParamsListCannotBeOverfilledBecauseWillDefaultAreaScanpath() throws Exception {
		prepareForNumericParamTest(unvalidatedClauseContext, Roi.CIRCLE);
		assertThat(unvalidatedClauseContext.getRequiredParamCount(), is(3));
		assertThat(unvalidatedClauseContext.addParam(0), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(3), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(4), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(true));
		assertThat(unvalidatedClauseContext.addParam(6), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getPathParams(), contains(6));
		assertThat(unvalidatedClauseContext.getPathParams().size(), is(1));
		assertThat(unvalidatedClauseContext.getAreaScanpath(), is(AreaScanpath.defaultValue()));
	}

	@Test(expected = NoSuchElementException.class)
	public void unboundedRoiParamsListCannotBeOverfilled() throws Exception {
		prepareForNumericParamTest(unvalidatedClauseContext, Roi.POLYGON);
		assertThat(unvalidatedClauseContext.getRequiredParamCount(), is(6));
		assertThat(unvalidatedClauseContext.addParam(0), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(3), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(4), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(6), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(7), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(4), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(6), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.addParam(7), is(true));
		assertThat(unvalidatedClauseContext.paramsFull(), is(false));
		assertThat(unvalidatedClauseContext.getPathParams().size(), is(0));
		unvalidatedClauseContext.getAreaScanpath();
	}

	@Test
	public void paramsForAreaScanpathWithDefaultedAreaScanpathCannotBeFilledBeyondTheBound() throws Exception {
		expectIllegalStateWithMessageContents("has already been supplied");
		prepareForAreaScanpathTest(clauseContext);
		assertThat(clauseContext.addParam(0), is(true));
		assertThat(clauseContext.paramsFull(), is(false));
		assertThat(clauseContext.addParam(3), is(true));
		assertThat(clauseContext.paramsFull(), is(true));
		clauseContext.addParam(7);
	}
	@Test
	public void paramsForAreaScanpathWithSetAreaScanpathCannotBeFilledBeyondTheBound() throws Exception {
		expectIllegalStateWithMessageContents("has already been supplied");
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.setAreaScanpath(AreaScanpath.SPIRAL);
		assertThat(clauseContext.addParam(3), is(true));
		assertThat(clauseContext.paramsFull(), is(true));
		clauseContext.addParam(7);
	}

	@Test
	public void contextValuesCannotBeUsedIfNotValidated() throws Exception {
		expectUnsupportedOperationWithMessageContents("must be validated");
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.setAreaScanpath(AreaScanpath.SPIRAL);
		clauseContext.addParam(1);
		clauseContext.getScannables();
	}

	// Post completion validation tests

	@Test
	public void validateRejectsContextsWithoutScannables() throws Exception {
		expectIllegalStateWithMessageContents("required number of Scannables");
		clauseContext.validateAndAdjust();
	}

	@Test
	public void validateRejectsContextsWithoutBothRoiAndAreaScanpath() throws Exception {
		expectIllegalStateWithMessageContents("both Roi and AreaScanpath");
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.validateAndAdjust();
	}

	@Test
	public void validateRejectsContextsWithTooFewAreaScanpathParams() throws Exception {
		expectIllegalStateWithMessageContents("correct no of params");
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.addParam(3);
		clauseContext.validateAndAdjust();
	}

	@Test
	public void validateRejectsContextsWithTooFewAreaScanpathParamsForUnboundedRoi() throws Exception {
		expectIllegalStateWithMessageContents("correct no of params");
		prepareForNumericParamTest(clauseContext, Roi.POLYGON);
		clauseContext.addParam(3);
		clauseContext.addParam(4);
		clauseContext.addParam(5);
		clauseContext.addParam(6);
		clauseContext.addParam(7);
		clauseContext.addParam(8);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
		clauseContext.addParam(3);
		clauseContext.validateAndAdjust();
	}

	@Test
	public void validateRejectsOddNumberOfParamsForPolygonalRoi() throws Exception {
		expectIllegalStateWithMessageContents("even number of params");
		prepareForNumericParamTest(clauseContext, Roi.POLYGON);
		clauseContext.addParam(3);
		clauseContext.addParam(4);
		clauseContext.addParam(5);
		clauseContext.addParam(6);
		clauseContext.addParam(7);
		clauseContext.addParam(8);
		clauseContext.addParam(9);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		clauseContext.validateAndAdjust();
	}

	@Test
	public void correctlyFullyCompletedContextWithBoundedRoiValidates() {
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		assertThat(clauseContext.validateAndAdjust(), is(true));
	}

	@Test
	public void correctlyFullyCompletedContextWithUnboundedRoiValidates() {
		prepareForNumericParamTest(clauseContext, Roi.POLYGON);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		clauseContext.addParam(4);
		clauseContext.addParam(4);
		clauseContext.addParam(5);
		clauseContext.addParam(4);
		clauseContext.addParam(6);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		clauseContext.addParam(2);
		clauseContext.setAreaScanpath(AreaScanpath.SPIRAL);
		clauseContext.addParam(3);
		assertThat(clauseContext.validateAndAdjust(), is(true));
	}

	@Test
	public void correctlyCompletedContextWithDefaultedRoiValidates() {
		prepareForRoiTest(clauseContext);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		clauseContext.addParam(4);
		clauseContext.addParam(4);
		clauseContext.setAreaScanpath(AreaScanpath.GRID);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		assertThat(clauseContext.validateAndAdjust(), is(true));
	}

	@Test
	public void correctlyCompletedContextWithDefaultedAreaScanpathValidates() {
		prepareForAreaScanpathTest(clauseContext);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		assertThat(clauseContext.validateAndAdjust(), is(true));
	}

	@Test
	public void correctlyCompletedContextWithDefaultedRoiAndAreaScanpathValidates() {
		prepareForRoiTest(clauseContext);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		clauseContext.addParam(4);
		clauseContext.addParam(4);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		assertThat(clauseContext.validateAndAdjust(), is(true));
	}

	@Test
	public void defaultingOfAreaScanpathCannotBeDetectedWithUnboundedRoi() {
		expectIllegalStateWithMessageContents("both Roi and AreaScanpath");
		prepareForNumericParamTest(clauseContext, Roi.POLYGON);
		clauseContext.addParam(3);
		clauseContext.addParam(3);
		clauseContext.addParam(4);
		clauseContext.addParam(4);
		clauseContext.addParam(5);
		clauseContext.addParam(4);
		clauseContext.addParam(6);
		clauseContext.addParam(3);
		// and now lets attempt to default the AreaScanpath by not specifying it
		clauseContext.addParam(3);
		clauseContext.addParam(2);
		assertThat(clauseContext.validateAndAdjust(), is(true));
	}

	private void prepareForMutatorTest(final ClauseContext context) {
		prepareForMutatorTest(context, AreaScanpath.GRID, 3, 3);
	}

	private void prepareForMutatorTest(final ClauseContext context, final AreaScanpath path, final Number... params) {
		prepareForAreaScanpathTest(context);
		context.setAreaScanpath(path);
		for (Number param : params) {
			context.addParam(param);
		}
	}

	private void prepareForAreaScanpathTest(final ClauseContext context) {
		prepareForRoiTest(context);
		context.setRoi(Roi.CIRCLE);
		context.addParam(3);
		context.addParam(3);
		context.addParam(3);
	}

	private void prepareForRoiTest(final ClauseContext context) {
		context.addScannable(scannable);
		context.addScannable(scannable);
	}

	private void prepareForNumericParamTest(final ClauseContext context, Roi roi) {
		prepareForRoiTest(context);
		context.setRoi(roi);
	}

	private void expectUnsupportedOperationWithMessageContents(final String... substrings) {
		thrown.expect(UnsupportedOperationException.class);
		expectMessageContents(substrings);
	}
	private void expectIllegalStateWithMessageContents(final String... substrings) {
		thrown.expect(IllegalStateException.class);
		expectMessageContents(substrings);
	}

	private void expectIllegalArgumentWithMessageContents(final String... substrings) {
		thrown.expect(IllegalArgumentException.class);
		expectMessageContents(substrings);
	}

	private void expectMessageContents(final String... substrings) {
		for (String substring : substrings) {
			thrown.expectMessage(substring);
		}
	}
}
