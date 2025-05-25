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

package org.wysko.midis2jam2.world
import com.jme3.input.InputManager
import com.jme3.input.controls.ActionListener
import com.jme3.input.controls.KeyTrigger
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Handles multi presses of keys. Use [maxPresses] to allow for multiple presses or disable with [NO_MULTI_PRESS].
 * [controlKeyCode] Enables multi press only when control key is depressed
 * [pressAction] count parameter will be called with 0 when [NO_MULTI_PRESS] or when [controlKeyCode] is not depressed (if specified)
 * Otherwise the count parameter will be the number of presses within the allowed [maxIntervalMillis]
 */
class MultiKeyPressHandler(
    private val inputManager: InputManager,
    private val keyName: String,
    private val keyCode: Int,
    private val pressAction: (multiPressCount: Int, isPressed: Boolean, tpf: Float) -> Unit,
    private val maxPresses: Int = NO_MULTI_PRESS,
    private val controlKeyCodes: IntArray = intArrayOf(),
    private val maxIntervalMillis: Long = 300L,
) : ActionListener {

    private var pressCount = 0
    private var scheduledPressTask: ScheduledFuture<*>? = null
    private var scheduledUnpressTask: ScheduledFuture<*>? = null
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    private fun hasControlKeys(): Boolean = controlKeyCodes.isNotEmpty()
    private var controlKeyIsDown: Boolean = false
    companion object {
        const val CONTROL_KEY_NAME: String = "multiKeyControl"
        const val NO_MULTI_PRESS: Int = 0
    }

    init {
        inputManager.addMapping(keyName, KeyTrigger(keyCode))
        inputManager.addListener(this, keyName)
        if (hasControlKeys() && maxPresses != NO_MULTI_PRESS) {
            controlKeyCodes.forEach {
                if (!inputManager.hasMapping(CONTROL_KEY_NAME)) {
                    inputManager.addMapping(CONTROL_KEY_NAME, KeyTrigger(it))
                }
            }
            inputManager.addListener(this, CONTROL_KEY_NAME)
        }
    }

    override fun onAction(name: String, isPressed: Boolean, tpf: Float) {
        if (name == keyName) {
            handleKeyPress(isPressed, tpf)
        } else if (name == CONTROL_KEY_NAME) {
            handleControlAction(isPressed, tpf)
        }
    }

    private fun handleKeyPress(isPressed: Boolean, tpf: Float) {
        if (maxPresses == NO_MULTI_PRESS || (hasControlKeys() && !controlKeyIsDown)) {
            pressAction.invoke(0, isPressed, tpf)
        } else if (pressCount < maxPresses) {
            if (isPressed) {
                pressCount++
                scheduledPressTask = scheduleBlock(scheduledPressTask, {
                    pressAction.invoke(pressCount, isPressed, tpf)
                    pressCount = 0
                    scheduledPressTask = null
                })
            } else {
                scheduledUnpressTask = scheduleBlock(scheduledUnpressTask, {
                    pressAction.invoke(pressCount, isPressed, tpf)
                    scheduledUnpressTask = null
                })
            }
        }
    }

    private fun handleControlAction(isPressed: Boolean, tpf: Float) {
        if (hasControlKeys() && maxPresses != NO_MULTI_PRESS) {
            controlKeyIsDown = isPressed
        }
    }

    private fun scheduleBlock(task: ScheduledFuture<*>?, runnable: Runnable): ScheduledFuture<*> {
        task?.cancel(false)
        return scheduler.schedule(runnable, maxIntervalMillis, TimeUnit.MILLISECONDS)
    }
}