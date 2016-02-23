/*-
 * Copyright © 2011 Diamond Light Source Ltd.
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

package org.opengda.lde.ui.viewfactories;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.opengda.lde.ui.views.DataCollectionStatus;
import org.opengda.lde.utils.LDEResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.rcp.views.FindableExecutableExtension;

/**
 * Factory method that create the View object
 */
public class DataCollectionStatusFactory implements FindableExecutableExtension {

	private final Logger logger = LoggerFactory.getLogger(DataCollectionStatusFactory.class);
	private String viewPartName;
	private String name;
	private LDEResourceUtil resUtil;
	private String eventAdminName;

	public String getViewPartName() {
		return viewPartName;
	}

	public void setViewPartName(String viewPartName) {
		this.viewPartName = viewPartName;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Object create() throws CoreException {
		logger.info("Creating children table view");
		DataCollectionStatus dataCollectionStatusView = new DataCollectionStatus();
		dataCollectionStatusView.setViewPartName(viewPartName);
		dataCollectionStatusView.setResUtil(resUtil);
		if (getEventAdminName()!=null) {
			dataCollectionStatusView.setEventAdminName(eventAdminName);
		}
		return dataCollectionStatusView;
	}

	@Override
	public void setInitializationData(IConfigurationElement config,
			String propertyName, Object data) throws CoreException {
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (resUtil==null) {
			throw new IllegalStateException("LDE resource util must NOT be null.");
		}
	}

	public LDEResourceUtil getResUtil() {
		return resUtil;
	}

	public void setResUtil(LDEResourceUtil resUtil) {
		this.resUtil = resUtil;
	}

	public String getEventAdminName() {
		return eventAdminName;
	}

	public void setEventAdminName(String eventAdminName) {
		this.eventAdminName = eventAdminName;
	}
}
