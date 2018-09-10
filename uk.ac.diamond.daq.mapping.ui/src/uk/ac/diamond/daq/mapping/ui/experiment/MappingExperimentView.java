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

package uk.ac.diamond.daq.mapping.ui.experiment;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import uk.ac.diamond.daq.mapping.api.IMappingExperimentBeanProvider;

/**
 * Subclasses {#link uk.ac.diamond.daq.mapping.ui.experiment.AbstractSectionsView} to show the standard set of views
 * required for a mapping scan.
 */
public class MappingExperimentView extends AbstractSectionsView {

	@Inject
	public MappingExperimentView(IMappingExperimentBeanProvider beanProvider) {
		super(beanProvider);
	}

	@Override
	protected List<Class<? extends AbstractMappingSection>> getScrolledSectionClasses() {
		return Arrays.asList(
			// a section for configuring scannables to be moved to a particular position at the start of a scan
			BeamlineConfigurationSection.class,
			// a section for configuring scripts to be run before and after a scan
			ScriptFilesSection.class,
			// a section for configuring outer scannables (i.e. in addition to the inner map)
			OuterScannablesSection.class,
			// a section for choosing the detectors (or malcolm device) to include in the scan
			DetectorsSection.class,
			// a section for configuring the path of the mapping scan
			RegionAndPathSection.class,
			// a section for configuring metadata to add to the scan
			ScanMetadataSection.class,
			// a section for configuring live processing to run
			ProcessingSection.class);
	}

	@Override
	protected List<Class<? extends AbstractMappingSection>> getUnscrolledSectionClasses() {
		return Arrays.asList(
			// a section for submitting the scan to the queue
			SubmitScanSection.class);
	}
}
