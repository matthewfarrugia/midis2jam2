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

package org.wysko.midis2jam2.starter

import com.jme3.app.SimpleApplication
import com.jme3.system.AppSettings
import org.wysko.midis2jam2.starter.configuration.*
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWVidMode
import java.io.File
import javax.imageio.ImageIO

object AppArguments {
    private lateinit var programArguments: Array<String>
    fun init(args: Array<String>) {
        if (::programArguments.isInitialized) {
            throw Exception("Cannot init programArguments again")
        }
        programArguments = args
    }
    fun isRunningInApplicationMode(): Boolean = programArguments.isEmpty()
    fun directFile(): File = File(programArguments.first())
}

fun isRunningInApplicationMode(): Boolean = AppArguments.isRunningInApplicationMode()
fun isMacOS(): Boolean = System.getProperty("os.name").contains("Mac", ignoreCase = true)

internal fun SimpleApplication.applyConfigurations(configurations: Collection<Configuration>) {
    setSettings(AppSettings(false).apply {
        copyFrom(DEFAULT_JME_SETTINGS)
        getCurrentVideoMode()?.let {
            frequency = it.refreshRate()
            applyResolution(it, configurations)
        }
    })
    setDisplayStatView(false)
    setDisplayFps(false)
    isPauseOnLostFocus = false
    isShowSettings = false
}

private fun AppSettings.applyResolution(currentMode: GLFWVidMode, configurations: Collection<Configuration>) = when {
    configurations.find<SettingsConfiguration>().isFullscreen -> {
        isFullscreen = true
        with(currentMode) {
            this@applyResolution.width = width()
            this@applyResolution.height = height()
        }
    }

    else -> {
        isFullscreen = false
        with(configurations.find<GraphicsConfiguration>()) {
            when (windowResolution) {
                is Resolution.DefaultResolution ->
                    with(preferredResolution(currentMode)) {
                        this@applyResolution.width = width
                        this@applyResolution.height = height
                    }

                is Resolution.CustomResolution ->
                    with(windowResolution) {
                        this@applyResolution.width = width
                        this@applyResolution.height = height
                    }
            }
        }
    }
}

internal fun preferredResolution(currentMode: GLFWVidMode): Resolution.CustomResolution =
    with(currentMode) {
        Resolution.CustomResolution((width() * 0.95).toInt(), (height() * 0.85).toInt())
    }

private val DEFAULT_JME_SETTINGS = AppSettings(true).apply {
    frameRate = -1
    isVSync = true
    isResizable = false
    isGammaCorrection = false
    if (!isMacOS() || isRunningInApplicationMode()) {
        icons =
            arrayOf("/ico/icon16.png", "/ico/icon32.png", "/ico/icon128.png", "/ico/icon256.png").map {
                ImageIO.read(this::class.java.getResource(it))
            }.toTypedArray()
    }
    title = "midis2jam2"
    audioRenderer = null
    centerWindow = true
}


internal fun getCurrentVideoMode(): GLFWVidMode? {
    if (!GLFW.glfwInit()) {
        throw Exception()
    }
    val monitor = GLFW.glfwGetPrimaryMonitor()
    val mode = GLFW.glfwGetVideoMode(monitor)
    GLFW.glfwTerminate()
    return mode
}
