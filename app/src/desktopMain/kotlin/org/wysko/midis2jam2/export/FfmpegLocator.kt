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

import org.wysko.midis2jam2.util.logger
import java.io.File

const val FFMPEG_ENV: String = "MIDIS2JAM2_FFMPEG"
const val FFMPEG_COMMAND_ENV: String = "MIDIS2JAM2_FFMPEG_COMMAND"
private const val OUT_DIR_PLACEHOLDER = "{outDir}"

internal object FfmpegLocator {

    fun locate(outputFile: File): List<String>? {
        val outputDirectory = outputFile.absoluteFile.parentFile ?: File(".").absoluteFile
        return fromCommandTemplate(outputDirectory)
            ?: fromExecutableVariable()
            ?: fromPath()
    }

    private fun fromCommandTemplate(outputDirectory: File): List<String>? {
        val template = env(FFMPEG_COMMAND_ENV) ?: return null
        val command = template.split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .map { it.replace(OUT_DIR_PLACEHOLDER, outputDirectory.absolutePath) }
        if (command.isEmpty()) return null
        return command
    }

    private fun fromExecutableVariable(): List<String>? {
        val path = env(FFMPEG_ENV) ?: return null
        val executable = File(path)
        if (!executable.canExecute()) {
            logger().warn("$FFMPEG_ENV is set to $path, which is not executable; ignoring it")
            return null
        }
        return listOf(executable.absolutePath)
    }

    private fun fromPath(): List<String>? {
        val executable = System.getenv("PATH")
            ?.split(File.pathSeparatorChar)
            ?.asSequence()
            ?.map { File(it, executableName()) }
            ?.firstOrNull { it.isFile && it.canExecute() }
            ?: return null
        return listOf(executable.absolutePath)
    }

    private fun executableName(): String =
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) "ffmpeg.exe" else "ffmpeg"

    private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
}
