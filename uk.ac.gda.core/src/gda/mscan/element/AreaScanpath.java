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

package gda.mscan.element;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.eclipse.scanning.api.points.models.AbstractBoundingBoxModel;
import org.eclipse.scanning.api.points.models.BoundingBox;
import org.eclipse.scanning.api.points.models.GridModel;
import org.eclipse.scanning.api.points.models.IScanPathModel;
import org.eclipse.scanning.api.points.models.LissajousModel;
import org.eclipse.scanning.api.points.models.RandomOffsetGridModel;
import org.eclipse.scanning.api.points.models.RasterModel;
import org.eclipse.scanning.api.points.models.SpiralModel;

import com.google.common.collect.ImmutableMap;

import gda.device.Scannable;

/**
 * Links the revised mscan syntax to corresponding scanning model factory functions in a typed way for paths
 * that occupy a two dimensional area. The abbreviation to specify a path is linked to the model via the
 * constructor along with the number of parameters required to specify the path (seed count). The factory
 * functions need to be a nested static class so that they are in scope when the constructor is called.
 *
 * @since GDA 9.9
 */
public enum AreaScanpath implements IMScanElementEnum {
	GRID("grid", 2, GridModel.class, Factory::createGridModel),
	RASTER("rast", 2, RasterModel.class, Factory::createRasterModel),
	SPIRAL("spir", 1, SpiralModel.class, Factory::createSpiralModel),
	LISSAJOUS("liss", 5, LissajousModel.class, Factory::createLissajousModel);

	private static final int NUMBER_OF_AXES = 2;
	private static final int BBOX_REQUIRED_PARAMS = 4;
	private static final String PREFIX = "Invalid Scan clause: ";

	private final String text;
	/** The number of parameters required to generate the path **/
	private final int valueCount;
	private final Class<? extends AbstractBoundingBoxModel> modelType;
	private final AreaScanpathModelFactoryFunction factory;

	private AreaScanpath(final String text, final int valueCount,
						final Class<? extends AbstractBoundingBoxModel> type,
						final AreaScanpathModelFactoryFunction factoryFunction) {
		this.text = text;
		this.valueCount = valueCount;
		this.modelType = type;
		this.factory= factoryFunction;
	}

	/**
	 * The number of values required to construct the path
	 *
	 * @return		The number of values required to construct the path
	 */
	public int valueCount() {
		return valueCount;
	}

	/**
	 * The default instance value to be used if one is not specified in the scan command
	 *
	 * @return		The {@link #GRID} instance
	 */
	public static AreaScanpath defaultValue() {
		return RASTER;
	}

	/**
	 * The default text values that correspond to the instances of AreaScanpath
	 *
	 * @return		List of default text for the instances
	 */
	public static List<String> strValues() {
		return stream(values()).map(val -> val.text).collect(toList());
	}

	/**
	 * The type of model that a particular instance's {@link #createModel} method will construct
	 *
	 * @return		The {@link AbstractBoundingBoxModel} based model type associated with the instance.
	 */
	public Class<? extends AbstractBoundingBoxModel> modelType() {
		return modelType;
	}

	/**
	 * Creates the correct {@link AbstractBoundingBoxModel} based object for this instance of AreaScanpath
	 * The number of supplied {@link Scannable}s, path parameters and bounding box parameters will be validated
	 * against their respective required numbers.
	 *
	 * @param scannables		The {@link List} of scannables associated with the path
	 * @param pathParams		The {@link List} of numeric parameters used to generate the path
	 * @param bboxParams		The {@link List} of bounding box parametes that define the extent of the path
	 * @param mutatorUses		The {@link Map} of mutators to their parameters to be applied to the path
	 *
	 * @return					The {@link IScanPathModel} of the constructed model object
	 *
	 * @throws 					IllegalArgumentException if the validation of parameters or {@link Mutator}s fails
	 */
	public IScanPathModel createModel(final List<Scannable> scannables, final List<Number> pathParams,
										final List<Number> bboxParams, final Map<Mutator, List<Number>> mutatorUses) {
		validateInputs(ImmutableMap.of(scannables, NUMBER_OF_AXES,
										pathParams, valueCount,
										bboxParams, BBOX_REQUIRED_PARAMS));
		if (this != GRID && mutatorUses.containsKey(Mutator.RANDOM_OFFSET)) {
			throw new IllegalArgumentException(PREFIX + "Only Grid Model supports Random Offset paths");
		}

		if (this != GRID && this != RASTER  && mutatorUses.containsKey(Mutator.SNAKE)) {
			throw new IllegalArgumentException(PREFIX + "Only Grid and Raster Models support Snake paths");
		}
		return factory.createScanpathModel(scannables, pathParams, bboxParams, mutatorUses);
	}

	/**
	 * Check that the correct number of all required parameters has been supplied for the required AreaScanpath
	 * covering Scannables, path parameters and bounding box parameters.
	 *
	 * @param inputs			An {@link Map} of lists to their expected sizes
	 * @param pathName			The name of the required path for Exception message purposes
	 *
	 * @throws					IllegalArgument exception if the required number of parameters is not present.
	 */
	private void validateInputs(final Map<List<? extends Object>, Integer> inputs) {
		inputs.entrySet().forEach(entry ->{
			if (entry.getKey().size() != entry.getValue()) {
				throw new IllegalArgumentException(String.format(
						"%s%s requires %s numeric values to be specified",
							PREFIX, modelType.getSimpleName(), entry.getValue()));
			}
		});
	}

	/**
	 * This Class contains factory methods for each of the required {@link AbstractBoundingBoxModel} based types
	 *  mapped by the instance constructor. It also defines constants to allow the various parameters required for
	 *  construction of the required {@link IScanPathModel} to be expressed in a more meaningful way.
	 */
	private static class Factory {
		// Constants to reference the axes of the various models via the supplied {@link List} of {@link Scannables}
		private static final int FAST = 0;
		private static final int SLOW = 1;

		// Constants to reference the available parameters for a {@link RandomOffsetGridModel}
		private static final int OFFSET = 0;
		private static final int SEED = 1;

		// Constants to reference the bounding box coordinates via the supplied {@link List} of {@link Number}s
		// (bboxParameters)
		private static final int FAST_START = 0;
		private static final int SLOW_START = 1;
		private static final int FAST_LENGTH = 2;
		private static final int SLOW_LENGTH = 3;

		// Constant to reference the available parameter of a {@link SpiralModel}
		private static final int SCALE = 0;

		/**
		 * Creates a {@link GridModel} using the supplied params. If the RandomOffset {@link Mutator} is specified, a
		 * {@link RandomOffsetGridModel} is created instead.
		 *
		 * @param scannables		The {@link Scannable}s that relate to the axes of the grid as a {@link List} in the
		 * 							order: fastScannable, slowScannable
		 * @param scanParameters	The number of points in each direction of the grid as a {@link List} in the order:
		 * 							nFast, nSlow
		 * @param bboxParameters	The coordinates of one corner of the rectangular bounding box that	encloses the
		 * 							grid plus its width and height as a {@link List} in the order x, y, width, height
		 * @param mutatorUses		A {@link Map} of mutators to their parameters to be applied to the path
		 * @return					An {@link IScanPathModel} of the requested path and features
		 */
		private static IScanPathModel createGridModel ( final List<Scannable> scannables,
				 										final List<Number> scanParameters,
				 										final List<Number> bboxParameters,
				 										final Map<Mutator, List<Number>> mutatorUses) {

			for (Number param : scanParameters) {
				if (param.doubleValue() < 0) {
					throw new IllegalArgumentException(PREFIX + "Grid requires all positive parameters");
				}
				if (!(param instanceof Integer)) {
					throw new IllegalArgumentException(PREFIX + "Grid requires integer parameters");
				}
			}
			GridModel model;
			if (mutatorUses.containsKey(Mutator.RANDOM_OFFSET)) {
				RandomOffsetGridModel roModel = new RandomOffsetGridModel(
					scannables.get(FAST).getName(),
					scannables.get(SLOW).getName());
				roModel.setFastAxisPoints(scanParameters.get(FAST).intValue());
				roModel.setSlowAxisPoints(scanParameters.get(SLOW).intValue());
				List<Number> params = mutatorUses.get(Mutator.RANDOM_OFFSET);
				roModel.setOffset(params.get(OFFSET).doubleValue());
				if (params.size() > 1) {
					roModel.setSeed(mutatorUses.get(Mutator.RANDOM_OFFSET).get(SEED).intValue());
				}
				model = roModel;
			} else {
				model = new GridModel(
						scannables.get(FAST).getName(),
						scannables.get(SLOW).getName(),
						scanParameters.get(FAST).intValue(),
						scanParameters.get(SLOW).intValue());
			}
			model.setBoundingBox(new BoundingBox(
					bboxParameters.get(FAST_START).doubleValue(), bboxParameters.get(SLOW_START).doubleValue(),
					bboxParameters.get(FAST_LENGTH).doubleValue(), bboxParameters.get(SLOW_LENGTH).doubleValue()));

			if (mutatorUses.containsKey(Mutator.SNAKE)) {
				model.setSnake(true);
			}
			return model;
		}

		/**
		 * Creates a {@link RasterModel} using the supplied params.
		 *
		 * @param scannables		The {@link Scannable}s that relate to the axes of the raster as a {@link List} in
		 * 							the order: fastScannable, slowScannable
		 * @param scanParameters	The step size in each direction of the raster as a {@link List} in the order:
		 * 							fastStep, slowStep
		 * @param bboxParameters	The coordinates of one corner of the rectangular bounding box that	encloses the
		 * 							raster plus its width and height as a {@link List} in the order x, y, width, height
		 * @param mutatorUses		A {@link Map} of mutators to their parameters to be applied to the path
		 * @return					An {@link IScanPathModel} of the requested path and features
		 */
		private static IScanPathModel createRasterModel (final List<Scannable> scannables,
														 final List<Number> scanParameters,
														 final List<Number> bboxParameters,
														 final Map<Mutator, List<Number>> mutatorUses) {

			for (Number param : scanParameters) {
				if (param.doubleValue() < 0) {
					throw new IllegalArgumentException(PREFIX + "Raster requires all positive parameters");
				}
			}
			RasterModel model = new RasterModel(scannables.get(FAST).getName(),scannables.get(SLOW).getName());
			model.setFastAxisStep(scanParameters.get(FAST).doubleValue());
			model.setSlowAxisStep(scanParameters.get(SLOW).doubleValue());
			model.setBoundingBox(new BoundingBox(
					bboxParameters.get(FAST_START).doubleValue(), bboxParameters.get(SLOW_START).doubleValue(),
					bboxParameters.get(FAST_LENGTH).doubleValue(), bboxParameters.get(SLOW_LENGTH).doubleValue()));

			if (mutatorUses.containsKey(Mutator.SNAKE)) {
				model.setSnake(true);
			}
			return model;
		}

		/**
		 * Creates a {@link SpiralModel} using the supplied params.
		 *
		 * @param scannables		The {@link Scannable}s that relate to the axes of the spiral as a {@link List} in
		 * 							the order: fastScannable, slowScannable
		 * @param scanParameters	The required scale factor for the spiral as a single element {@link List}
		 * @param bboxParameters	The coordinates of one corner of the rectangular bounding box that	encloses the
		 * 							spiral plus its width and height as a {@link List} in the order x, y, width, height
		 * @param mutatorUses		A {@link Map} of mutators to their parameters to be applied to the path
		 * @return					An {@link IScanPathModel} of the requested path and features
		 */
		private static IScanPathModel createSpiralModel (final List<Scannable> scannables,
														 final List<Number> scanParameters,
														 final List<Number> bboxParameters,
														 final Map<Mutator, List<Number>> mutatorUses) {

			SpiralModel model = new SpiralModel(scannables.get(FAST).getName(), scannables.get(SLOW).getName());
			model.setScale(scanParameters.get(SCALE).doubleValue());
			model.setBoundingBox(new BoundingBox(
					bboxParameters.get(FAST_START).doubleValue(), bboxParameters.get(SLOW_START).doubleValue(),
					bboxParameters.get(FAST_LENGTH).doubleValue(), bboxParameters.get(SLOW_LENGTH).doubleValue()));
			return model;
		}
		/**
		 * Creates a {@link LissajousModel} using the supplied params.
		 *
		 * @param scannables		The {@link Scannable}s that relate to the axes of the lissajous as a {@link List} in
		 * 							the order: fastScannable, slowScannable
		 * @param scanParameters	The parameters that define the lissajous path as a {@link List} in the order:
		 * 							A, B, Delta, ThetaStep, nunberOfPoints
		 * @param bboxParameters	The coordinates of diagonally opposite corners of the rectangular bounding box that
		 * 							encloses the lissajous as a {@link List} in the order x1, y1, x2, y2
		 * @param mutatorUses		A {@link Map} of mutators to their parameters to be applied to the path
		 * @return					An {@link IScanPathModel} of the requested path and features
		 */
		private static IScanPathModel createLissajousModel (final List<Scannable> scannables,
															final List<Number> scanParameters,
															final List<Number> bboxParameters,
															final Map<Mutator, List<Number>> mutatorUses) {

			LissajousModel model = new LissajousModel();
			model.setFastAxisName(scannables.get(FAST).getName());
			model.setSlowAxisName(scannables.get(SLOW).getName());
			model.setA(scanParameters.get(0).doubleValue());
			model.setB(scanParameters.get(1).doubleValue());
			model.setDelta(scanParameters.get(2).doubleValue());
			model.setThetaStep(scanParameters.get(3).doubleValue());
			model.setPoints(scanParameters.get(4).intValue());
			model.setBoundingBox(new BoundingBox(
					bboxParameters.get(FAST_START).doubleValue(), bboxParameters.get(SLOW_START).doubleValue(),
					bboxParameters.get(FAST_LENGTH).doubleValue(), bboxParameters.get(SLOW_LENGTH).doubleValue()));
			return model;
		}
	}
}
