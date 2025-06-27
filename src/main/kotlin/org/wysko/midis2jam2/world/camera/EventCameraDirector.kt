/*
 * Copyright (C) 2025 Matthew Farrugia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/.
 */

package org.wysko.midis2jam2.world.camera

import com.install4j.runtime.installer.frontend.Messages
import org.wysko.kmidi.midi.event.ControlChangeEvent
import org.wysko.kmidi.midi.event.Event
import org.wysko.kmidi.midi.event.MetaEvent
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.algorithmic.EventCollector
import org.wysko.midis2jam2.util.logger
import org.wysko.midis2jam2.world.lyric.startTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private const val CC_CAMERA_SPEED = 102.toByte()
private const val CC_CAMERA_SPEED_BASE = 103.toByte()

/**
 * The Directed Camera is an auto-cam which moves the camera to follow directed camera select events.
 */
class EventCameraDirector(context: Midis2jam2, events: List<MetaEvent.Marker>, controlChanges: List<ControlChangeEvent>): CameraDirector {

    private val cameraEventCollector = EventCollector(
        context,
        events.filter { it.text.lowercase().startsWith("cam:") },
    )

    private val cameraSpeedCollector = EventCollector(
        context,
        controlChanges.filter { it.controller == CC_CAMERA_SPEED },
    )

    private val cameraSpeedBaseCollector = EventCollector(
        context,
        controlChanges.filter { it.controller == CC_CAMERA_SPEED_BASE }
    )

    private var ccSpeed = 63
    private var ccSpeedBase = 1

    override fun transitionForTick(time: Duration, delta: Duration, moving: Boolean): CameraChange? {
        cameraSpeedBaseCollector.advanceCollectOne(time)?.let { ccSpeedBase = it.value.toInt() }
        cameraSpeedCollector.advanceCollectOne(time)?.let { ccSpeed = it.value.toInt() }
        val marker = cameraEventCollector.advanceCollectOne(time) ?: return null
        val cameraAngleName = if (marker.text.startsWith("cam:", ignoreCase = true))
            marker.text.substring(4).trim().uppercase().replace(' ', '_')
            else return null

        val cameraAngle = AutoCamPosition.entries.firstOrNull { it.name == cameraAngleName }
        if (cameraAngle == null) {
            logger().warn("Failed to find camera angle for name: "+cameraAngleName)
            return null
        }

        return Pair(cameraAngle, cameraPanSpeed())
    }

    private fun cameraPanSpeed(): Duration {
        val maxTimeSeconds = 10
        val baseNormalized = maxTimeSeconds * (ccSpeedBase.coerceIn(0, 127) / 127.0)
        val speedNormalized = ccSpeed.coerceIn(0, 127) / 127.0
        return (baseNormalized * speedNormalized).seconds
    }

    /** External trigger to change angle, ignore - camera will be selected on next event */
    override fun trigger(moving: Boolean) {
        // ignored
    }
}