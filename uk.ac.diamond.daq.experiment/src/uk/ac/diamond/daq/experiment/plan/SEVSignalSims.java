package uk.ac.diamond.daq.experiment.plan;

import java.util.function.DoubleSupplier;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gda.device.DeviceException;
import gda.device.Scannable;

/**
 * SEV Signal simulations for testing. Exists in this package
 * because it is useful for runtime testing & demos
 *
 */
public class SEVSignalSims {
	
	private static final Logger logger = LoggerFactory.getLogger(SEVSignalSims.class);
	
	private SEVSignalSims() {
		// do not instantiate
	}
	
	/**
	 * Signal which evolves linearly with each read() call. Useful for tests
	 * @param start
	 * @param step (negative for decreasing signal)
	 * @return
	 */
	public static DoubleSupplier linearEvolution(double start, double step) {
		return new DoubleSupplier() {
			
			private double position = start;
			
			private void advancePosition() {
				position += step;
			}
			
			@Override
			public double getAsDouble() {
				advancePosition();
				return position;
			}
			
			@Override
			public String toString() {
				return "linearEvolution [start = "+start+", step = "+step+"]";
			}
		};
	}
	
	/**
	 * Useful for demos, so we can drive the signal manually.
	 * @param scannable
	 * @return
	 */
	public static DoubleSupplier fromScannable(Scannable scannable) {
		return new DoubleSupplier() {
			private Scannable input = scannable;
			@Override
			public double getAsDouble() {
				try {
					return (double) input.getPosition();
				} catch (DeviceException e) {
					logger.error("Could not read scannable position", e);
					return 0;
				}
			}
			@Override
			public String toString() {
				return "fromScannable [scannable = "+scannable.getName()+"]";
			}
		};
	}
	
	/**
	 * Unfortunately, we cannot pass in a plain Jython lambda.
	 * The lambda must be wrapped in a class extending java.util.function.Function,
	 * with self.apply = [your lambda]
	 * 
	 * @param function 
	 * @return
	 */
	public static DoubleSupplier fromFunction(Function<Double, Double> function) {
		return new DoubleSupplier() {
			
			private Double x = 0.0;
			
			@Override
			public double getAsDouble() {
				x++;
				return function.apply(x);
			}
		};
	}

}
