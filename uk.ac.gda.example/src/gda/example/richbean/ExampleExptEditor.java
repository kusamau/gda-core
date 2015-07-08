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

/**
 *
 */
package gda.example.richbean;

import java.net.URL;

import uk.ac.gda.richbeans.editors.DelegatingRichBeanEditorPart;
import uk.ac.gda.richbeans.editors.RichBeanEditorPart;
import uk.ac.gda.richbeans.editors.RichBeanMultiPageEditorPart;

public final class ExampleExptEditor extends RichBeanMultiPageEditorPart {

	@Override
	public Class<?> getBeanClass() {
		return ExampleExpt.class;
	}

	@Override
	public URL getMappingUrl() {
		return ExampleExpt.mappingURL; // Please make sure this field is present and the mapping
	}

	@Override
	public RichBeanEditorPart getRichBeanEditorPart(String path, Object editingBean) {
//		return new ExampleExptUIEditor(path, getMappingUrl(), this, editingBean);

		DelegatingRichBeanEditorPart editor = new DelegatingRichBeanEditorPart(path,getMappingUrl(),this,editingBean);
		editor.setEditorClass(ExampleExptComposite.class);
		editor.setRichEditorTabText("Example Custom UI");
		return editor;
	}

	@Override
	public URL getSchemaUrl() {
		return ExampleExpt.schemaURL; // Please make sure this field is present and the schema
	}

}
