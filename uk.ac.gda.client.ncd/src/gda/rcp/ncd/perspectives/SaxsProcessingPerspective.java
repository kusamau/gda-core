/*-
 * Copyright © 2011 Diamond Light Source Ltd.
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

package gda.rcp.ncd.perspectives;

import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IFolderLayout;

public class SaxsProcessingPerspective implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		layout.setEditorAreaVisible(false);
	
		layout.addView("uk.ac.gda.client.ncd.saxsview", IPageLayout.RIGHT, 0.5f, IPageLayout.ID_EDITOR_AREA);
		{
			IFolderLayout folderLayout = layout.createFolder("folder_1", IPageLayout.TOP, 0.15f, "uk.ac.gda.client.ncd.saxsview");
			folderLayout.addView("uk.ac.gda.rcp.views.dashboardView");
		}
		{
			IFolderLayout folderLayout = layout.createFolder("folder", IPageLayout.LEFT, 0.95f, IPageLayout.ID_EDITOR_AREA);
			folderLayout.addView("gda.rcp.jythonterminalview");
		}
		{
			IFolderLayout folderLayout = layout.createFolder("folder_3", IPageLayout.BOTTOM, 0.43f, "folder");
			folderLayout.addView("gda.rcp.ncd.views.StatsAndMathsWithSymmetry");
			folderLayout.addView("gda.rcp.ncd.views.NcdDataSourceSaxs");
			folderLayout.addView("uk.ac.gda.client.ncd.QAxisCalibration");
		}
		{
			IFolderLayout folderLayout = layout.createFolder("folder_2", IPageLayout.BOTTOM, 0.67f, "folder_3");
			folderLayout.addView("uk.ac.gda.client.ncd.NcdButtonPanelView");
			folderLayout.addView("gda.rcp.views.baton.BatonView");
		}
		{
			IFolderLayout folderLayout = layout.createFolder("folder_4", IPageLayout.RIGHT, 0.5f, "folder");
			folderLayout.addPlaceholder("uk.ac.diamond.scisoft.analysis.rcp.views.HistogramView:*");
		}
	}
}