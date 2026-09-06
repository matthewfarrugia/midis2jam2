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

import com.jme3.post.SceneProcessor
import com.jme3.profile.AppProfiler
import com.jme3.renderer.RenderManager
import com.jme3.renderer.ViewPort
import com.jme3.renderer.queue.RenderQueue
import com.jme3.texture.FrameBuffer
import com.jme3.texture.Image
import com.jme3.util.BufferUtils
import org.wysko.midis2jam2.util.logger
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

private const val BYTES_PER_PIXEL = 4
private const val DEFAULT_POOL_DEPTH = 4

open class SinkInitializeError(message: String) : Exception(message)

internal class VideoCaptureProcessor(
    private val sink: (width: Int, height: Int) -> FrameSink,
    private val frameLimit: Int,
    private val poolDepth: Int = DEFAULT_POOL_DEPTH,
) : SceneProcessor {

    private class PendingFrame(val index: Int, val buffer: ByteBuffer)

    private lateinit var renderManager: RenderManager
    private var initialized = false

    private var free: ArrayBlockingQueue<ByteBuffer>? = null
    private var pending: ArrayBlockingQueue<PendingFrame>? = null
    private var writer: Thread? = null
    private var resolvedSink: FrameSink? = null

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    @Volatile
    var framesCaptured: Int = 0
        private set

    @Volatile
    var failure: Throwable? = null
        private set

    @Volatile
    private var finished = false

    override fun initialize(renderManager: RenderManager, viewPort: ViewPort) {
        this.renderManager = renderManager
        width = viewPort.camera.width
        height = viewPort.camera.height
        initialized = true

        val sink: FrameSink
        try {
            sink = sink(width, height).also { resolvedSink = it }
        } catch (e: SinkInitializeError) {
            failure = e
            return
        }

        val free = ArrayBlockingQueue<ByteBuffer>(poolDepth)
        val pending = ArrayBlockingQueue<PendingFrame>(poolDepth)
        repeat(poolDepth) { free.put(BufferUtils.createByteBuffer(width * height * BYTES_PER_PIXEL)) }
        this.free = free
        this.pending = pending

        logger().debug("Capturing $width x $height, pool depth $poolDepth, $frameLimit frames")

        writer = Thread {
            try {
                while (true) {
                    val frame = pending.take()
                    if (frame.index < 0) break // poison pill
                    if (failure == null) {
                        try {
                            sink.write(frame.index, frame.buffer)
                        } catch (e: Exception) {
                            failure = e
                        }
                    }
                    frame.buffer.clear()
                    free.put(frame.buffer)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply {
            name = "video-export-writer"
            isDaemon = false // non-daemon: Ensure the last frames are processed, even if render process has gone
            start()
        }
    }

    override fun postFrame(out: FrameBuffer?) {
        if (finished || framesCaptured >= frameLimit || failure != null) return
        val free = free ?: return
        val pending = pending ?: return

        val buffer = free.take()
        buffer.clear()
        renderManager.renderer.readFrameBufferWithFormat(out, buffer, Image.Format.BGRA8)
        pending.put(PendingFrame(framesCaptured, buffer))
        framesCaptured++
    }

    fun finish() {
        if (finished) return
        finished = true
        val pending = pending
        val writer = writer
        if (pending != null && writer != null) {
            pending.put(PendingFrame(-1, ByteBuffer.allocate(0)))
            writer.join()
        }
        runCatching { resolvedSink?.close() }.onFailure { if (failure == null) failure = it }
    }

    override fun reshape(viewPort: ViewPort, w: Int, h: Int) {
        if (initialized && (w != width || h != height)) {
            logger().warn("Render surface resized to $w x $h mid-export; frames will be malformed")
        }
    }

    override fun isInitialized(): Boolean = initialized

    override fun preFrame(tpf: Float) = Unit

    override fun postQueue(queue: RenderQueue?) = Unit

    override fun cleanup() = finish()

    override fun setProfiler(profiler: AppProfiler?) = Unit
}

internal fun RenderManager.lastEnabledPostView(): ViewPort =
    postViews.lastOrNull { it.isEnabled } ?: error("No enabled post view to capture from")
