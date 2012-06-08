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

package gda.configuration.object;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CastorConfigClassLoader
 * <p>
 * This custom class loader attempts to load a classname from a location relative to a class root folder specified to
 * the constructor. If class is not found, it reverts to the default/system class loader. Loaded classes are cached for
 * faster load times, if class is used more than once.
 * <p>
 * This class loader is for use during configuration mode. The main purpose is to intercept creation of "real" app
 * classes and replace them with dummy JavaBean classes, which have the same getters and setters. This is so during
 * config mode, "fake" app classes can be configured, instead of real classes. This should ensure config mode will
 * always be safe - ie no real application behaviour should occur if configuration mode just alters "fake" beans.
 */
public class CastorConfigClassLoader extends /* URL */ClassLoader /* SecureClassLoader */
{
	private static final Logger logger = LoggerFactory.getLogger(CastorConfigClassLoader.class);

	private Map<String, Class<?>> classCache = new HashMap<String, Class<?>>();

	// absolute path to a root folder where class files are stored below
	private String classFileRootFolder;

	// offset package path ("dotted") which can be applied to classname
	// either with or without classFileRootFolder
	private String offsetPackagePath;

	// private CastorConfigClassLoader() { super(); }

	// private CastorConfigClassLoader(ClassLoader parent) { super(parent);
	// }

	/**
	 * @param classFileRootFolder
	 *            the root folder of autogenerated class files
	 * @param offsetPackagePath
	 *            the full package name of the autogenerated classes
	 */
	public CastorConfigClassLoader(String classFileRootFolder, String offsetPackagePath) {
		super();

		this.classFileRootFolder = classFileRootFolder;
		this.offsetPackagePath = offsetPackagePath;
	}

	// private CastorConfigClassLoader(ClassLoader parent,
	// String classFileRootFolder, String offsetPackagePath)
	// {
	// super(parent);

	// this.classFileRootFolder = classFileRootFolder;
	// this.offsetPackagePath = offsetPackagePath;
	// }

	/**
	 * try to load class using default parent/system classloader
	 * 
	 * @param className
	 *            class to load
	 * @return the loaded Class derivative
	 * @throws ClassNotFoundException
	 */
	private Class<?> loadClassFromParentLoader(String className) throws ClassNotFoundException {
		try {
			logger.debug("loading class " + className);

			Class<?> c = getParent().loadClass(className);

			return c;
		} catch (ClassNotFoundException e) {
			logger.error("loading class failed : " + className + e.getMessage());

			throw new ClassNotFoundException("CastorConfigClassLoader exception while loading class " + className);
		}
	}

	/**
	 * Try to load class from a .class file and generate a Java class from it file is attempted to be loaded using dot
	 * package convention, relative to specified classFileRootFolder.
	 * 
	 * @param className
	 *            the name of the requested class
	 * @return an instance of the requested Class derivative
	 * @throws ClassNotFoundException
	 */
	private Class<?> loadClassFromFile(String className) throws ClassNotFoundException {
		// Object object = null;
		try {
			// File f = new File(fileName);

			logger.debug("loading class " + className);

			FileInputStream file = new FileInputStream(className);

			// if (file != null)
			// {
			byte[] classData = new byte[file.available()];

			file.read(classData);
			file.close();
			file = null;

			Class<?> c = super.defineClass(className, classData, 0, classData.length);

			if (c == null) {
				throw new ClassFormatError("CastorConfigClassLoader failed to define " + className);
			}

			return c;
			// }

			// Message.alarm("loadClassFromFile class file not found: " +
			// className);
			// throw new ClassNotFoundException(className);
			// return null;
		} catch (IOException e) {
			logger.debug(e.getStackTrace().toString());
			// return null;

			throw new ClassNotFoundException("CastorConfigClassLoader IO exception while loading class " + className);
		} catch (Exception e) {
			logger.debug(e.getStackTrace().toString());

			throw new ClassNotFoundException("CastorConfigClassLoader exception trying to load class " + className);
		}
	}

	// @Override protected URL findResource(String name)
	// {
	// // return
	// super.findResource(name);
	// }

	/**
	 * Prepend class root folder. Then combine the offset package path and requested className and extract dotted
	 * package/class strings and create a file path.
	 * 
	 * @param className
	 *            the name of the class
	 * @return the class root folder path combined with offset path and the requested class name.
	 */
	private String getOffsetFilePathName(String className) {
		String fileName = classFileRootFolder;

		StringTokenizer s = new StringTokenizer(offsetPackagePath + className, ".");

		while (s.hasMoreTokens()) {
			fileName += s.nextToken() + File.separator;
		}

		return fileName;
	}

	/**
	 * Combine offset package path with requested classname.
	 * 
	 * @param className
	 *            name of the class
	 * @return the combined offset path with the class name
	 */
	private String getOffsetClassName(String className) {
		return offsetPackagePath + className;
	}

	@Override
	public Class<?> loadClass(String className) throws ClassNotFoundException {
		Class<?> theClass = null;

		// return super.loadClass(name);

		// see if requested class is already loaded in the cache
		theClass = classCache.get(className);

		if (theClass != null) {
			// cache the loaded class
			classCache.put(className, theClass);

			return theClass;
		}

		// search for offsetted class file from specified root folder
		// try to load class from a .class file and generate a Java class from
		// it
		theClass = loadClassFromFile(getOffsetFilePathName(className));

		if (theClass != null) {
			// cache the loaded class
			classCache.put(className, theClass);

			return theClass;
		}

		// try to load offsetted class using default parent/system classloader
		// (since offsetted class may be in a jar file)
		theClass = loadClassFromParentLoader(getOffsetClassName(className));

		if (theClass != null) {
			// cache the loaded class
			classCache.put(className, theClass);

			return theClass;
		}

		// last resort - try to load specified class
		// using default parent/system classloader
		theClass = loadClassFromParentLoader(className);

		if (theClass != null) {
			// cache the loaded class
			classCache.put(className, theClass);
		}

		return theClass;
	}

}
