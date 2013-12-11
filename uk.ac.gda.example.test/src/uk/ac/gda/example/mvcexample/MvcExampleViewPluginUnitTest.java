package uk.ac.gda.example.mvcexample;

import gda.device.ScannableMotionUnits;
import gda.device.motor.DummyMotor;
import gda.device.scannable.ScannableMotor;
import gda.rcp.util.OSGIServiceRegister;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import junit.framework.Assert;

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.gda.client.observablemodels.ScannableWrapper;

public class MvcExampleViewPluginUnitTest {

	private static MvcExampleView view;
	private static MvcExampleView view2;
	private static MyMvcExampleModel model;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		model = new MyMvcExampleModel();

		OSGIServiceRegister modelReg = new OSGIServiceRegister();
		modelReg.setClass(MvcExampleModel.class);
		modelReg.setService(model);
		modelReg.afterPropertiesSet();

		final IWorkbenchWindow window = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow();
		view = (MvcExampleView) window.getActivePage().showView(
				MvcExampleView.ID);
		window.getActivePage().activate(view);
		ActionFactory.IWorkbenchAction maximizeAction = ActionFactory.MAXIMIZE
				.create(window);
		maximizeAction.run(); // Will maximize the active part

		view2 = (MvcExampleView) window.getActivePage().showView(
				"uk.ac.gda.example.mvcexample.MvcExampleViewTest");
		window.getActivePage().activate(view2);

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		waitForJobs();
		delay(20000);
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.hideView(view);
		PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
				.hideView(view2);
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetBtnSelected() throws Exception {
		model.setSelected(false);
		Assert.assertEquals(false, view.btn1.getSelection());
		model.setSelected(true);
		Assert.assertEquals(true, view.btn1.getSelection());
		
	}

	/**
	 * Process UI input but do not return for the specified time interval.
	 * 
	 * @param waitTimeMillis
	 *            the number of milliseconds
	 */
	private static void delay(long waitTimeMillis) {
		Display display = Display.getCurrent();

		// If this is the UI thread,
		// then process input.

		if (display != null) {
			long endTimeMillis = System.currentTimeMillis() + waitTimeMillis;
			while (System.currentTimeMillis() < endTimeMillis) {
				if (!display.readAndDispatch())
					display.sleep();
			}
			display.update();
		}
		// Otherwise, perform a simple sleep.

		else {
			try {
				Thread.sleep(waitTimeMillis);
			} catch (InterruptedException e) {
				// Ignored.
			}
		}
	}

	/**
	 * Wait until all background tasks are complete.
	 */
	public static void waitForJobs() {
		while (!Job.getJobManager().isIdle())
			delay(1000);
	}
}

class MyMvcExampleModel implements MvcExampleModel {
	private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

	@Override
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		this.pcs.addPropertyChangeListener(listener);
	}

	@Override
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		this.pcs.removePropertyChangeListener(listener);
	}

	boolean selected;
	ScannableMotionUnits scannable;

	ScannableWrapper wrapper;

	@Override
	public boolean isSelected() {
		return selected;
	}

	@Override
	public void setSelected(boolean selected) {
		this.pcs.firePropertyChange(MvcExampleModel.SELECTED_PROPERTY_NAME,
				this.selected, this.selected = selected);
	}

	double position;

	@Override
	public double getPosition() {
		return position;
	}

	@Override
	public void setPosition(double position) {
		this.pcs.firePropertyChange(MvcExampleModel.POSITION_PROPERTY_NAME,
				this.position, this.position = position);
	}

	@Override
	public ScannableWrapper getScannableWrapper() throws Exception {
		if (wrapper == null) {
			DummyMotor dummyMotor = new DummyMotor();
			dummyMotor.setName("dummy_motor");
			dummyMotor.configure();
			ScannableMotor scannable = new ScannableMotor();
			scannable.setMotor(dummyMotor);
			scannable.setName("motor1");
			scannable.configure();
			wrapper = new ScannableWrapper(scannable);
		}
		return wrapper;
	}

};
