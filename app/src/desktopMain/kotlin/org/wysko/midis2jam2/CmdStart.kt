/*
 * Copyright (C) 2025 Jacob Wysko
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

package org.wysko.midis2jam2

import com.install4j.api.launcher.SplashScreen
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.wysko.midis2jam2.domain.ApplicationService
import org.wysko.midis2jam2.domain.ExecutionState
import org.wysko.midis2jam2.export.ExportSettings
import java.io.File

object CmdStart : KoinComponent {
    fun start(args: Array<String>) {
        if (args.isEmpty()) return

        val applicationService: ApplicationService by inject()
        val midiFile = PlatformFile(File(args.first()))

        when (val export = parseExportSettings(args)) {
            null -> startApplicationWithFile(applicationService, midiFile)
            else -> exportToVideo(applicationService, midiFile, export)
        }
    }

    private fun parseExportSettings(args: Array<String>): ExportSettings? {
        fun option(name: String): String? = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

        val exportPath = option("--export") ?: return null
        val size = option("--size")?.split('x', ignoreCase = true)
        var width: Int
        var height: Int
        try {
            check(size?.size == 2)
            width = size[0].toInt()
            height = size[1].toInt()
        } catch (e: Exception) {
            when (e) {
                is IllegalStateException, is IllegalArgumentException -> {
                    width = ExportSettings.DEFAULT_WIDTH
                    height = ExportSettings.DEFAULT_HEIGHT
                }

                else -> throw e
            }
        }

        return ExportSettings(
            outputFilepath = exportPath,
            width = width,
            height = height,
            framesPerSecond = option("--fps")?.toIntOrNull() ?: ExportSettings.DEFAULT_FRAMES_PER_SECOND,
            maxSeconds = option("--seconds")?.toDoubleOrNull(),
        )
    }

    private fun exportToVideo(
        applicationService: ApplicationService,
        midiFile: PlatformFile,
        export: ExportSettings,
    ) {
        applicationService.exportVideo(midiFile, export)
        try {
            SplashScreen.hide()
        } catch (_: Exception) {
        }
        runBlocking {
            applicationService.isApplicationRunning.first { !it }
        }
    }

    /**
     * Starts the application with a specific MIDI file.
     * This method can be called from both command line args and startup listener events.
     */
    fun startApplicationWithFile(
        applicationService: ApplicationService,
        midiFile: PlatformFile
    ) {
        applicationService.startApplication(ExecutionState(midiFile))
        try {
            SplashScreen.hide()
        } catch (_: Exception) {}
        runBlocking {
            applicationService.isApplicationRunning.first { !it }
        }
    }
}
