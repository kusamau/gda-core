/*-
 * Copyright © 2013 Diamond Light Source Ltd.
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

package uk.ac.gda.client;

import gda.device.Device;
import gda.factory.Configurable;
import gda.factory.FactoryException;
import gda.factory.Finder;
import gda.observable.IObserver;
import gda.rcp.GDAClientActivator;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.gda.preferences.PreferenceConstants;

public class ServerFileListener implements IObserver, Configurable {
	private static final Logger logger = LoggerFactory.getLogger(ServerFileListener.class);

	private IProject dataProject;

	private Device clientFileAnnouncer;
	private String clientFileAnnouncerName;

	private IFolder link;
	
	private RefreshJob refreshJob = new RefreshJob("data project refresh job");
	
	private class RefreshJob extends Job {

		public RefreshJob(String name) {
			super(name);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			try {
				if (link != null)
					link.refreshLocal(IResource.DEPTH_INFINITE, monitor);
				Thread.sleep(1000);
			} catch (Throwable e) {
				logger.error("error in the refresh job", e);
			}
			return Status.OK_STATUS;
		}
		
	}
	
	@Override
	public void configure() throws FactoryException {
		findDataProject();
		if (clientFileAnnouncer == null) {
			clientFileAnnouncer = Finder.getInstance().find(clientFileAnnouncerName);
		}
		clientFileAnnouncer.addIObserver(this);
	}

	public void findDataProject() {
		IPreferenceStore preferenceStore = GDAClientActivator.getDefault().getPreferenceStore();
		
		String projName = preferenceStore.getString(PreferenceConstants.GDA_DATA_PROJECT_NAME);
		
		dataProject = null; 
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			if (projects[i].getName().equals(projName)) {
				dataProject = projects[i];
				break;
			}
		}
	}
	
	@Override
	public void update(Object source, Object arg) {
		if (dataProject == null) {
			findDataProject();
			if (dataProject == null)
				return;
		}
		if (!(arg instanceof String[]))
			return;

		link = dataProject.getFolder("data");
		// as you can see below I tried only updating what is needed, but due to linked resources (I think)
		// (for now) only the full refresh seems to work, but at least we've put this in a jobs now
		refreshJob.schedule(500);
		
//		String[] files = (String[]) arg;
//		for (String path : files) {
//			IFile file = dataProject.getFile(path);
//			boolean exists = file.exists();
//			boolean linked = file.isLinked(IResource.CHECK_ANCESTORS);
//			IProject project = file.getProject();
//			IContainer parent = file.getParent();

//			try {
////				file.create(null, false, null);
////				file.refreshLocal(IResource.DEPTH_ZERO, null);
////				link.refreshLocal(IResource.DEPTH_INFINITE, null);
////				parent.refreshLocal(IResource.DEPTH_ZERO, null);
//
//			} catch (CoreException e) {
//				// TODO Auto-generated catch block
////				logger.error("TODO put description of error here", e);
//			}
//		}
	}

	public Device getClientFileAnnouncer() {
		return clientFileAnnouncer;
	}

	public void setClientFileAnnouncer(Device clientFileAnnouncer) {
		this.clientFileAnnouncer = clientFileAnnouncer;
	}

	public String getClientFileAnnouncerName() {
		return clientFileAnnouncerName;
	}

	public void setClientFileAnnouncerName(String clientFileAnnouncerName) {
		this.clientFileAnnouncerName = clientFileAnnouncerName;
	}
}