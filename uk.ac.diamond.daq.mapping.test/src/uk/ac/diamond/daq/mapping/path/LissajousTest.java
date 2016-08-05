package uk.ac.diamond.daq.mapping.path;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.scanning.api.points.IPosition;
import org.eclipse.scanning.api.points.Point;
import org.eclipse.scanning.api.points.models.BoundingBox;
import org.junit.Before;
import org.junit.Test;

public class LissajousTest {

	private LissajousGenerator generator;

	@Before
	public void before() throws Exception {
		BoundingBox box = new BoundingBox();
		box.setFastAxisStart(-10);
		box.setSlowAxisStart(5);
		box.setFastAxisLength(3);
		box.setSlowAxisLength(4);

		LissajousModel model = new LissajousModel();
		model.setBoundingBox(box);
		// use default parameters

		generator = new LissajousGenerator();
		generator.setModel(model);
	}

	@Test
	public void testLissajousNoROI() throws Exception {

		// Get the point list
		List<IPosition> pointList = generator.createPoints();

		assertEquals(503, pointList.size());

		// Test a few points
		// TODO check x and y index values - currently these are not tested by AbstractPosition.equals()
		int pointNumber = 0;
		assertEquals(new Point(pointNumber + 1, -8.5, 0, 9.0), pointList.get(pointNumber));
		pointNumber = 100;
		assertEquals(new Point(pointNumber + 1, -9.938386411994712, 0, 7.6306447247905425), pointList.get(pointNumber));
		pointNumber = 300;
		assertEquals(new Point(pointNumber + 1, -7.524568239764414, 0, 5.358881285320901), pointList.get(pointNumber));
	}
}
