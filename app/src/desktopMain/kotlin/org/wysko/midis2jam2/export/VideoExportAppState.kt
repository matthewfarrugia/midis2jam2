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

import com.jme3.app.Application
import com.jme3.app.SimpleApplication
import com.jme3.app.state.AbstractAppState
import com.jme3.renderer.ViewPort
import org.wysko.midis2jam2.manager.BaseManager
import org.wysko.midis2jam2.util.logger
import java.io.File

internal class VideoExportAppState(
    private val settings: ExportSettings,
    private val totalFrames: Int,
    private val onProgress: (frame: Int, total: Int) -> Unit = { _, _ -> },
    private val onComplete: (outputFile: File, frames: Int) -> Unit = { _, _ -> },
    private val onError: (Throwable) -> Unit = {},
) : BaseManager() {

    private lateinit var processor: VideoCaptureProcessor
    private lateinit var captureViewPort: ViewPort

    private var lastReportedFrame = -1
    private var isStopping = false

    private val outputFile = File(settings.outputFilepath)

    fun attachTo(app: SimpleApplication) {
        app.setTimer(FixedStepTimer(settings.framesPerSecond))

        processor = VideoCaptureProcessor(
            sink = { renderWidth, renderHeight ->
                FfmpegSink(
                    outputFile, renderWidth, renderHeight, settings.framesPerSecond
                )
            },
            frameLimit = totalFrames,
        )
        captureViewPort = app.renderManager.lastEnabledPostView()
        captureViewPort.addProcessor(processor)
        app.stateManager.attach(this)

        logger().debug(
            "Exporting $totalFrames frames at ${settings.framesPerSecond} fps, " +
                "${settings.width}x${settings.height} from a ${settings.width}x${settings.height}, " +
                "to ${outputFile.absolutePath}"
        )
    }

    override fun update(tpf: Float) {
        if (isStopping) return

        processor.failure?.let { cause ->
            isStopping = true
            logger().error("Frame writer failed; aborting export", cause)
            processor.finish()
            onError(cause)
            application.stop()
            return
        }

        val captured = processor.framesCaptured
        if (captured != lastReportedFrame) {
            lastReportedFrame = captured
            onProgress(captured, totalFrames)
        }

        if (captured >= totalFrames) {
            isStopping = true
            processor.finish()
            logger().debug("Export complete: exported ${outputFile.absolutePath}")
            onComplete(outputFile, captured)
            application.stop() // force stop if video recording is done
        }
    }

    override fun cleanup(app: Application?) {
        if (isInitialized) {
            captureViewPort.removeProcessor(processor)
        }
        processor.finish()
        super.cleanup(app)
    }
}
