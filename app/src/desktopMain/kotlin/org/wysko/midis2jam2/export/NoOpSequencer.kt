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

import org.wysko.kmidi.midi.TimeBasedSequence
import org.wysko.midis2jam2.midi.system.JwSequencer
import org.wysko.midis2jam2.midi.system.MidiDevice
import kotlin.time.Duration

internal class NoOpSequencer : JwSequencer {
    override var sequence: TimeBasedSequence? = null

    private var running = false
    private var open = false

    override val isRunning: Boolean
        get() = running

    override val isOpen: Boolean
        get() = open

    override fun open(device: MidiDevice) {
        open = true
    }

    override fun close() {
        running = false
        open = false
    }

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun setPosition(position: Duration, start: Boolean, onFinish: () -> Unit) = onFinish()

    override fun resetDevice() = Unit

    override fun sendData(data: ByteArray) = Unit
}

internal class NoOpMidiDevice : MidiDevice {
    override val name: String = "None"

    override fun open() = Unit

    override fun close() = Unit

    override fun sendNoteOnMessage(channel: Int, note: Int, velocity: Int) = Unit

    override fun sendNoteOffMessage(channel: Int, note: Int) = Unit

    override fun sendControlChangeMessage(channel: Int, controller: Int, value: Int) = Unit

    override fun sendProgramChangeMessage(channel: Int, program: Int) = Unit

    override fun sendPitchBendMessage(channel: Int, pitch: Int) = Unit

    override fun sendChannelPressureMessage(channel: Int, pressure: Int) = Unit

    override fun sendPolyphonicPressureMessage(channel: Int, note: Int, pressure: Int) = Unit

    override fun sendData(data: ByteArray) = Unit
}
