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

package gda.rcp;

import gda.configuration.properties.LocalProperties;
import gda.data.PathConstructor;
import gda.data.metadata.VisitEntry;
import gda.data.metadata.icat.IcatProvider;
import gda.factory.FactoryException;
import gda.factory.Finder;
import gda.factory.ObjectFactory;
import gda.factory.corba.util.AdapterFactory;
import gda.factory.corba.util.NetService;
import gda.jython.InterfaceProvider;
import gda.jython.MockJythonServer;
import gda.jython.MockJythonServerFacade;
import gda.jython.authenticator.Authenticator;
import gda.jython.authenticator.UserAuthentication;
import gda.jython.authoriser.AuthoriserProvider;
import gda.rcp.util.UIScanDataPointEventService;
import gda.util.ElogEntry;
import gda.util.ObjectServer;
import gda.util.SpringObjectServer;
import gda.util.logging.LogbackUtils;

import java.io.File;
import java.net.URL;
import java.util.HashMap;

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.gda.ClientManager;
import uk.ac.gda.preferences.PreferenceConstants;
import uk.ac.gda.richbeans.BeansFactoryInit;
import uk.ac.gda.ui.dialog.AuthenticationDialog;
import uk.ac.gda.ui.dialog.GenericDialog;
import uk.ac.gda.ui.dialog.VisitIDDialog;

/**
 * This class controls all aspects of the application's execution. We are very similar to an IDEApplication, so some of
 * this code comes from there.
 */
public class Application implements IApplication {

	private static final Logger logger = LoggerFactory.getLogger(Application.class);

	private static final String PROP_EXIT_CODE = "eclipse.exitcode";

	@Override
	public Object start(IApplicationContext context) {

		Display display = PlatformUI.createDisplay();
		try {
			// NOTE: Please keep the methods called during startup in tidy order. New tests or configurations should be
			// encapsulated in their own method.
			final boolean localObjectsOnly = createLocalObjectsIfRequired();

			LogbackUtils.configureLoggingForClientProcess();
			
			authenticateUser(display);


			if(!localObjectsOnly ){
				//get access to distributed metadata object needed for identifying Visit
				ObjectFactory objectFactory = new ObjectFactory();
				objectFactory.setName(LocalProperties.get("gda.factory.factoryName"));
				Finder finder = Finder.getInstance();
				finder.addFactory(objectFactory);
				NetService netService = NetService.getInstance();
				// Add an adapter factory to the finder to allow access to
				// objects created elsewhere. eg. in a standalone object server.
				AdapterFactory adapterFactory = new AdapterFactory(objectFactory.getName(), netService);
				finder.addFactory(adapterFactory);
				objectFactory.configure();

			}
			
			
			if (identifyVisitID(display) == EXIT_OK) {
				return EXIT_OK;
			}

			//set workspace before creating items in call to createObjectFactory as the latter
			//may cause the accessing of preferences which is not possible until the workspace is set
			final String workspacePath = getWorkSpacePath(); 
			createVisitBasedWorkspace(workspacePath);

			createObjectFactory(display, localObjectsOnly);

			// To break the dependency of uk.ac.gda.common.BeansFactory of RCP/Eclipse, we 
			// manually force initialisation here. In the object server this is handled
			// by Spring, in Eclipse we use the registry
			try {
				BeansFactoryInit.initBeansFactory();
			} catch (Exception e) {
				logger.error("Failed to initalize Beans Factory", e);
				throw new RuntimeException("Failed to initalize Beans Factory", e);
			}
			
			IPreferenceStore preferenceStore = GDAClientActivator.getDefault().getPreferenceStore();
			if (preferenceStore.getBoolean(PreferenceConstants.GDA_USE_SCANDATAPOINT_SERVICE)) {
				createScanDataPointService();
			}
			
			fixVisitID();
			
			int returnCode = PlatformUI.createAndRunWorkbench(display, new ApplicationWorkbenchAdvisor());			

			// the workbench doesn't support relaunch yet (bug 61809) so
			// for now restart is used, and exit data properties are checked
			// here to substitute in the relaunch return code if needed
			if (returnCode != PlatformUI.RETURN_RESTART) {
				return EXIT_OK;
			}

			// if the exit code property has been set to the relaunch code, then
			// return that code now, otherwise this is a normal restart
			return EXIT_RELAUNCH.equals(Integer.getInteger(PROP_EXIT_CODE)) ? EXIT_RELAUNCH : EXIT_RESTART;

		} catch (Throwable ne) {
			logger.error("Cannot start client", ne);
			MessageDialog
			.openError(
					new Shell(display),
					"Cannot Start Client",
					"The GDA Client cannot start.\n\nPlease contact your GDA support representative.\n\n'"
							+ ne.getMessage() + "'");
			return EXIT_OK;

		} finally {
			if (display != null && !display.isDisposed()) {
				try {
					display.dispose();
				} catch (Throwable ignored) {
					// Exit time, if exception thrown here, user gets message.
				}
			}
		}
	}

	/**
	 * Sets the visit ID of this Client process in the local JSF instance.
	 * <p>
	 * This must be done after the Workbench has started as the JSF makes connections to the server which can cause
	 * issues if this is done before the workbench is created.
	 */
	private void fixVisitID() {
		logger.info("User " + UserAuthentication.getUsername() + " running GDA client using visit " + LocalProperties.get(LocalProperties.RCP_APP_VISIT));
		InterfaceProvider.getBatonStateProvider().changeVisitID(LocalProperties.get(LocalProperties.RCP_APP_VISIT));
	}

	private boolean createLocalObjectsIfRequired() {
		boolean localObjectsOnly = LocalProperties.check("gda.localObjectsOnly");
		if (localObjectsOnly) {
			// we need to add a mock jython server facade until we can add a Command Server to the GUI.
			MockJythonServerFacade mockJythonServerFacade = new MockJythonServerFacade();
			MockJythonServer mockJythonServer = new MockJythonServer();
			InterfaceProvider.setCommandRunnerForTesting(mockJythonServerFacade);
			InterfaceProvider.setCurrentScanControllerForTesting(mockJythonServerFacade);
			InterfaceProvider.setTerminalPrinterForTesting(mockJythonServerFacade);
			InterfaceProvider.setScanStatusHolderForTesting(mockJythonServerFacade);
			InterfaceProvider.setJythonNamespaceForTesting(mockJythonServerFacade);
			InterfaceProvider.setAuthorisationHolderForTesting(mockJythonServerFacade);
			InterfaceProvider.setScriptControllerForTesting(mockJythonServerFacade);
			InterfaceProvider.setPanicStopForTesting(mockJythonServerFacade);
			InterfaceProvider.setCurrentScanInformationHolderForTesting(mockJythonServer);
			InterfaceProvider.setJythonServerNotiferForTesting(mockJythonServer);
			InterfaceProvider.setDefaultScannableProviderForTesting(mockJythonServer);
			InterfaceProvider.setScanDataPointProviderForTesting(mockJythonServerFacade);
			InterfaceProvider.setBatonStateProviderForTesting(mockJythonServerFacade);
			InterfaceProvider.setJSFObserverForTesting(mockJythonServerFacade);
			InterfaceProvider.setAliasedCommandProvider(mockJythonServerFacade);
		}
		return localObjectsOnly;
	}

	private void createScanDataPointService() {

		try {
			UIScanDataPointEventService.getInstance();
		} catch (Exception ne) {
			logger.error("Cannot start scan data point service", ne);
		}

	}

	/*
	 * sets the chosenVisit attribute to the default visit  java property
	 */
	private void setToDefaultVisit() {
		LocalProperties.set(LocalProperties.RCP_APP_VISIT,LocalProperties.get("gda.defVisit", "0-0"));
	}

	/*
	 * only sets the private chosenVisit attribute
	 */
	private int identifyVisitID(Display display) throws Exception {

		if (!IcatProvider.getInstance().icatInUse()) {
			logger.info("Icat database not in use. Using the default visit defined by property " + LocalProperties.GDA_DEF_VISIT);
			setToDefaultVisit();
			return 1;
		}

		// test if the result has multiple entries
		String user = UserAuthentication.getUsername();
		VisitEntry[] visits;
		try {
			visits = IcatProvider.getInstance().getMyValidVisits(user);
		} catch (Exception e) {
			logger.info(e.getMessage() + " - using default visit defined by property " + LocalProperties.GDA_DEF_VISIT, e);
			setToDefaultVisit();
			return 1;
		}

		boolean isStaff = false;
		try {
			if (AuthoriserProvider.getAuthoriser().isLocalStaff(user)) {
				isStaff = true;
			}
		} catch (ClassNotFoundException e) {
			logger.error("Problem checking if user is staff. Assuming user IS staff.", e);
			isStaff = true;
		}

		// if no valid visit ID then do same as the cancel button
		if (visits == null || visits.length == 0) {
			if (!isStaff) {
				logger
						.info("No visits found for user " + user
								+ " at this time on this beamline. GUI will not start.");
				return EXIT_OK;
			}
			logger.info("No visits found for user " + user
					+ " at this time on this beamline. Will use default visit as ID listed as a member of staff.");
			setToDefaultVisit();
		} else if (visits.length == 1) {
			LocalProperties.set(LocalProperties.RCP_APP_VISIT,visits[0].getVisitID());
		} else {
			// send array of visits to dialog to pick one
			String[][] visitInfo = new String[visits.length][];
			int i = 0;
			for (VisitEntry visit : visits) {
				visitInfo[i] = new String[] { visit.getVisitID(), visit.getTitle() };
				i++;
			}

			final VisitIDDialog visitDialog = new VisitIDDialog(display, visitInfo);
			if (visitDialog.open() == IDialogConstants.CANCEL_ID) {
				logger.info("Cancel pressed in visit chooser dialog. GUI will not continue.");
				return EXIT_OK;
			}
			if (visitDialog.getChoosenID() == null) {
				logger.info("Visit not resolved from visit chooser dialog. GUI will not start.");
				return EXIT_OK;
			}
			LocalProperties.set(LocalProperties.RCP_APP_VISIT,visitDialog.getChoosenID());
		}
		return 1;
	}



	@Override
	public void stop() {
		final IWorkbench workbench = PlatformUI.getWorkbench();
		if (workbench == null)
			return;
		final Display display = workbench.getDisplay();
		if (display == null)
			return;

		if (!display.isDisposed())
			display.syncExec(new Runnable() {
				@Override
				public void run() {
					if (!display.isDisposed()){
						System.out.println("closing the workbench");
						workbench.close();
						System.out.println("closed the workbench");
					}
				}
			});
	}

	/**
	 * Exits if no good
	 */
	private void authenticateUser(final Display display) {

		// if java property not set, then use the process' user.
		if (LocalProperties.get(Authenticator.AUTHENTICATORCLASS_PROPERTY, null) == null) {
			UserAuthentication.setToUseOSAuthentication();
			return;
		}

		String userID = System.getProperty("user.name");

		// change to AuthenticationDialog_old to use an alternative which looks like the old swing version of the GDA
		// login
		final AuthenticationDialog dialog = new AuthenticationDialog(display, SWT.OPEN,
				"Login to the Data Acquisition Client", userID, null);

		final GenericDialog.PasswordChecker checker = new GenericDialog.PasswordChecker() {
			@Override
			public boolean isValid() {
				if (!dialog.isAutomatic()) {
					UserAuthentication.setToNotUseOSAuthentication(dialog.getUsername(), dialog.getPassword());

					// test if info given and user was authenticated
					if (UserAuthentication.getUsername() == null || UserAuthentication.getPassword() == null
							|| UserAuthentication.getUsername().length() == 0
							|| UserAuthentication.getPassword().length() == 0) {
						dialog.setErrorMessage("Please enter a user name and password");
						return false;
					}

					try {
						if (!UserAuthentication.isAuthenticated()) {
							dialog.setErrorMessage("Please enter a correct user name and password");
							return false;
						}
						return true;
					} catch (Exception e) {
						Logger logger = LoggerFactory.getLogger(ElogEntry.class);
						logger.error(e.getMessage(), e);
						System.exit(0);
					}
				} else {
					UserAuthentication.setToUseOSAuthentication();
					return true;
				}
				return false;
			}
		};

		dialog.setChecker(checker);
		final Object ob = dialog.open();

		if (ob == null)
			System.exit(0);

		return;
	}

	/*
	 * Launch the ObjectServer to create the client implementation (as the AcquisitionFrame would do in original gda)
	 */
	private static boolean started = false;

	@SuppressWarnings("unused")
	private static void createClientObjects() throws FactoryException {
		if (!started) {
			ObjectServer.createClientImpl();
			started = true;
		}
	}

	/**
	 * 
	 * @param workspacePath
	 * @throws Exception if the workspace failed to be set
	 */
	private void createVisitBasedWorkspace(final String workspacePath) throws Exception {

		boolean newWorkspace = false;
		final File workspace = new File(workspacePath);
		if (!workspace.exists()) {
			// New workspace
			newWorkspace = true;
			if (!workspace.mkdirs()) {
				final String msg = "Cannot create workspace in " + workspace.getAbsolutePath() + " not setting workspace";
				throw new Exception(msg);
			}
		}
		workspace.setWritable(true);
		workspace.setReadable(true);

		if (!workspace.canRead() || !workspace.canWrite() || !workspace.canExecute()) {
			final String msg = "Not setting workspace to " + workspace.getAbsolutePath()
					+ " due to insufficient permissions.";
			throw new Exception(msg);
		}

		URL url;
		Location instanceLocation;
		try {
			url = workspace.toURI().toURL();
			instanceLocation = Platform.getInstanceLocation();
			if (!instanceLocation.isSet()) {
				instanceLocation.set(url, false);
			} else {
				// generally it is expected that the instanceLocation is only set in development environment
				
				if (instanceLocation.getURL().equals(instanceLocation.getDefault())) {
					// If you get this exception you have hit a race condition similar to what was reported in GDA-3414.
					// This has probably occurred because some Eclipse component was run prior to the workbench being 
					// fully initialised. To track down the area, place a breakpoint in BasicLocation#getUrl()'s if statement
					// that controls if the Location has not been initialised. Once you know who is calling getUrl too early,
					// figure out how to delay it until the workbench has been started.
					// NOTE: this check may be brittle as it is dependent on current implementation of LocationManager#initializeLocations
					throw new Exception("Workspace has already been set when trying to set visit based workspace location.");
				}
				
				logger.warn("Not using visit based workspace " + url + " because instance location has been set with -data command line argument to " + instanceLocation.getURL());
				// for correct reporting further on
				url = instanceLocation.getURL();
			}
		} catch (Exception e) {
			final String msg = "Cannot set workspace to " + workspace.getAbsolutePath();
			throw new Exception(msg, e);
		}
		if (instanceLocation.lock()) {
			logger.info("Workspace set to " + url);
		} else {
			throw new Exception("Workspace at " + url + " is locked.\n Is another instance of GDA already running?");
		}
		GDAClientActivator.getDefault().getPreferenceStore().setValue(PreferenceConstants.NEW_WORKSPACE, newWorkspace);
	}

	private String getWorkSpacePath() {
		
		// ensure we do not take these values from the metadata when defining the workspace that this client will use
		HashMap<String,String> metadataOverrides = new HashMap<String,String>();
		metadataOverrides.put("visit", LocalProperties.get(LocalProperties.RCP_APP_VISIT));
		metadataOverrides.put("federalid", UserAuthentication.getUsername());
		metadataOverrides.put("user", UserAuthentication.getUsername());
		
		String path = null;
		try {
			path = PathConstructor.createFromProperty("gda.rcp.workspace",metadataOverrides);
		} catch (Exception ne) {
			path = null;
		}

		if (path == null) {
			final String varDir = LocalProperties.getVarDir();
			final String username = UserAuthentication.getUsername();
			final String visit = LocalProperties.get(LocalProperties.RCP_APP_VISIT);
			final String template = String.format("%s/.workspace-%s-%s", varDir, username, visit);
			path = PathConstructor.createFromTemplate(template);
		}
		return path;
	}

	/**
	 * Returns the path to the location of the XML project, e.g. As used for the storage location of the EXAFS project.
	 * The intention is this project is stored outside the workspace to allow the workspace to be deleted without losing
	 * the user created XML files. The path can be set via the gda.rcp.xmlproject property, or it will be created in the
	 * users visit.
	 * 
	 * @return the path to the xmlproject
	 */
	public static String getXmlPath() {
		String path = null;
		try {
			path = PathConstructor.createFromProperty("gda.rcp.xmlproject");
		} catch (Exception ne) {
			path = null;
		}

		if (path == null) {
			path = PathConstructor.createFromRCPProperties() + "/xml/";
		}
		return path;
	}

	private static void createObjectFactory(final Display display, final boolean localObjectsOnly) {
		try {
			if (!started) {
				String gda_gui_beans = LocalProperties.get(
						LocalProperties.GDA_GUI_BEANS_XML,
						LocalProperties.get(LocalProperties.GDA_GUI_XML));
					if (gda_gui_beans != null) {
						// remove existing factories first 
						Finder.getInstance().removeAllFactories();
						SpringObjectServer s = new SpringObjectServer(new File(gda_gui_beans), localObjectsOnly);
						s.configure();
						
					}
					started = true;
			}
		} catch (Exception ne) {
			logger.error("Error in createObjectFactory", ne);// Representative

			MessageDialog
					.openError(
							new Shell(display),
							"Error starting GDA Client",
							"The GDA Client cannot start.\n\nPlease contact your GDA support representative.\n\n'"
									+ ne.getMessage() + "'");

			if (!ClientManager.isTestingMode())
				System.exit(-1);
		}
	}
}
