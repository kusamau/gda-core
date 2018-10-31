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

package uk.ac.gda.remoting.server;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.remoting.rmi.RmiServiceExporter;

import com.google.common.base.Stopwatch;

import gda.configuration.properties.LocalProperties;
import gda.factory.Findable;
import gda.factory.Localizable;
import gda.jython.accesscontrol.RbacUtils;
import gda.observable.IObservable;
import uk.ac.gda.api.remoting.ServiceInterface;

/**
 * This class will automatically create RMI exports for beans defined in Spring which define a service interface via the
 * {@link ServiceInterface} annotation, and do not declare themselves local using the {@link Localizable} interface. In
 * addition it will setup events dispatching if the objects supports events by being {@link IObservable}.
 * <p>
 * To use this add the following to the server Spring XML configuration:
 *
 * <pre>
 * {@code
 * <bean class="uk.ac.gda.remoting.server.RmiAutomatedExporter" />
 * }
 * </pre>
 *
 * @author James Mudd
 * @since GDA 9.7
 */
public class RmiAutomatedExporter implements ApplicationContextAware, InitializingBean {
	private static final Logger logger = LoggerFactory.getLogger(RmiAutomatedExporter.class);

	public static final String AUTO_EXPORT_RMI_PREFIX = "gda-auto-export/";

	/** Property that allows the RMI port to be changed. it defaults to 1099 the default RMI port */
	public static final String RMI_PORT_PROPERTY = "uk.ac.gda.remoting.rmiPort";

	/** The port used to expose both the RMI registry and services */
	private final int rmiPort = LocalProperties.getAsInt(RMI_PORT_PROPERTY, 1099);

	/**
	 * This is the uk.ac.diamond.org.springframework OSGi bundle classloader. It's needed here because you might want to
	 * export any class Spring has instantiated.
	 */
	private static final ClassLoader SPRING_BUNDLE_LOADER = ApplicationContext.class.getClassLoader();

	private ApplicationContext applicationContext;

	/** List of all the exporters this is to allow them to be unbound at shutdown */
	private final List<RmiServiceExporter> exporters = new ArrayList<>();

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// Spring will call this automatically after setting up properties.
		exportAll();
	}

	/**
	 * This is the method that inspects the {@link #applicationContext} and will automatically export all the qualifying
	 * beans.
	 */
	private void exportAll() {
		logger.info("Starting automated RMI exports...");
		final Stopwatch exportsTimer = Stopwatch.createStarted();

		logger.debug("RMI services on port '{}' with prefix '{}'", rmiPort, AUTO_EXPORT_RMI_PREFIX);

		final Map<String, Findable> allRmiExportableBeans = getRmiExportableBeans();

		logger.debug("Exporting {} beans over RMI...", allRmiExportableBeans.size());

		// Cache the existing TCCL to be switched back after exporting
		final ClassLoader tccl = Thread.currentThread().getContextClassLoader();

		try {
			// Switch the TCCL to the Spring bundle loader which should be able to see all the needed classes
			Thread.currentThread().setContextClassLoader(SPRING_BUNDLE_LOADER);

			for (Entry<String, Findable> entry : allRmiExportableBeans.entrySet()) {
				final String name = entry.getKey();
				final Findable bean = entry.getValue();

				final Class<?> beanClass = getType(bean);
				final Class<?> serviceInterface = beanClass.getAnnotation(ServiceInterface.class).value();

				export(name, bean, serviceInterface);
			}

		} finally {
			// Restore the original TCCL
			Thread.currentThread().setContextClassLoader(tccl);
		}

		logger.info("Completed RMI exports. Exported {} objects, in {} ms", allRmiExportableBeans.size(),
				exportsTimer.elapsed(MILLISECONDS));
	}

	private Map<String, Findable> getRmiExportableBeans() {
		final Map<String, Findable> allFindableBeans = applicationContext.getBeansOfType(Findable.class);
		return allFindableBeans.entrySet().stream()
				.filter(this::hasServiceInterfaceAnnotation) // Removes beans without @ServiceInterface
				.filter(this::isNotLocal) // Removes beans declared as local
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}

	/**
	 * Export the object over RMI, this includes setting up events if needed.
	 *
	 * @param name The name of the object to be exported
	 * @param bean The object itself
	 * @param serviceInterface The interface to expose over RMI
	 */
	private void export(String name, Findable bean, Class<?> serviceInterface) {
		logger.trace("Exporting '{}' with interface '{}'...", name, serviceInterface.getName());
		final RmiServiceExporter serviceExporter = new RmiServiceExporter();
		serviceExporter.setRegistryPort(rmiPort);
		serviceExporter.setServicePort(rmiPort);
		serviceExporter.setService(bean);
		serviceExporter.setServiceName(AUTO_EXPORT_RMI_PREFIX + name);
		serviceExporter.setServiceInterface(serviceInterface);
		serviceExporter.setReplaceExistingBinding(false); // Try to be safe there shouldn't be an existing binding
		try {
			// Actually export the service here
			serviceExporter.prepare();
			logger.debug("Exported '{}' with interface '{}'", name, serviceInterface.getName());

			setupEventDispatchIfSupported(bean);
			// Add to list of exporters for unbinding at shutdown
			exporters.add(serviceExporter);
		} catch (RemoteException e) {
			logger.error("Exception exporting '{}' with interface '{}'", name, serviceInterface.getName(), e);
		}
	}

	private void setupEventDispatchIfSupported(Findable toBeExported) {
		if (toBeExported instanceof IObservable) {
			final IObservable observable = (IObservable) toBeExported;

			ServerSideEventDispatcher observer = new ServerSideEventDispatcher();
			observer.setSourceName(toBeExported.getName());
			observer.setObject(observable);

			try {
				observer.afterPropertiesSet();
			} catch (Exception e) {
				logger.error("Failed to setup events dispatching for: {}", toBeExported.getName(), e);
			}
		} else {
			logger.debug("No events support added for '{}' as it does not implement IObservable",
					toBeExported.getName());
		}
	}

	/**
	 * Checks the objects class to see if it has a {@link ServiceInterface} annotation.
	 *
	 * @param entry
	 *            {@link Entry} of name to {@link Findable}
	 * @return <code>true</code> if the object has a {@link ServiceInterface} annotation <code>false</code> otherwise
	 */
	private boolean hasServiceInterfaceAnnotation(Entry<String, Findable> entry) {
		Class<?> type = getType(entry.getValue());
		final boolean serviceInterfaceAnnotationDeclared = type.isAnnotationPresent(ServiceInterface.class);

		if (!serviceInterfaceAnnotationDeclared) {
			logger.trace("'{}' not exported as '{}' class doesn't have @ServiceInterface annotation", entry.getKey(),
					type.getName());
			return false; // No @ServiceInterface on class
		}
		return true;
	}

	private boolean isLocal(Entry<String, Findable> entry) {
		final Findable bean = entry.getValue();
		if (bean instanceof Localizable) {
			return ((Localizable) bean).isLocal();
		}
		return false;
	}

	private boolean isNotLocal(Entry<String, Findable> entry) {
		return !isLocal(entry);
	}

	/**
	 * Try's to unbind all the RMI services exported. Failures will be logged only.
	 */
	public void shutdown() {
		for (RmiServiceExporter exporter : exporters) {
			try {
				exporter.destroy();
			} catch (Exception e) {
				logger.error("Failed to unbind RMI service: {}", exporter, e);
			}
		}
	}

	private static Class<?> getType(Object bean) {
		if (RbacUtils.objectIsCglibProxy(bean)) {
			return bean.getClass().getSuperclass();
		}
		return bean.getClass();
	}
}
