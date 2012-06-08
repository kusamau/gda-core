/*-
 * Copyright © 2009 Diamond Light Source Ltd., Science and Technology
 * Facilities Council Daresbury Laboratory
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

package gda.util;

import gda.configuration.properties.LocalProperties;
import gda.factory.ConditionallyConfigurable;
import gda.factory.Configurable;
import gda.factory.Factory;
import gda.factory.FactoryBase;
import gda.factory.FactoryException;
import gda.factory.Finder;
import gda.factory.corba.util.AdapterFactory;
import gda.spring.SpringApplicationContextBasedObjectFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * A subclass of {@link ObjectServer} that uses a Spring application context.
 */
public class SpringObjectServer extends ObjectServer {

	private static final Logger logger = LoggerFactory.getLogger(SpringObjectServer.class);
	
	boolean allowExceptionInConfigure=LocalProperties.check(FactoryBase.GDA_FACTORY_ALLOW_EXCEPTION_IN_CONFIGURE);

	private ApplicationContext applicationContext;

	/**
	 * Creates an object server.
	 * 
	 * @param xmlFile
	 *            the XML configuration file
	 */
	public SpringObjectServer(File xmlFile) {
		this(xmlFile, false);
	}

	/**
	 * Creates an object server.
	 * 
	 * @param xmlFile
	 *            the XML configuration file
	 * @param localObjectsOnly
	 */
	public SpringObjectServer(File xmlFile, boolean localObjectsOnly) {
		super(xmlFile, localObjectsOnly);
		if (xmlFile.isFile()) {
			applicationContext = new FileSystemXmlApplicationContext("file:" + xmlFile.getAbsolutePath());
		} else if (xmlFile.isDirectory()) {
			//TODO to support multiple configuration metadata files so no need <import/> in a single root XML file. 
			applicationContext = new FileSystemXmlApplicationContext(listOfFiles(xmlFile));
		} else {
			throw new IllegalArgumentException("Argument is neither a file nor a directory :" + xmlFile);
		}
	}

	private String[] listOfFiles(File xmlFile) {
		return xmlFile.list(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				if (name.contains(".xml")) {
					return true;
				} else if (new File(dir, name).isDirectory()) {
					//recurse through child directories.
					listOfFiles(new File(dir, name));
				}
				return false;
			}
		});
	}

	@Override
	protected void startServer() throws FactoryException {
		addSpringBackedFactoryToFinder(applicationContext);
		/*
		 * We need to add the adapterFactory to the finder if present in the applicationContext to allow remote objects to 
		 * be found during subsequent configureAllFindablesInApplicationContext. 
		 * The adapterFactory must be added after the spring backed objects as the latter may include those from corba:import. If
		 * the order was otherwise we would duplicate adapters for remote objects. 
		 * This change is in anticipation of future changes to corba:import to only import named objects rather than all.
		 */
		addAdapterFactoryToFinder();		
		configureAllConfigurablesInApplicationContext(applicationContext);
		startOrbRunThread();
	}

	
	/**
	 * Adds a Spring-backed {@link Factory} to the {@link Finder}.
	 */
	private void addSpringBackedFactoryToFinder(ApplicationContext applicationContext) {
		SpringApplicationContextBasedObjectFactory springObjectFactory = new SpringApplicationContextBasedObjectFactory();
		springObjectFactory.setApplicationContext(applicationContext);
		factories.add(springObjectFactory);
		Finder.getInstance().addFactory(springObjectFactory);
	}

	private void addAdapterFactoryToFinder() {
		Map<String,AdapterFactory> adapterFactories = applicationContext.getBeansOfType(AdapterFactory.class);
		for (Map.Entry<String, AdapterFactory> entry : adapterFactories.entrySet()) {
			String name = entry.getKey();
			AdapterFactory adapterFactory = entry.getValue();
			logger.info("Adding AdapterFactory " + name + " to finder");
			Finder.getInstance().addFactory(adapterFactory);
		}
	}

	private void configureAllConfigurablesInApplicationContext(ApplicationContext applicationContext)
			throws FactoryException {
		Map<String, Configurable> configurables = applicationContext.getBeansOfType(Configurable.class);
		for (Map.Entry<String, Configurable> entry : configurables.entrySet()) {
			String name = entry.getKey();
			Configurable obj = entry.getValue();
			
			boolean willConfigure = true;
			
			if (obj instanceof ConditionallyConfigurable) {
				final ConditionallyConfigurable cc = (ConditionallyConfigurable) obj;
				willConfigure = cc.isConfigureAtStartup();
			}
			
			if (willConfigure) {
				logger.info("Configuring " + name);
				try {
					obj.configure();
				} catch (Exception e) {
					if (!allowExceptionInConfigure) {
						throw new FactoryException("Error in configure for " + name, e);
					}
					logger.error("Error in configure for " + name, e);
				}
			}
			
			else {
				logger.info("Not configuring " + name);
			}
		}
	}

}
