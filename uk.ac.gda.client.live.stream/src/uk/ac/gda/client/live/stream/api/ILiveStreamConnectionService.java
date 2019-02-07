/*-
 * Copyright © 2019 Diamond Light Source Ltd.
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

package uk.ac.gda.client.live.stream.api;

import uk.ac.gda.client.live.stream.LiveStreamConnection;
import uk.ac.gda.client.live.stream.view.CameraConfiguration;
import uk.ac.gda.client.live.stream.view.StreamType;

public interface ILiveStreamConnectionService extends IMappableLiveStreamConnectionSource {

	/**
	 * Retrieve a shared {@link LiveStreamConnection} of the specified {@link StreamType} based on the specified
	 * {@link CameraConfiguration}.
	 */
	public LiveStreamConnection getSharedLiveStreamConnection(
			final CameraConfiguration cameraConfig, final StreamType streamType);

	/**
	 * Retrieve a shared {@link LiveStreamConnection} of the specified {@link StreamType} corresponding to the supplied
	 * named camera.
	 */
	public LiveStreamConnection getSharedLiveStreamConnection(final String cameraName, final StreamType streamType);

	/**
	 * Instantiate a new {@link LiveStreamConnection} of the specified {@link StreamType} based on the specified
	 * {@link CameraConfiguration}
	 */
	public LiveStreamConnection getFreshLiveStreamConnection(
			final CameraConfiguration cameraConfig, final StreamType streamType);

	/**
	 * Instantiate a new {@link LiveStreamConnection} of the specified {@link StreamType} corresponding to the supplied
	 * named camera
	 */
	public LiveStreamConnection getFreshLiveStreamConnection(final String cameraName, final StreamType streamType);
}
