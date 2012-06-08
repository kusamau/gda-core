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
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finder, a singleton class, allows objects to be retrieved from local store, a name service or created by a factory.
 * Objects must be specified in XML file.
 */
public class Finder {
	
	private static final Logger logger = LoggerFactory.getLogger(Finder.class);

	private static Finder instance;

	private static Vector<Factory> factories;

	/**
	 * Constructs a new Finder and initialises its list of factories which will be used to search for objects.
	 */
	private Finder() {
		factories = new Vector<Factory>();
	}

	/**
	 * Getter to construct and/or return single instance of the finder.
	 * 
	 * @return the instance of finder.
	 */
	public synchronized static Finder getInstance() {
		if( instance == null){
			instance = new Finder();
		}
		return instance;
	}

	/**
	 * Return a named object from any of the factories known to the finder.
	 * 
	 * @param <T>
	 * @param name
	 *            object to find.
	 * @return object of type {@link gda.factory.Findable}.
	 */
	@SuppressWarnings("unchecked")
	public <T extends Findable> T find(String name) {
		T findable = null;
		for (Factory factory : factories) {
			try {
				if ((findable = (T) factory.getFindable(name)) != null) {
					break;
				}
			} catch (FactoryException e) {
				logger.warn("FactoryException looking for "+name,e);
			}
		}
		return findable;
	}

	/**
	 * Return a named object from any of the factories known to the finder.
	 * 
	 * @param <T>
	 *            class of Object being returned
	 * @param name
	 *            object to find.
	 * @return object of type {@link gda.factory.Findable}.
	 */
	public <T extends Findable> T findNoWarn(String name) {
		T findable = null;
		for (Factory factory : factories) {
			try {
				if ((findable = factory.getFindable(name)) != null) {
					break;
				}
			} catch (FactoryException e) {
				// Don't issue a warning message.
			}
		}
		return findable;
	}

	/**
	 * Find an instance of a locally defined object
	 * 
	 * @param <T>
	 * @param name
	 *            the name of the instance to find
	 * @return the findable object
	 */
	public <T extends Findable> T findLocal(String name) {
		T findable = null;
		for (Factory factory : factories) {
			if (!factory.isLocal()) {
				continue;
			}
			try {
				if ((findable = factory.getFindable(name)) != null) {
					break;
				}
			} catch (FactoryException e) {
				logger.warn("FactoryException locally looking for "+name,e);
			}
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
		factories.addElement(factory);
	}

	public void removeAllFactories(){
		factories.clear();
	}
	

	/**
	 * List all the interfaces available on the Finder. This method is aimed at users of the scripting environment for
	 * searching for available hardware by using the 'list' command.
	 * 
	 * @return array of interface names
	 */
	public ArrayList<String> listAllInterfaces() {
		ArrayList<Findable> objects = listAllObjects();
		ArrayList<String> usedInterfaces = new ArrayList<String>();

		for (Findable findable : objects) {
			// loop through all the interfaces that objects use
			Class<?> superclass = findable.getClass();

			while (superclass != null) {
				for (Class<?> theClass : superclass.getInterfaces()) {
					// if there is a match then add this object
					String name = theClass.getName();
					name = name.substring(name.lastIndexOf(".") + 1);
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
	public ArrayList<String> listAllNames(String interfaceName) {
		ArrayList<Findable> findableRefs = listAllObjects(interfaceName);
		ArrayList<String> findableNames = new ArrayList<String>();
		for (Findable findable : findableRefs) {
			String findableName = findable.getName();
			findableName = findableName.substring(findableName.lastIndexOf(".") + 1);
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
	public ArrayList<Findable> listAllLocalObjects(String interfaceName) {
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
	public ArrayList<Findable> listAllObjects(String interfaceName) {
		return listAllObjects(interfaceName,false);
	}
	
	private ArrayList<Findable> listAllObjects(String interfaceName, boolean localObjectsOnly) {
		// if no class name given, then supply all objects
		if (interfaceName == null) {
			return listAllObjects();
		}

		ArrayList<Findable> objectRefs = new ArrayList<Findable>();
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
		String shortName = className.substring(className.lastIndexOf(".") + 1);
		return className.compareTo(interfaceName) == 0 || shortName.compareTo(interfaceName) == 0;
	}

	/**
	 * Returns an array of all the objects in this finder's factories as defined by the XML.
	 * 
	 * @return a list of all known Findable objects.
	 */
	private ArrayList<Findable> listAllObjects() {
		ArrayList<Findable> allFindables = new ArrayList<Findable>();
		for (Factory factory : factories) {
			allFindables.addAll(factory.getFindables());
		}
		return allFindables;
	}

	/**
	 * Returns {@link Findable}s that match the specified object type.
	 * 
	 * @param clazz the class or interface to match
	 * 
	 * @return a map of matching {@code Findable}s, with the object names as
	 *         keys and the objects as values
	 */
	public Map<String, Findable> getFindablesOfType(Class<?> clazz) {
		Map<String, Findable> findables = new HashMap<String, Findable>();
		for (Factory factory : factories) {
			for (Findable findable : factory.getFindables()) {
				if (clazz.isAssignableFrom(findable.getClass())) {
					findables.put(findable.getName(), findable);
				}
			}
		}
		return findables;
	}

	/**
	 * Returns a typed list {@link Findable}s that match the specified object type.
	 * 
	 * @param clazz the class or interface to match
	 * 
	 * @return a list of matching {@code Findable}s
	 */
	@SuppressWarnings("unchecked")
	public <T extends Findable> List<T> listFindablesOfType(Class<T> clazz) {
		List<T> clazzList = new ArrayList<T>();
		for (Factory factory : factories) {
			for (Findable findable : factory.getFindables()) {
				if (clazz.isAssignableFrom(findable.getClass())) {
					clazzList.add((T)findable);
				}
			}
		}
		return clazzList;
	}

	/**
	 * Remove all entries from the factory list.To be used with care!
	 * Primarily used to tidy up in test cases.
	 */
	public void clear() {
		instance = null;
	}
}
