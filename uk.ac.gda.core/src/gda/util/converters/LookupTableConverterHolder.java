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

import gda.factory.Findable;

import java.io.File;
import java.util.ArrayList;

import org.jscience.physics.quantities.Quantity;
import org.jscience.physics.units.Unit;

/**
 * class to be generated by the Castor on the ObjectServer to allow conversion between two DOFs using a lookup table.
 * <p>
 * An instance of this object is to be created by the Castor system within the ObjectServer due to presence in the main
 * xml file of content of the from:
 * <p>
 * <preset>&lt;LookupTableConverter name="dcm_energy_perp_converter" columnDataFileName="dcm_energy_perp.txt"
 * sColumn="0" tColumn="1" mode="StoT"/&gt;</preset>
 * <p>
 * <code>name</code> The text by which the object can be located using the Finder
 * <p>
 * <code>columnDataFileName</code> The name of the lookup file. This can be an absolute filename, or a
 * relative filename (in which case {@code gda.function.columnDataFile.lookupDir} will be prepended to
 * the filename).
 * <p>
 * The lookup table is to be found in the folder pointed to be
 * LocalProperties.get("gda.function.columnDataFile.lookupDir").
 * <p>
 * The lookup table is read by the class ColumnDataFile and so must conform to the format that that class requires.
 * <p>
 * <code>sColumn</code> Zero based index to identify the column that contains the values for the Source
 * <p>
 * <code>tColumn</code> Zero based index to identify the column that contains the values for the Target
 * <p>
 * <code>mode</code> Indicates the direction in which the conversion is valid
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
 * The object implements IQuantityConverter to allow the conversion to be easily tested using the commands:
 * <p>
 * 
 * <pre>
 *      converter = finder.find(&quot;dcm_energy_perp_converter&quot;)
 *      import  org.jscience.physics.quantities.Quantity as Quantity
 *      q = Quantity.valueOf(-200, converter.AcceptableTargetUnits().get(0))
 *      converter.ToSource(q)
 * </pre>
 * 
 * @see gda.util.converters.LookupTableQuantityConverter#getMode( String modeString )
 */
public final class LookupTableConverterHolder implements IReloadableQuantitiesConverter, Findable, IQuantityConverter {
	private GenQuantitiesConverter converter = null;

	private final String columnDataFileName, name, modeString;

	private final int sColumn, tColumn;

	private  boolean interpolateNotExtrapolate = false;

	/**
	 * @param name
	 *            The name of the object. This is required to allow the object to be found via the Finder.
	 * @param columnDataFileName
	 *            Lookup table filename. The lookup table is to be found in the folder pointed to be
	 *            LocalProperties.get("gda.function.columnDataFile.lookupDir"). The lookup table is read by the class
	 *            ColumnDataFile and so must conform to the format that that class requires.
	 * @param sColumn
	 *            The column of the lookup table that contains the source values. First column is 0.
	 * @param tColumn
	 *            The column of the lookup table that contains the target values. First column is 0.
	 * @param modeString
	 *            If not null this is a String that indicates the direction for which the conversion is valid In some
	 *            circumstances the lookup table is only valid for conversion in one direction e.g. The xvalues all
	 *            increase but the yvalues show a change in direction.
	 * @see gda.util.converters.LookupTableQuantityConverter#getMode( String modeString )
	 */
	public LookupTableConverterHolder(String name, String columnDataFileName, int sColumn, int tColumn,
			String modeString) {
		this.name = name;
		this.columnDataFileName = columnDataFileName;
		this.sColumn = sColumn;
		this.tColumn = tColumn;
		this.modeString = (modeString != null) ? modeString : LookupTableQuantityConverter.Mode_Both;
	}

	/**
	 * @see gda.factory.Findable#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Castor function. - I am using the constructor mapping option for set-methods so that members can be final
	 * 
	 * @return Lookup table filename. The lookup table is to be found in the folder pointed to be
	 *         LocalProperties.get("gda.function.columnDataFile.lookupDir")
	 */
	public String getColumnDataFileName() {
		return columnDataFileName;
	}

	/**
	 * Castor function. - I am using the constructor mapping option for set-methods so that members can be final
	 * 
	 * @return The column of the lookup table that contains the source values. First column is 0.
	 */
	public int getSColumn() {
		return sColumn;
	}

	/**
	 * Castor function. - I am using the constructor mapping option for set-methods so that members can be final
	 * 
	 * @return The column of the lookup table that contains the target values. First column is 0.
	 */
	public int getTColumn() {
		return tColumn;
	}

	/**
	 * Castor function. - I am using the constructor mapping option for set-methods so that members can be final
	 * 
	 * @return The column of the lookup table that contains the target values. First column is 0.
	 */
	public String getMode() {
		return modeString;
	}
	
	public boolean isInterpolateNotExtrapolate() {
		return interpolateNotExtrapolate;
	}

	public void setInterpolateNotExtrapolate(boolean interpolateNotExtrapolate) {
		this.interpolateNotExtrapolate = interpolateNotExtrapolate;
	}

	/**
	 * Castor function. - This should never be called and will throw an exception if it is. {@inheritDoc}
	 * 
	 * @see gda.factory.Findable#setName(java.lang.String)
	 */
	@Override
	public void setName(String name) throws IllegalArgumentException {
		// I need to support the function but will not do anything with the name
		// as I do not want to allow the name to be changed after construction
		// this.name = name;
		throw new IllegalArgumentException("LookupTableConverterHolder.setName() : Error this should not be called");
	}

	static boolean UnitsAreEqual1(ArrayList<Unit<? extends Quantity>> o, ArrayList<Unit<? extends Quantity>> n) {
		if (o.size() != n.size())
			return false;

		for (int i = 0; i < o.size(); i++) {
			if (!o.get(i).equals(n.get(i))) {
				return false;
			}
		}
		return true;
	}

	static boolean UnitsAreEqual(ArrayList<ArrayList<Unit<? extends Quantity>>> o,
			ArrayList<ArrayList<Unit<? extends Quantity>>> n) {
		if (o.size() != n.size())
			return false;

		for (int i = 0; i < o.size(); i++) {
			if (!UnitsAreEqual1(o.get(i), n.get(0))) {
				return false;
			}
		}
		return true;
	}

	static void CheckUnitsAreEqual(IQuantitiesConverter o, IQuantitiesConverter n) {
		if (o == null || n == null) {
			throw new IllegalArgumentException("LookupTableConverterHolder.CheckUnitsAreEqual() : o or n is null ");
		}
		ArrayList<ArrayList<Unit<? extends Quantity>>> newAcceptableUnits = n.getAcceptableUnits();
		if (!UnitsAreEqual(newAcceptableUnits, o.getAcceptableUnits())) {
			throw new IllegalArgumentException(
					"LookupTableConverterHolder.CheckUnitsAreEqual() : AcceptableUnits have changed from "
							+ o.getAcceptableUnits().toString() + " to " + newAcceptableUnits.toString());
		}

		ArrayList<ArrayList<Unit<? extends Quantity>>> newAcceptableMoveableUnits = n.getAcceptableMoveableUnits();
		if (!UnitsAreEqual(newAcceptableMoveableUnits, o.getAcceptableMoveableUnits())) {
			throw new IllegalArgumentException(
					"LookupTableConverterHolder.CheckUnitsAreEqual() : AcceptableMoveableUnits have changed from "
							+ o.getAcceptableMoveableUnits().toString() + " to "
							+ newAcceptableMoveableUnits.toString());
		}
	}

	/**
	 * Re-reads the lookup table. Note that the units of the lookup must not change. {@inheritDoc}
	 * 
	 * @see gda.util.converters.IReloadableQuantitiesConverter#reloadConverter()
	 */
	@Override
	public void reloadConverter() {
		// To reduce race conditions create a brand new converter rather than
		// change existing which may be already being accessed on other threads
		try {
			
			final boolean filenameIsFull = checkWhetherFilenameIsFull(columnDataFileName);
			GenQuantitiesConverter newConverter = new GenQuantitiesConverter(new LookupTableQuantityConverter(
					columnDataFileName, filenameIsFull, sColumn, tColumn, LookupTableQuantityConverter.getMode(modeString), !interpolateNotExtrapolate));
			if (converter != null) {
				CheckUnitsAreEqual(converter, newConverter);
			}
			converter = newConverter;
		} catch (Exception e) {
			throw new RuntimeException("Could not reload lookup table converter (" + toString(false) + ")", e);
		}
	}
	
	protected static boolean checkWhetherFilenameIsFull(String filename) {
		return new File(filename).isAbsolute();
	}

	/**
	 * @see gda.util.converters.IQuantitiesConverter#getAcceptableMoveableUnits()
	 */
	@Override
	public ArrayList<ArrayList<Unit<? extends Quantity>>> getAcceptableMoveableUnits() {
		return getConverter().getAcceptableMoveableUnits();
	}

	/**
	 * @see gda.util.converters.IQuantitiesConverter#getAcceptableUnits()
	 */
	@Override
	public ArrayList<ArrayList<Unit<? extends Quantity>>> getAcceptableUnits() {
		return getConverter().getAcceptableUnits();
	}

	/**
	 * Uses the lookup table to calculate values for the moveables given the values in sources.
	 * 
	 * @see gda.util.converters.IQuantitiesConverter#calculateMoveables(org.jscience.physics.quantities.Quantity[],
	 *      java.lang.Object[])
	 */
	@Override
	public Quantity[] calculateMoveables(Quantity[] sources, Object[] moveables) throws Exception {
		return getConverter().calculateMoveables(sources, moveables);
	}

	/**
	 * Uses the lookup table to calculate values for the sources given the values in targets.
	 * 
	 * @see gda.util.converters.IQuantitiesConverter#toSource(org.jscience.physics.quantities.Quantity[],
	 *      java.lang.Object[])
	 */
	@Override
	public Quantity[] toSource(Quantity[] targets, Object[] moveables) throws Exception {
		return getConverter().toSource(targets, moveables);
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return toString(true);
	}

	// function to be used when creating an exeption caused by attempts to
	// reload the converter.
	private String toString(boolean callGetConverter) {
		// we may be being called within getConverter
		return (callGetConverter && (getConverter() != null)) ? getConverter().toString()
				: "LookupTableQuantityConverter using details in " + columnDataFileName + ". sColumn=" + sColumn
						+ " tColumn=" + tColumn + " mode=" + modeString;
	}

	/**
	 * @see gda.util.converters.IQuantityConverter#getAcceptableSourceUnits()
	 */
	@Override
	public ArrayList<Unit<? extends Quantity>> getAcceptableSourceUnits() {
		return getConverter().getAcceptableSourceUnits();
	}

	/**
	 * @see gda.util.converters.IQuantityConverter#getAcceptableTargetUnits()
	 */
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

	/**
	 * Helper function to ensure the converter is created before any attempt to use it.
	 * 
	 * @return QuantityConverter
	 */
	private synchronized GenQuantitiesConverter getConverter() {
		if (converter == null) {
			reloadConverter();
		}
		return converter;
	}

	/**
	 * @see gda.util.converters.IQuantitiesConverter#sourceMinIsTargetMax()
	 */
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
