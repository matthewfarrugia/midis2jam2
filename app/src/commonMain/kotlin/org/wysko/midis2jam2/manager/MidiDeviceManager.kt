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

package org.wysko.midis2jam2.manager

import com.jme3.app.Application
import org.wysko.midis2jam2.domain.settings.AppSettings.PlaybackSettings.MidiSpecificationResetSettings
import org.wysko.midis2jam2.midi.midiSpecificationResetMessage
import org.wysko.midis2jam2.midi.system.MidiDevice
import org.wysko.midis2jam2.starter.configuration.Configuration
import org.wysko.midis2jam2.starter.configuration.Configuration.AppSettingsConfiguration
import org.wysko.midis2jam2.starter.configuration.find

class MidiDeviceManager(
    private val configs: Collection<Configuration>,
    private val midiDevice: MidiDevice
) : BaseManager() {
    override fun initialize(app: Application) {
        super.initialize(app)
        val resetSettings = configs.find<AppSettingsConfiguration>()
            .appSettings
            .playbackSettings
            .midiSpecificationResetSettings
        if (resetSettings.isSendSpecificationResetMessage) {
            sendResetMessage(midiDevice, resetSettings)
        }
    }

    fun sendResetMessage() {
        sendResetMessage(
            midiDevice,
            configs.find<AppSettingsConfiguration>()
                .appSettings
                .playbackSettings
                .midiSpecificationResetSettings
        )
    }

    companion object {
        fun sendResetMessage(midiDevice: MidiDevice, resetSettings: MidiSpecificationResetSettings) {
            val specification = resetSettings.midiSpecification
            midiDevice.sendData(midiSpecificationResetMessage[specification] ?: return)
        }
    }
}