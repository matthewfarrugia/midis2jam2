/*
 * Copyright (C) 2026 Jacob Wysko
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

package org.wysko.midis2jam2.export

import com.jme3.system.Timer

private const val NANOS_PER_SECOND = 1_000_000_000L

internal class FixedStepTimer(private val framesPerSecond: Int) : Timer() {
    private var ticks = 0L

    /** The number of frames the simulation has stepped through. */
    val frameIndex: Long
        get() = ticks

    override fun getTime(): Long = ticks * NANOS_PER_SECOND / framesPerSecond

    override fun getResolution(): Long = NANOS_PER_SECOND

    override fun getFrameRate(): Float = framesPerSecond.toFloat()

    override fun getTimePerFrame(): Float = 1f / framesPerSecond

    override fun update() {
        ticks++
    }

    override fun reset() {
        ticks = 0
    }
}
