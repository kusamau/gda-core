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

package gda.util.converters;

import gda.configuration.properties.LocalProperties;
import gda.factory.Findable;

import java.io.File;
import java.util.ArrayList;

import org.jscience.physics.quantities.Quantity;
import org.jscience.physics.units.Unit;

/**
 * class to be generated by the Castor on the ObjectServer to allow conversion between two DOFs using mathematical
 * expressions given in a file.
 * <p>
 * An instance of this object is to be created by the Castor system within the ObjectServer due to presence in the main
 * xml file of content of the from:
 * <p>
 * <preset>&lt;JEPConverter name="AngstromToDeg" expressionFileName="AngstromToDeg.xml"/&gt;</preset>
 * <p>
 * The object implements findable so it can be found via the Jython script using the command
 * <p>
 * <code>converter = finder.find("dcm_energy_perp_converter")</code>
 * <p>
 * The object implements IReloadableQuantitiesConverter so that the lookup table can be re-read using the command:
 * <p>
 * <code>finder.find("dcm_energy_perp_converter").ReloadConverter</code>
 * <p>
 * The object implements IQuantitiesConverter so that the object can be referenced by CombinedDOF.
 * <p>
 * The object implements IQuantityConverter to allow the conversion to be easilty tested using the commands:
 * <p>
 * 
 * <pre>
 *   converter = finder.find(&quot;dcm_energy_perp_converter&quot;)
 *   import  org.jscience.physics.quantities.Quantity as Quantity
 *   q = Quantity.valueOf(-200, converter.AcceptableTargetUnits().get(0))
 *   converter.ToSource(q)
 * </pre>
 * 
 * <p>
 * The expression file is to be found in the folder pointed to be
 * LocalProperties.get(&quot;gda.function.columnDataFile.lookupDir&quot;).
 * <p>
 * The expression file is to be of the form:
 * 
 * <pre>
 *   &lt;JEPQuantityConverter&gt;
 *   &lt;ExpressionTtoS&gt;X/2&lt;/ExpressionTtoS&gt;
 *   &lt;ExpressionStoT&gt;2X&lt;/ExpressionStoT&gt;
 *   &lt;AcceptableSourceUnits&gt;Angstrom&lt;/AcceptableSourceUnits&gt;
 *   &lt;AcceptableTargetUnits&gt;mm&lt;/AcceptableTargetUnits&gt;
 *   &lt;/JEPQuantityConverter&gt;
 * </pre>
 */
public final class JEPConverterHolder implements IReloadableQuantitiesConverter, Findable, IQuantityConverter
{
	private GenQuantitiesConverter converter = null;

	private final String expressionFileName, name;

	/**
	 * Creates a JEPConverterHolder with the specified name which loads
	 * converter information from the specified file.
	 * 
	 * @param name the converter name
	 * @param expressionFileName the converter expression filename
	 */
	public JEPConverterHolder(String name, String expressionFileName) {
		this.name = name;
		this.expressionFileName = determineFileLocation(expressionFileName);
	}
	
	private String determineFileLocation(String filename) {
		if (fileExists(filename)) {
			return filename;
		}
		
		String lookupTableFolder = LocalProperties.get("gda.function.columnDataFile.lookupDir");
		File fullPath = new File(lookupTableFolder, filename);
		return fullPath.getAbsolutePath();
	}
	
	private boolean fileExists(String filename) {
		return new File(filename).exists();
	}

	@Override
	public String getName() {
		return name;
	}

	/**
	 * Returns the filename of the expression file.
	 * 
	 * @return the expression filename
	 */
	public String getExpressionFileName() {
		return expressionFileName;
	}

	@Override
	public void setName(String name) {
		// I need to support the function but will not do anything with the name
		// as I do not want to allow the name to be changed after construction
		// this.name = name;
		throw new IllegalArgumentException("JEPConverterHolder.setName() : Error this should not be called");
	}

	@Override
	public void reloadConverter() {
		// To reduce race conditions create a brand new converter rather than
		// change existing which may be already being accessed on other threads
		try {
			GenQuantitiesConverter newJEPConverter = new GenQuantitiesConverter(new JEPQuantityConverter(expressionFileName));
			if (converter != null) {
				LookupTableConverterHolder.CheckUnitsAreEqual(converter, newJEPConverter);
			}

			converter = newJEPConverter;
		} catch (Exception e) {
			String msg = "JEPConverterHolder.ReloadConverter: Exception seen in " + toString(false) + ". "
					+ e.getMessage();
			throw new RuntimeException(msg, e);
		}
	}

	private synchronized GenQuantitiesConverter getConverter() {
		if (converter == null) {
			reloadConverter();
		}
		return converter;
	}

	@Override
	public ArrayList<ArrayList<Unit<? extends Quantity>>> getAcceptableMoveableUnits() {
		return getConverter().getAcceptableMoveableUnits();
	}

	@Override
	public ArrayList<ArrayList<Unit<? extends Quantity>>> getAcceptableUnits() {
		return getConverter().getAcceptableUnits();
	}

	@Override
	public Quantity[] calculateMoveables(Quantity[] sources, Object[] moveables) throws Exception {
		return getConverter().calculateMoveables(sources, moveables);
	}

	@Override
	public Quantity[] toSource(Quantity[] targets, Object[] moveables) throws Exception {
		return getConverter().toSource(targets, moveables);
	}

	@Override
	public String toString() {
		return toString(true);
	}

	// function to be used when creating an exeption caused by attempts to
	// reload the converter.
	private String toString(boolean callGetConverter) {
		// we may be being called within getConverter
		return (callGetConverter && (getConverter() != null)) ? getConverter().toString()
				: "JEPQuantityConverter using details in " + expressionFileName;
	}

	@Override
	public ArrayList<Unit<? extends Quantity>> getAcceptableSourceUnits() {
		return getConverter().getAcceptableSourceUnits();
	}

	@Override
	public ArrayList<Unit<? extends Quantity>> getAcceptableTargetUnits() {
		return getConverter().getAcceptableTargetUnits();
	}

	@Override
	public Quantity toSource(Quantity target) throws Exception {
		return getConverter().toSource(target);
	}

	@Override
	public Quantity toTarget(Quantity source) throws Exception {
		return getConverter().toTarget(source);
	}

	@Override
	public boolean sourceMinIsTargetMax() {
		return getConverter().sourceMinIsTargetMax();
	}
	@Override
	public boolean handlesStoT() {
		return getConverter().handlesStoT();
	}

	@Override
	public boolean handlesTtoS() {
		return getConverter().handlesTtoS();
	}

}
