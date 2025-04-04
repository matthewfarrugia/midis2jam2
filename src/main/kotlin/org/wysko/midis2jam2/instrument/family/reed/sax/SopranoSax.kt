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
package org.wysko.midis2jam2.instrument.family.reed.sax

import com.jme3.material.Material
import com.jme3.math.ColorRGBA
import com.jme3.math.Quaternion
import com.jme3.scene.Node
import org.wysko.kmidi.midi.event.MidiEvent
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.algorithmic.PressedKeysFingeringManager
import org.wysko.midis2jam2.util.Utils.rad
import kotlin.time.Duration

private val FINGERING_MANAGER: PressedKeysFingeringManager = PressedKeysFingeringManager.from(SopranoSax::class)
private const val STRETCH_FACTOR = 2f

/**
 * The Soprano saxophone.
 */
class SopranoSax(
    context: Midis2jam2,
    events: List<MidiEvent>
) : Saxophone(context, events, SopranoSaxClone::class, FINGERING_MANAGER) {

    /**
     * A single Soprano saxophone.
     */
    inner class SopranoSaxClone : SaxophoneClone(this@SopranoSax, STRETCH_FACTOR) {
        override fun adjustForPolyphony(delta: Duration) {
            root.localRotation = Quaternion().fromAngles(0f, rad((20f * indexForMoving()).toDouble()), 0f)
        }

        init {
            val shine = context.reflectiveMaterial("Assets/HornSkinGrey.bmp")

            with(bell) {
                move(0f, -22f, 0f)
                attachChild(context.assetManager.loadModel("Assets/SopranoSaxHorn.obj"))
                setMaterial(shine)
            }

            context.assetManager.loadModel("Assets/SopranoSaxBody.obj").apply {
                this as Node
                getChild(0).setMaterial(
                    Material(context.assetManager, "Common/MatDefs/Misc/Unshaded.j3md").apply {
                        setColor("Color", ColorRGBA.Black)
                    }
                )
                getChild(1).setMaterial(shine)
                geometry.attachChild(this)
            }
            highestLevel.localRotation = Quaternion().fromAngles(rad(54.8 - 90), rad(54.3), rad(2.4))
        }
    }

    init {
        with(geometry) {
            setLocalTranslation(-7f, 22f, -51f)
            setLocalScale(0.75f)
        }
    }
}
