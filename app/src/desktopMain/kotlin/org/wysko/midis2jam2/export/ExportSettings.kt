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

import kotlinx.serialization.Serializable

@Serializable
data class ExportSettings(
    val outputFilepath: String,
    val width: Int = DEFAULT_WIDTH,
    val height: Int = DEFAULT_HEIGHT,
    val framesPerSecond: Int = DEFAULT_FRAMES_PER_SECOND,
    val maxSeconds: Double? = null,
) {
    companion object {
        const val DEFAULT_WIDTH: Int = 1920
        const val DEFAULT_HEIGHT: Int = 1080
        const val DEFAULT_FRAMES_PER_SECOND: Int = 30
    }
}
