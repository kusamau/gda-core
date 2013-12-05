package uk.ac.gda.example.test.mvcexample.MvcExampleView;

import gda.rcp.util.OSGIServiceRegister;

import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.gda.example.mvcexample.MvcExampleModel;
import uk.ac.gda.example.mvcexample.MvcExampleView;

public class MvcExampleViewPluginUnitTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetBtnSelected() throws Exception {
		MyMvcExampleModel model = new MyMvcExampleModel();
		
		OSGIServiceRegister modelReg = new OSGIServiceRegister();
		modelReg.setClass(MvcExampleModel.class);
		modelReg.setService(model);
		modelReg.afterPropertiesSet();		
		
		
		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		MvcExampleView view = (MvcExampleView)window.getActivePage().showView(MvcExampleView.ID);
		window.getActivePage().activate(view);
		
		ActionFactory.IWorkbenchAction  maximizeAction = ActionFactory.MAXIMIZE.create(window);
		maximizeAction.run(); // Will maximize the active part		
		for(int i=0; i<60; i++){
			delay(1000);
			model.setSelected(!model.isSelected());
		}
		
	}
	/**
	 * Process UI input but do not return for the specified time interval.
	 * 
	 * @param waitTimeMillis
	 *            the number of milliseconds
	 */
	private void delay(long waitTimeMillis) {
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
	
	class MyMvcExampleModel implements MvcExampleModel{
		boolean selected;

		public boolean isSelected() {
			return selected;
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
		}

	};

}
