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
import org.wysko.kmidi.midi.event.MidiEvent
import org.wysko.kmidi.midi.event.NoteEvent
import org.wysko.midis2jam2.domain.GervillMidiDevice
import org.wysko.midis2jam2.manager.MidiDeviceManager
import org.wysko.midis2jam2.starter.configuration.Configuration
import org.wysko.midis2jam2.starter.configuration.Configuration.AppSettingsConfiguration
import org.wysko.midis2jam2.starter.configuration.Configuration.HomeConfiguration
import org.wysko.midis2jam2.starter.configuration.find
import java.io.File
import javax.sound.midi.MidiSystem
import javax.sound.midi.ShortMessage
import javax.sound.midi.ShortMessage.NOTE_OFF
import javax.sound.midi.ShortMessage.NOTE_ON
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration
import kotlin.time.DurationUnit

class OfflineAudioRenderer(
    private val sequence: TimeBasedSequence,
    private val introLength: Duration,
) {
    fun render(outputFile: File, length: Duration, configurations: Collection<Configuration>) {
        val midiDevice = GervillMidiDevice.instance

        val (_, _, selectedSoundbank, _) = configurations.find<HomeConfiguration>()
        val synth = midiDevice.synthesizer.apply {
            selectedSoundbank?.let {
                val soundbank = MidiSystem.getSoundbank(File(it))
                loadAllInstruments(soundbank)
            }
        }

        val info: MutableMap<String?, Any?> = HashMap()
        val format = AudioFormat(44100f, 16, 2, true, false)
        val stream = GervillStream.open(synth, format, info)
        val receiver = synth.getReceiver()

        MidiDeviceManager.sendResetMessage(
            midiDevice,
            configurations.find<AppSettingsConfiguration>()
                .appSettings
                .playbackSettings
                .midiSpecificationResetSettings
        )

        sequence.smf.tracks.flatMap { it.events }.filterIsInstance<MidiEvent>().sortedBy { it.tick }.forEach {
            when (it) {
                is NoteEvent -> {
                    receiver.send(
                        ShortMessage(
                            when (it) {
                                is NoteEvent.NoteOn -> NOTE_ON
                                else -> NOTE_OFF
                            },
                            it.channel.toInt(),
                            it.note.toInt(),
                            it.velocity.toInt()
                        ),
                        (sequence.getTimeAtTick(it.tick) + introLength).toLong(DurationUnit.MICROSECONDS)
                    )
                }

                else -> Unit
            }
        }

        val lengthInFrames = (length.inWholeMicroseconds * format.getFrameRate() / 1000000L).toLong()
        val limitedStream = AudioInputStream(stream, format, lengthInFrames)

        AudioSystem.write(limitedStream, AudioFileFormat.Type.WAVE, outputFile)
        synth.close()
    }
}