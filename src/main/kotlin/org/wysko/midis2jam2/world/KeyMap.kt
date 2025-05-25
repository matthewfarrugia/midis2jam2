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

package org.wysko.midis2jam2.world

import com.jme3.app.Application
import com.jme3.input.KeyInput
import com.jme3.input.controls.ActionListener
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.wysko.midis2jam2.util.Utils

/**
 * Stores a map between an action name and a key.
 *
 * @property name The action name.
 * @property key The key name, as defined in [KeyInput].
 */
@Serializable
data class KeyMap(val name: String, val key: String, val multi: Int = MultiKeyPressHandler.NO_MULTI_PRESS) {
    companion object {
        /**
         * Registers key mappings for an application using the given listener.
         *
         * @param app The application instance to register the key mappings.
         * @param listener The action listener to be associated with the key mappings.
         */
        fun registerMappings(app: Application, listener: ActionListener) {
            val keyMaps: Array<KeyMap> = Json.decodeFromString(Utils.resourceToString("/keymap.json"))
            val javaFields = KeyInput::class.java.declaredFields

            keyMaps.forEach { (name, key, multi) ->
                javaFields.firstOrNull { it.name == key }?.let { field ->
                    with(app.inputManager) {
                        val keyCode = field.getInt(KeyInput::class.java)
                        MultiKeyPressHandler(
                            this,
                            name,
                            keyCode,
                            maxPresses = multi,
                            controlKeyCodes = intArrayOf(KeyInput.KEY_C),
                            pressAction = { multiPressCount, isPressed, tfp ->
                                val cameraName = if (multi == MultiKeyPressHandler.NO_MULTI_PRESS || multiPressCount == 0) name else {
                                        name + "_" + ('a' + (multiPressCount - 1)) // "cam1" + "_" + ("a"|"b"|"c"...)
                                    }
                                listener.onAction(cameraName, isPressed, tfp)
                            }
                        )
                    }
                }
            }
        }
    }
}
