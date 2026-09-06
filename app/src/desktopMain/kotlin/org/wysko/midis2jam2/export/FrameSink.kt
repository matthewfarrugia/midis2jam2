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

import com.jme3.util.Screenshots
import org.wysko.midis2jam2.util.logger
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

private const val BYTES_PER_PIXEL = 4
private const val OPAQUE: Byte = -1
private const val PNG_METADATA_FORMAT = "javax_imageio_png_1.0"
private const val DEFAULT_QUALITY = 18
private const val EXIT_GRACE_SECONDS = 30L
private const val STDERR_TAIL_LINES = 20

internal interface FrameSink {
    fun write(frameIndex: Int, bgra: ByteBuffer)
    fun close()
}

internal class FfmpegSink(
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val audioFile: File,
    private val framesPerSecond: Int,
    private val quality: Int = DEFAULT_QUALITY,
) : FrameSink {
    private val output = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
    private val outputPixels = (output.raster.dataBuffer as DataBufferByte).data

    private val stderrTail = ArrayDeque<String>()
    private val command = FfmpegLocator.locate(outputFile)
        ?: throw SinkInitializeError(
            "No ffmpeg found. Set $FFMPEG_ENV to an executable, or $FFMPEG_COMMAND_ENV to a wrapper command."
        )

    private val process: Process
    private val stdin: OutputStream
    private var failure: IOException? = null

    private val workingDirectory = outputFile.absoluteFile.parentFile ?: File(".").absoluteFile

    init {
        workingDirectory.mkdirs()
        require(audioFile.absoluteFile.parentFile == workingDirectory) {
            "Audio file ${audioFile.absolutePath} must sit beside the output file in $workingDirectory."
        }
        logger().debug("Encoding with ${command.joinToString(" ")}")
        process = ProcessBuilder(command + arguments()).directory(workingDirectory).start()
        stdin = process.outputStream
        drainStderr()
    }

    override fun write(frameIndex: Int, bgra: ByteBuffer) {
        failure?.let { throw it }
        Screenshots.convertScreenShot(bgra, output)
        try {
            stdin.write(outputPixels)
        } catch (e: IOException) {
            throw IOException("ffmpeg stopped after $frameIndex frames.${stderrReport()}", e).also { failure = it }
        }
    }

    override fun close() {
        runCatching { stdin.flush() }
        runCatching { stdin.close() }

        if (!process.waitFor(EXIT_GRACE_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IOException("ffmpeg did not finish within $EXIT_GRACE_SECONDS seconds.${stderrReport()}")
        }
        if (process.exitValue() != 0) {
            throw IOException("ffmpeg exited with code ${process.exitValue()}.${stderrReport()}")
        }
        logger().debug("Wrote ${outputFile.absolutePath}")
    }

    private fun arguments(): List<String> = buildList {
        addAll(listOf("-hide_banner", "-loglevel", "error", "-y"))
        addAll(listOf("-f", "rawvideo", "-pix_fmt", "abgr", "-s", "${width}x$height"))
        addAll(listOf("-framerate", framesPerSecond.toString(), "-i", "-"))
        addAll(listOf("-i", audioFile.name, "-c:a", "aac", "-b:a", "448k"))
        addAll(listOf("-c:v", "libx264", "-preset", "medium", "-crf", quality.toString()))
        addAll(listOf("-pix_fmt", "yuv420p"))
        addAll(listOf("-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709"))
        addAll(listOf("-movflags", "+faststart"))
        add(outputFile.name)
    }

    private fun drainStderr() {
        Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(stderrTail) {
                        stderrTail.addLast(line)
                        if (stderrTail.size > STDERR_TAIL_LINES) stderrTail.removeFirst()
                    }
                }
            }
        }.apply {
            name = "ffmpeg-stderr"
            isDaemon = true
        }.start()
    }

    private fun stderrReport(): String = synchronized(stderrTail) {
        if (stderrTail.isEmpty()) "" else "\n${stderrTail.joinToString("\n")}"
    }
}
