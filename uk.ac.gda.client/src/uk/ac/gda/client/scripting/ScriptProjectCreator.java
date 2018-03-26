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

package uk.ac.gda.client.scripting;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.python.copiedfromeclipsesrc.JavaVmLocationFinder;
import org.python.pydev.core.IInterpreterInfo;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.editor.codecompletion.revisited.ModulesManagerWithBuild;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.plugin.nature.PythonNature;
import org.python.pydev.runners.SimpleJythonRunner;
import org.python.pydev.shared_core.io.FileUtils;
import org.python.pydev.shared_core.structure.Tuple;
import org.python.pydev.ui.interpreters.JythonInterpreterManager;
import org.python.pydev.ui.pythonpathconf.InterpreterInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.configuration.properties.LocalProperties;
import gda.jython.JythonServerFacade;
import gda.rcp.GDAClientActivator;
import uk.ac.gda.common.rcp.util.BundleUtils;
import uk.ac.gda.jython.PydevConstants;
import uk.ac.gda.ui.utils.ProjectUtils;

/**
 * Class creates/removes script projects and XML projects if required to by preferences. Also can automatically create a Jython Interpreter
 * and assign PyDev nature to the script projects.
 */
public class ScriptProjectCreator {

	private static final Logger logger = LoggerFactory.getLogger(ScriptProjectCreator.class);
	private static Map<String, IProject> pathProjectMap = new HashMap<String, IProject>();

	private static String getProjectNameXMLConfig() {
		return getProjectName("gda.scripts.user.xml.project.name", "XML - Config");
	}

	private static String getProjectName(final String property, final String defaultValue) {
		String projectName = LocalProperties.get(property);
		if (projectName == null)
			projectName = defaultValue;
		return projectName;
	}

	public static void handleShowXMLConfig(IProgressMonitor monitor) throws CoreException {
		final IPreferenceStore store = GDAClientActivator.getDefault().getPreferenceStore();
		if (store.getBoolean(PreferenceConstants.SHOW_XML_CONFIG)) {
			ProjectUtils.createImportProjectAndFolder(getProjectNameXMLConfig(), "src",
					LocalProperties.get(LocalProperties.GDA_CONFIG) + "/xml", null, null, monitor);
		} else {
			final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			final IProject project = root.getProject(getProjectNameXMLConfig());
			if (project.exists()) {
				// exists so delete
				project.delete(false, true, monitor);
			}
		}

	}

	/**
	 * We programmatically create a Jython Interpreter so that the user does not have to.
	 */
	private static void createInterpreter(IProgressMonitor monitor) throws Exception {

		final IPreferenceStore preferenceStore = GDAClientActivator.getDefault().getPreferenceStore();

		// Horrible Hack warning: This code is copied from parts of Pydev to set up the interpreter and save it.
		// Code copies from Pydev when the user chooses a Jython interpreter - these are the defaults.

		final Path interpreterPath = Paths.get(BundleUtils.getBundleLocation("uk.ac.diamond.jython").getAbsolutePath(),
				"jython2.7");
		final String executable = interpreterPath.resolve("jython.jar").toString();
		if (!(new File(executable)).exists())
			throw new Exception("Jython jar not found  :" + executable);

		final File script = PydevPlugin.getScriptWithinPySrc("interpreterInfo.py");
		if (!script.exists()) {
			throw new RuntimeException("The file specified does not exist: " + script);
		}
		monitor.subTask("Creating interpreter");
		// gets the info for the python side
		String encoding = null;
		Tuple<String, String> outTup = new SimpleJythonRunner().runAndGetOutputWithJar(
				FileUtils.getFileAbsolutePath(script), executable, null, null, null, monitor, encoding);

		InterpreterInfo info = null;
		try {
			// HACK Otherwise Pydev shows a dialog to the user.
			ModulesManagerWithBuild.IN_TESTS = true;
			info = InterpreterInfo.fromString(outTup.o1, false);
		} catch (Exception e) {
			logger.error("Something went wrong creating the InterpreterInfo.", e);
		} finally {
			ModulesManagerWithBuild.IN_TESTS = false;
		}

		if (info == null) {
			// cancelled
			return;
		}
		// the executable is the jar itself
		info.executableOrJar = executable;

		// we have to find the jars before we restore the compiled libs
		if (preferenceStore.getBoolean(PreferenceConstants.GDA_PYDEV_ADD_DEFAULT_JAVA_JARS)) {
			List<File> jars = JavaVmLocationFinder.findDefaultJavaJars();
			for (File jar : jars) {
				info.libs.add(FileUtils.getFileAbsolutePath(jar));
			}
		}

		// Defines all third party libs that can be used in scripts.
		if (preferenceStore.getBoolean(PreferenceConstants.GDA_PYDEV_ADD_GDA_LIBS_JARS)) {
			final List<String> gdaJars = LibsLocationFinder.findGdaLibs();
			info.libs.addAll(gdaJars);
		}

		// Defines gda classes which can be used in scripts.
		final String gdaInterfacePath = LibsLocationFinder.findGdaInterface();
		if (gdaInterfacePath != null) {
			info.libs.add(gdaInterfacePath);
		}

		List<String> allScriptProjectFolders = JythonServerFacade.getInstance().getAllScriptProjectFolders();
		for (String s : allScriptProjectFolders) {
			info.libs.add(s);
		}

		// java, java.lang, etc should be found now
		info.restoreCompiledLibs(monitor);
		info.setName(PydevConstants.INTERPRETER_NAME);

		final JythonInterpreterManager man = (JythonInterpreterManager) PydevPlugin.getJythonInterpreterManager();
		HashSet<String> set = new HashSet<String>();
		set.add(PydevConstants.INTERPRETER_NAME);
		man.setInfos(new IInterpreterInfo[] { info }, set, monitor);

		logger.info("Jython interpreter registered: " + PydevConstants.INTERPRETER_NAME);
	}


	/**
	 * Base method of this class for setting up script projects
	 *  which calls other class methods to: create a Jython interpreter if preferences are set,
	 * create script projects in the client, manage which projects are shown (created) based on preferences,
	 * add PyDev nature to these projects and add script projects to working set
	 */
	public static void createProjects(IProgressMonitor monitor) throws Exception {
		monitor.subTask("Checking existence of projects");
		final IPreferenceStore store = GDAClientActivator.getDefault().getPreferenceStore();
		boolean chkGDASyntax = store.getBoolean(PreferenceConstants.CHECK_SCRIPT_SYNTAX);


		//The behaviour of the property: gda.client.jython.automatic.interpreter is to prevent auto interpreter set up
		//if set to anything. It is not set by default
		if (chkGDASyntax && System.getProperty("gda.client.jython.automatic.interpreter") == null) {
			monitor.subTask("Checking if interpreter already exists");
			if (isInterpreterCreationRequired(monitor))
				createInterpreter(monitor);
		}

		List<IAdaptable> scriptProjects = new ArrayList<IAdaptable>();

		for (String path : JythonServerFacade.getInstance().getAllScriptProjectFolders()) {
			String projectName = JythonServerFacade.getInstance().getProjectNameForPath(path);
			boolean shouldShowProject = checkShowProjectPref(path, store);
			if (shouldShowProject) {
				final IProject newProject = createJythonProject(projectName, path, chkGDASyntax, monitor);
				scriptProjects.add(newProject);
				pathProjectMap.put(path, newProject);
			} else {
				final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
				final IProject project = root.getProject(projectName);
				if (project.exists()) {
					// exists so delete rather than hide for efficiency reasons
					try {
						project.delete(false, true, monitor);
					} catch (CoreException e) {
						logger.warn("Error deleting project " + projectName, e);
					}
				}
			}
		}

		IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
		IWorkingSet workingSet = workingSetManager.getWorkingSet("Scripts");
		if (workingSet == null) {
			monitor.subTask("Adding Scripts working set");
			workingSetManager.addWorkingSet(
					workingSetManager.createWorkingSet("Scripts", scriptProjects.toArray(new IAdaptable[] {})));
		} else {
			for (IAdaptable element : scriptProjects) {
				workingSetManager.addToWorkingSets(element, new IWorkingSet[] { workingSet });
			}
		}
	}

	/**
	 * Checks GDA script preferences to see if the project should be shown or hidden in the workspace
	 */
	private static boolean checkShowProjectPref(String path, IPreferenceStore store) throws RuntimeException {
		if (JythonServerFacade.getInstance().projectIsUserType(path)) {
			return true;
		}
		if (JythonServerFacade.getInstance().projectIsConfigType(path)) {
			return store.getBoolean(PreferenceConstants.SHOW_CONFIG_SCRIPTS);
		}
		if (JythonServerFacade.getInstance().projectIsCoreType(path)) {
			return store.getBoolean(PreferenceConstants.SHOW_GDA_SCRIPTS);
		}
		throw new RuntimeException("Unknown type of Jython Script Project: " + path + " = "
				+ JythonServerFacade.getInstance().getProjectNameForPath(path));
	}

	/**
	 * Uses ProjectUtils to create the script project in the workspace and also adds PyDev specific (.pydevproject) and
	 * PyDev Eclipse nature (in .project) when chkGDASyntax is true. Otherwise removes just the nature from .project
	 */
	private static IProject createJythonProject(final String projectName, final String importFolder,
			final boolean chkGDASyntax, IProgressMonitor monitor) throws CoreException {

		IProject project = ProjectUtils.createImportProjectAndFolder(projectName, "src", importFolder, null, null,
				monitor);
		boolean hasPythonNature = PythonNature.getPythonNature(project) != null;

		if (chkGDASyntax) {
			if (!hasPythonNature) {
				// Assumes that the interpreter named PydevConstants.INTERPRETER_NAME has been created.
				// NOTE Very important to start the name with a '/'
				// or pydev creates the wrong kind of nature.
				PythonNature.addNature(project, monitor, IPythonNature.JYTHON_VERSION_2_7,
						"/" + project.getName() + "/src", null, PydevConstants.INTERPRETER_NAME, null);
			}
		} else {
			if (hasPythonNature) {
				// This should do the same as removing the Pydev config but neither
				// prevent it being added when a python file is edited.
				PythonNature.removeNature(project, monitor);
				// This method removes just the nature in .project
			}
		}
		return project;
	}

	/**
	 * The method PydevPlugin.getJythonInterpreterManager().getInterpreterInfo(...) can never return in some
	 * circumstances because of a bug in pydev. Therefore this is dealt with in InterpreterThread.
	 *
	 * @return true if new interpreter required
	 */
	private static boolean isInterpreterCreationRequired(final IProgressMonitor monitor) {

		final InterpreterThread checkInterpreter = new InterpreterThread(monitor);
		checkInterpreter.start();

		int totalTimeWaited = 0;
		while (!checkInterpreter.isFinishedChecking()) {
			try {
				if (totalTimeWaited > 4000) {
					logger.error("Unable to call getInterpreterInfo() method on pydev, assuming interpreter is already created.");
					return false;
				}
				Thread.sleep(100);
				totalTimeWaited += 100;
			} catch (InterruptedException ne) {
				break;
			}
		}
		return !checkInterpreter.isInterpreter();
	}

	/**
	 *Thread to check if the desired interpreter already exists within PyDev. Will throw an error in logs if it doesn't.
	 */
	private static class InterpreterThread extends Thread {
		private static final Logger logger = LoggerFactory.getLogger(InterpreterThread.class);

		private IInterpreterInfo info = null;
		private IProgressMonitor monitor;
		private boolean finishedCheck = false;

		private InterpreterThread(final IProgressMonitor monitor) {
			super("Interpreter Info");
			setDaemon(true);// This is not that important
			this.monitor = monitor;
		}

		@Override
		public void run() {
			// Might never return...
			try {
				//Attempts to get interpreter info for a Jython interpreter called INTERPRETER_NAME (doesn't actually check version)
				info = PydevPlugin.getJythonInterpreterManager().getInterpreterInfo(PydevConstants.INTERPRETER_NAME,
						monitor);
			} catch (MisconfigurationException e) {
				logger.error("Jython is not configured properly", e);
			}
			finishedCheck = true;
		}

		/**
		 * Check if Jython interpreter called INTERPRETER_NAME exists
		 * @return true if it does exist
		 */
		private boolean isInterpreter() {
			return info != null;
		}

		/**
		 * Used to confirm to main thread that a check for existing interpreter actually completed
		 * @return true when run method completed
		 */
		private boolean isFinishedChecking() {
			return finishedCheck;
		}

	}
}
