/*-
 * Copyright © 2015 Diamond Light Source Ltd.
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

package uk.ac.gda.eventbus.api.message;

import java.io.Serializable;

import com.google.common.base.Optional;

public abstract class GDAMessage implements Serializable, IGDAMessage {

	private static final long serialVersionUID = 165830281177522976L;

	private Optional<?> sourceToken = null;
	private GDAMessageCategory category = GDAMessageCategory.NOTIFY;
	private String message = "";

	public GDAMessage() {
		this(GDAMessageCategory.NOTIFY);
	}

	public GDAMessage(String message) {
		this(null,message);
	}

	public GDAMessage(GDAMessageCategory category) {
		this(null,category);
	}

	public GDAMessage(Object sourceToken) {
		this(sourceToken,GDAMessageCategory.NOTIFY);
	}

	public GDAMessage(Object sourceToken, String message) {
		this(sourceToken,GDAMessageCategory.NOTIFY, message);
	}

	public GDAMessage(Object sourceToken, GDAMessageCategory category) {
		this(sourceToken,category,"");
	}

	public GDAMessage(Object sourceToken, GDAMessageCategory category, String message) {
		this.sourceToken = Optional.of(sourceToken);
		this.category = category;
		this.message = message;
	}

	@Override
	public GDAMessageCategory getCategory() {
		return category;
	}

	@Override
	public String getMessage() {
		return message;
	}

	@Override
	public Optional<?> getSourceToken() {
		return sourceToken;
	}

	@Override
	public void setCategory(GDAMessageCategory category) {
		this.category = category;
	}

	@Override
	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public void setSourceToken(Object sourceToken) {
		this.sourceToken = Optional.of(sourceToken);
	}

}
