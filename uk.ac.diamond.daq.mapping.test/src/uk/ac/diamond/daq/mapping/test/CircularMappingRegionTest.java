package uk.ac.diamond.daq.mapping.test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.eclipse.dawnsci.analysis.dataset.roi.CircularROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.junit.Test;

import uk.ac.diamond.daq.mapping.api.IMappingScanRegionShape;
import uk.ac.diamond.daq.mapping.region.CircularMappingRegion;

public class CircularMappingRegionTest {

	@Test
	public void testGettingBoundingRectange() {
		double xCentre = 15.25;
		double yCentre = -23.65;
		double radius = 5.64;

		CircularMappingRegion circularMappingRegion = new CircularMappingRegion();
		circularMappingRegion.setxCentre(xCentre);
		circularMappingRegion.setyCentre(yCentre);
		circularMappingRegion.setRadius(radius);

		// getPoint() returns bottom left ie min x and min y, getEndPoint() returns top right ie max x max y
		// epsilon is relative to the values * 1e-8 accept a small floating point error
		double epsilon = Math.abs(xCentre + yCentre + radius) * 0.33 * 1e-8;
		assertArrayEquals(new double[] { xCentre - radius, yCentre - radius }, circularMappingRegion.toROI().getBounds().getPoint(), epsilon);
		assertArrayEquals(new double[] { xCentre + radius, yCentre + radius }, circularMappingRegion.toROI().getBounds().getEndPoint(), epsilon);
	}

	@Test
	public void testUpdatingFromROI() {
		double xCentre = 15.25;
		double yCentre = -23.65;
		double radius = 5.64;

		// Create ROI
		CircularROI circularROI = new CircularROI(radius, xCentre, yCentre);

		// Create Region
		CircularMappingRegion circularMappingRegion = new CircularMappingRegion();

		// Update region using ROI
		circularMappingRegion.updateFromROI(circularROI);

		// Check values
		assertEquals("xCentre", xCentre, circularMappingRegion.getxCentre(), xCentre * 1e-8);
		assertEquals("yCentre", yCentre, circularMappingRegion.getyCentre(), yCentre * 1e-8);
		assertEquals("radius", radius, circularMappingRegion.getRadius(), radius * 1e-8);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUpdatingFromInvalidROIType() {
		// Create ROI
		RectangularROI rectangularROI = new RectangularROI();

		// Create Region
		CircularMappingRegion circularMappingRegion = new CircularMappingRegion();

		// Update region using ROI should throw
		circularMappingRegion.updateFromROI(rectangularROI);
	}

	@Test
	public void testCopy() {
		final CircularMappingRegion original = new CircularMappingRegion();
		final IMappingScanRegionShape copy = original.copy();

		assertThat(copy, is(equalTo(original)));
		assertThat(copy, is(not(sameInstance(original))));
	}

	@Test
	public void testCentre() {
		CircularMappingRegion region = new CircularMappingRegion();
		region.setRadius(3.0);
		region.setxCentre(-12);
		region.setyCentre(16.44);

		double targetX = 23.4;
		double targetY = 12.3;

		region.centre(targetX, targetY);

		assertThat(region.getxCentre(), is(targetX));
		assertThat(region.getyCentre(), is(targetY));
		assertThat(region.getRadius(), is(3.0)); // unaffected
	}
}
