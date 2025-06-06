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

package org.wysko.midis2jam2.gui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import midis2jam2.generated.resources.Res
import midis2jam2.generated.resources.graphic_eq
import midis2jam2.generated.resources.repeat
import midis2jam2.generated.resources.repeat_on
import org.jetbrains.compose.resources.painterResource
import org.wysko.midis2jam2.gui.components.*
import org.wysko.midis2jam2.gui.viewmodel.HomeViewModel
import org.wysko.midis2jam2.gui.viewmodel.I18n
import org.wysko.midis2jam2.gui.viewmodel.SoundBankConfigurationViewModel
import org.wysko.midis2jam2.midi.search.MIDI_FILE_EXTENSIONS
import java.io.File
import javax.sound.midi.MidiDevice

/**
 * Displays the home screen UI.
 *
 * @param homeViewModel The HomeViewModel instance.
 * @param openMidiSearch Callback function to open the MIDI search screen.
 * @param openMidiFileSelector Callback function to open the MIDI file selector screen.
 * @param playMidiFile Callback function to play the MIDI file.
 */
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    soundbankConfigurationViewModel: SoundBankConfigurationViewModel,
    openMidiSearch: () -> Unit,
    playMidiFile: () -> Unit,
    flicker: Boolean,
    snackbarHostState: SnackbarHostState,
    onOpenSoundbankConfig: () -> Unit,
    isLockPlayButton: Boolean,
) {
    val midiFile by homeViewModel.midiFile.collectAsState()
    val midiDevices by homeViewModel.midiDevices.collectAsState()
    val selectedMidiDevice by homeViewModel.selectedMidiDevice.collectAsState()
    val soundbanks by soundbankConfigurationViewModel.soundbanks.collectAsState()
    val selectedSoundbank by homeViewModel.selectedSoundbank.collectAsState()
    val lastMidiFileSelectedDirectory by homeViewModel.lastMidiFileSelectedDirectory.collectAsState()
    val isLooping by homeViewModel.isLooping.collectAsState()

    val scope = rememberCoroutineScope()
    var focusCount by remember { mutableStateOf(0) }

    val selectMidiFileLauncher = rememberFilePickerLauncher(
        mode = PickerMode.Single,
        type = PickerType.File(MIDI_FILE_EXTENSIONS),
        title = "Select MIDI file",
        initialDirectory = lastMidiFileSelectedDirectory
    ) { file ->
        file?.path?.let {
            homeViewModel.selectMidiFile(File(it))
        }
        focusCount++
    }

    Scaffold(
        floatingActionButton = {
            HelpButton()
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.padding(64.dp).requiredWidth(512.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Midis2jam2Logo(Modifier.align(Alignment.CenterHorizontally))
                SelectMidiFileRow(midiFile, {
                    selectMidiFileLauncher.launch()
                }, openMidiSearch, flicker, focusCount)
                SelectMidiDeviceRow(midiDevices, selectedMidiDevice, homeViewModel) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            I18n["refreshed_midi_devices"].value,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
                AnimatedVisibility(homeViewModel.shouldShowSoundbankSelector) {
                    SelectSoundbankRow(selectedSoundbank, soundbanks.toList(), homeViewModel, onOpenSoundbankConfig)
                }
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = playMidiFile,
                        modifier = Modifier.width(160.dp).height(48.dp),
                        enabled = !isLockPlayButton && midiFile != null,
                    ) {
                        Icon(Icons.Default.PlayArrow, I18n["play"].value)
                        Spacer(Modifier.width(8.dp))
                        Text(I18n["play"].value)
                    }
                    ToolTip(I18n["repeat"].value) {
                        IconToggleButton(isLooping, {
                            homeViewModel.setLooping(it)
                        }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                            Icon(painterResource(if (isLooping) Res.drawable.repeat_on else Res.drawable.repeat), I18n["repeat"].value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectMidiFileRow(
    midiFile: File?,
    openMidiFileSelector: () -> Unit,
    openMidiSearch: () -> Unit,
    flicker: Boolean,
    focusCount: Int,
) {
    val flickerSize by animateFloatAsState(if (flicker) 1.05f else 1f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MidiFileSelector(
            selectedMidiFile = midiFile?.name ?: "",
            modifier = Modifier.weight(1f).scale(flickerSize),
            focusCount = focusCount,
        ) {
            openMidiFileSelector()
        }

        ToolTip(I18n["search"].value) {
            IconButton({ openMidiSearch() }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Default.Search, I18n["search"].value)
            }
        }
    }
}

@Composable
private fun SelectMidiDeviceRow(
    midiDevices: List<MidiDevice.Info>,
    selectedMidiDevice: MidiDevice.Info,
    viewModel: HomeViewModel,
    onRefresh: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MidiDeviceSelector(
            devices = midiDevices,
            selectedDevice = selectedMidiDevice,
            modifier = Modifier.weight(1f),
        ) { info ->
            viewModel.selectMidiDevice(info)
        }
        ToolTip(I18n["refresh_midi_devices"].value) {
            IconButton({
                viewModel.refreshMidiDevices()
                onRefresh()
            }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Icon(Icons.Default.Refresh, I18n["refresh_midi_devices"].value)
            }
        }
    }
}

@Composable
private fun SelectSoundbankRow(
    selectedSoundbank: File?,
    soundbanks: List<File>,
    viewModel: HomeViewModel,
    onOpenSoundbankConfig: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SoundbankSelector(
            modifier = Modifier.weight(1f),
            selectedSoundbank = selectedSoundbank,
            soundbanks = soundbanks,
        ) { soundbank ->
            viewModel.selectSoundbank(soundbank)
        }
        ToolTip(I18n["soundbank_configure"].value) {
            IconButton({
                onOpenSoundbankConfig()
            }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                Icon(painterResource(Res.drawable.graphic_eq), I18n["soundbank_configure"].value)
            }
        }
    }
}
