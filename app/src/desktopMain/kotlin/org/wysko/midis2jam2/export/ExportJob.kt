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

import com.jme3.system.AppSettings
import org.wysko.kmidi.midi.TimeBasedSequence
import org.wysko.midis2jam2.manager.PlaybackManager
import java.io.File
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

internal data class ExportJob(
    val settings: ExportSettings,
    val onProgress: (frame: Int, total: Int) -> Unit = { _, _ -> },
    val onComplete: (outputFile: File, frames: Int) -> Unit = { _, _ -> },
    val onError: (cause: Throwable) -> Unit = {},
)

internal fun exportFrameCount(sequence: TimeBasedSequence, settings: ExportSettings): Int {
    val performance = PlaybackManager.performanceDuration(sequence)
    val length = settings.maxSeconds?.seconds?.coerceAtMost(performance) ?: performance
    return ceil(length.toDouble(DurationUnit.SECONDS) * settings.framesPerSecond).toInt()
}

internal fun AppSettings.applyExportOverrides(settings: ExportSettings) {
    isVSync = false
    frameRate = -1
    isFullscreen = false
    isResizable = false
    width = settings.width
    height = settings.height
    setUseJoysticks(false)
}
