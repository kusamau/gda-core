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

package gda.factory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finder, a singleton class, allows objects to be retrieved from local store, a name service or created by a factory.
 * <p>
 * For unit testing classes which depend on the Finder, set up the Finder first with a test factory (see TestHelpers in the uk.ac.gda.test.helpers bundle) and
 * add any necessary Findables for the test to it. For example:
 *
 * <pre>
 * <code>
 * public void setUp() throws Exception {
 * 	// .. set up mocks first
 * 	Factory testFactory = TestHelpers.createTestFactory("test");
 * 	testFactory.addFindable(mockFindable);
 * 	Finder.getInstance().addFactory(testFactory);
 * }
 * </code>
 * </pre>
 */
public enum Finder {
	INSTANCE;

	private static final Logger logger = LoggerFactory.getLogger(Finder.class);

	private final Set<Factory> factories = new CopyOnWriteArraySet<>();

	/**
	 * Getter to construct and/or return single instance of the finder.
	 * <p>
	 * This can be used in unit tests independently of the rest of the GDA framework.
	 *
	 * @return the instance of finder.
	 */
	public static Finder getInstance() {
		return INSTANCE;
	}

	/**
	 * Return a named object from any of the factories known to the finder.
	 *
	 * @param <T>
	 *            class of Object being returned
	 * @param name
	 *            object to find.
	 * @return the findable object or null if it cannot be found
	 */
	public <T extends Findable> T find(String name) {
		return findObjectByName(name, false, true);
	}

	/**
	 * Return a named object from any of the factories known to the finder.
	 * <p>
	 * Do <b>not</b> log warning if there is a FactoryException or nothing can be found.
	 *
	 * @param <T>
	 *            class of Object being returned
	 * @param name
	 *            object to find.
	 * @return the findable object or null if it cannot be found
	 */
	public <T extends Findable> T findNoWarn(String name) {
		return findObjectByName(name, false, false);
	}

	/**
	 * Find an instance of a locally defined object
	 *
	 * @param <T>
	 *            class of Object being returned
	 * @param name
	 *            the name of the instance to find
	 * @return the findable object or null if it cannot be found
	 */
	public <T extends Findable> T findLocal(String name) {
		return findObjectByName(name, true, true);
	}

	/**
	 * Find an instance of a locally defined object
	 * <p>
	 * Do <b>not</b> log warning if there is a FactoryException or nothing can be found.
	 *
	 * @param <T>
	 *            class of Object being returned
	 * @param name
	 *            the name of the instance to find
	 * @return the findable object or null if it cannot be found
	 */
	public <T extends Findable> T findLocalNoWarn(String name) {
		return findObjectByName(name, true, false);
	}

	/**
	 * Find an instance of an object given its name
	 *
	 * @param name
	 *            The name of the object to find
	 * @param local
	 *            True if only local objects are to be found
	 * @param warn
	 *            True to log a warning message in the case of a FactoryException
	 * @return the findable object or null if it cannot be found
	 */
	private <T extends Findable> T findObjectByName(String name, boolean local, boolean warn) {
		T findable = null;
		for (Factory factory : factories) {
			if (local && !factory.isLocal()) {
				continue;
			}
			try {
				if ((findable = factory.getFindable(name)) != null) {
					logger.debug("Found '{}' using factory '{}' (local={})", name, factory, local);
					break;
				}
			} catch (FactoryException e) {
				if (warn) {
					logger.warn("FactoryException locally looking for {}", name, e);
				}
			}
		}
		if (findable == null && warn) {
			logger.warn("Could not find \"{}\"", name);
		}
		return findable;
	}


	/**
	 * Adds a factory to the list of searchable factories known by the Finder.
	 *
	 * @param factory
	 *            the factory to add to the list.
	 */
	public void addFactory(Factory factory) {
		factories.add(factory);
		logger.debug("Added factory '{}' now have {} factories", factory, factories.size());
	}

	public void removeAllFactories(){
		factories.clear();
		logger.debug("Cleared factories");
	}


	/**
	 * List all the interfaces available on the Finder. This method is aimed at users of the scripting environment for
	 * searching for available hardware by using the 'list' command.
	 *
	 * @return array of interface names
	 */
	public List<String> listAllInterfaces() {
		List<Findable> objects = listAllObjects();
		List<String> usedInterfaces = new ArrayList<>();

		for (Findable findable : objects) {
			// loop through all the interfaces that objects use
			Class<?> superclass = findable.getClass();

			while (superclass != null) {
				for (Class<?> theClass : superclass.getInterfaces()) {
					// if there is a match then add this object
					String name = theClass.getName();
					name = name.substring(name.lastIndexOf('.') + 1);
					if (!usedInterfaces.contains(name)) {
						usedInterfaces.add(name);
					}
				}
				superclass = superclass.getSuperclass();
			}
		}
		return usedInterfaces;
	}

	/**
	 * Returns an array of the names of all the objects in this Finder's factories which use the supplied interface
	 * name, as defined by the XML.
	 *
	 * @param interfaceName
	 *            the required interface to search for.
	 * @return the list of Findable object names supporting the named interface.
	 */
	public List<String> listAllNames(String interfaceName) {
		final List<Findable> findableRefs = listAllObjects(interfaceName);
		final List<String> findableNames = new ArrayList<>();
		for (Findable findable : findableRefs) {
			String findableName = findable.getName();
			findableName = findableName.substring(findableName.lastIndexOf('.') + 1);
			findableNames.add(findableName);
		}
		return findableNames;
	}


	/**
	 * Local version of listAllObjects
	 *
	 * @param interfaceName
	 * @return the list of Findable objects supporting the named interface.
	 */
	public List<Findable> listAllLocalObjects(String interfaceName) {
		return listAllObjects(interfaceName,true);
	}

	/**
	 * Returns an array of the references of all the objects in this Finder's factories which use the supplied interface
	 * name as defined by the XML.
	 *
	 * @param interfaceName
	 *            the required interface to search for.
	 * @return the list of Findable objects supporting the named interface.
	 */
	public List<Findable> listAllObjects(String interfaceName) {
		return listAllObjects(interfaceName, false);
	}

	private List<Findable> listAllObjects(String interfaceName, boolean localObjectsOnly) {
		// if no class name given, then supply all objects
		if (interfaceName == null) {
			return listAllObjects();
		}

		List<Findable> objectRefs = new ArrayList<>();
		// loop through all factories
		for (Factory factory : factories) {

			if (localObjectsOnly && !factory.isLocal()){
				continue;
			}

			// loop through all objects in that factory
			for (Findable findable : factory.getFindables()) {

				// for this findable, check its class and interfaces to see if they match the reqested interface
				if (classOrInterfacesMatchesString(findable.getClass(), interfaceName)
						&& !objectRefs.contains(findable)) {
					objectRefs.add(findable);
				}
				// else loop over superclasses up the heirachy until java.lang.Object reached, testing each in turn
				else {
					Class<?> superclass = findable.getClass().getSuperclass();
					boolean found = false;

					while (!found && superclass != null) {
						found = classOrInterfacesMatchesString(superclass, interfaceName);
						superclass = superclass.getSuperclass();
					}

					if (found) {
						objectRefs.add(findable);
					}
				}

			}
		}
		return objectRefs;
	}

	/**
	 * Tests the given class to see if its name, or the name of any interface it uses, matches the given interface name.
	 *
	 * @param classToTest
	 * @param interfaceName
	 * @return true if the same else false
	 */
	private boolean classOrInterfacesMatchesString(Class<?> classToTest, String interfaceName) {
		// first check the actual class
		if (classNameMatchesString(classToTest, interfaceName)) {
			return true;
		}

		// then loop through all the interfaces that object uses
		for (Class<?> objInterface : classToTest.getInterfaces()) {
			if (classOrInterfacesMatchesString(objInterface, interfaceName)) {
				return true;
			}
		}

		// if get here then nothing found
		return false;
	}

	/**
	 * Tests if the given Class has a name which matches the given interface name. This works if the interface name is
	 * either fully resolved or not.
	 *
	 * @param theClass
	 * @param interfaceName
	 * @return true if the same else false
	 */
	private boolean classNameMatchesString(Class<?> theClass, String interfaceName) {
		String className = theClass.getName();
		String shortName = className.substring(className.lastIndexOf('.') + 1);
		return className.compareTo(interfaceName) == 0 || shortName.compareTo(interfaceName) == 0;
	}

	/**
	 * Returns an array of all the objects in this finder's factories as defined by the XML.
	 *
	 * @return a list of all known Findable objects.
	 */
	private List<Findable> listAllObjects() {
		List<Findable> allFindables = new ArrayList<>();
		for (Factory factory : factories) {
			allFindables.addAll(factory.getFindables());
		}
		return allFindables;
	}

	/**
	 * Returns a map of all {@link Findable} objects (local & remote) of the given type
	 *
	 * @param <T>
	 * @param clazz
	 *            the class or interface to match
	 * @return a map of matching {@code Findable}s, with the object names as keys and the objects as values
	 */
	public <T extends Findable> Map<String, T> getFindablesOfType(Class<T> clazz) {
		return getFindablesOfType(clazz, false);
	}

	/**
	 * Returns a map of all local {@link Findable} objects of the given type
	 *
	 * @param clazz
	 *            the class or interface to match
	 * @return a map of matching {@code Findable}s, with the object names as keys and the objects as values
	 */
	public <T extends Findable> Map<String, T> getLocalFindablesOfType(Class<T> clazz) {
		return getFindablesOfType(clazz, true);
	}

	/**
	 * Returns a map of all {@link Findable} objects of the given type
	 *
	 * @param clazz
	 *            the class or interface to match
	 * @param local True if only local objects are to be returned
	 * @return a map of matching {@code Findable}s, with the object names as keys and the objects as values
	 */
	private <T extends Findable> Map<String, T> getFindablesOfType(Class<T> clazz, boolean local) {
		Map<String, T> findables = new HashMap<>();
		for (Factory factory : factories) {
			if (local && !factory.isLocal()) {
				continue;
			}
			for (Findable findable : factory.getFindables()) {
				if (clazz.isAssignableFrom(findable.getClass())) {
					findables.put(findable.getName(), clazz.cast(findable));
				}
			}
		}
		return findables;
	}

	/**
	 * Returns a list of all {@link Findable} objects (local & remote) of the given type
	 *
	 * @param clazz
	 *            the class or interface to match
	 * @return a list of matching {@code Findable}s
	 */
	public <T extends Findable> List<T> listFindablesOfType(Class<T> clazz) {
		return listFindablesOfType(clazz, false);
	}

	/**
	 * Returns a list of all local {@link Findable} objects of the given type
	 *
	 * @param clazz
	 *            the class or interface to match
	 * @return a list of matching {@code Findable}s
	 */
	public <T extends Findable> List<T> listLocalFindablesOfType(Class<T> clazz) {
		return listFindablesOfType(clazz, true);
	}

	/**
	 * Returns a list of all {@link Findable} objects of the given type.
	 *
	 * @param clazz
	 *            the class or interface to match
	 * @param local
	 *            True to only search local factories
	 * @return a list of matching {@code Findable}s
	 */
	private <T extends Findable> List<T> listFindablesOfType(Class<T> clazz, boolean local) {
		return new ArrayList<>(getFindablesOfType(clazz, local).values());
	}

}
