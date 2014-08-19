package org.opengda.lde.ui.viewfactories;

import gda.rcp.views.FindableExecutableExtension;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.opengda.lde.ui.views.LiveImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveImageViewFactory implements FindableExecutableExtension {
	private static final Logger logger=LoggerFactory.getLogger(LiveImageViewFactory.class);
	private String viewPartName;
	private String name;
	private String arrayPV;
	private String arrayEnablePV;
	private int xDimension;
	private int yDimension;
	@Override
	public Object create() throws CoreException {
		logger.info("Creating image plot view");
		LiveImageView imageView = new LiveImageView();
		imageView.setViewPartName(viewPartName);
		imageView.setArrayPV(arrayPV);
		imageView.setArrayEnablePV(arrayEnablePV);
		imageView.setxDimension(xDimension);
		imageView.setyDimension(yDimension);
		return imageView;
	}

	@Override
	public void setName(String name) {
		this.name=name;		
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setInitializationData(IConfigurationElement config,
			String propertyName, Object data) throws CoreException {
		
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if (arrayPV == null ) {
			throw new IllegalArgumentException("'arrayPV' cannot be null in image View.");
		}
		if (arrayEnablePV == null ) {
			throw new IllegalArgumentException("'arrayEnablePV' cannot be null in image View.");
		}
		if (xDimension == 0 ) {
			throw new IllegalArgumentException("'xDimension' of a image cannot be 0.");
		}
		if (yDimension == 0 ) {
			throw new IllegalArgumentException("'yDimension' of a image cannot be 0.");
		}
		
	}

	public String getViewPartName() {
		return viewPartName;
	}

	public void setViewPartName(String viewPartName) {
		this.viewPartName = viewPartName;
	}

	public String getArrayPV() {
		return arrayPV;
	}

	public void setArrayPV(String arrayPV) {
		this.arrayPV = arrayPV;
	}

	public int getyDimension() {
		return yDimension;
	}

	public void setyDimension(int yDimension) {
		this.yDimension = yDimension;
	}

	public int getxDimension() {
		return xDimension;
	}

	public void setxDimension(int xDimension) {
		this.xDimension = xDimension;
	}

	public String getArrayEnablePV() {
		return arrayEnablePV;
	}

	public void setArrayEnablePV(String arrayEnablePV) {
		this.arrayEnablePV = arrayEnablePV;
	}

}
