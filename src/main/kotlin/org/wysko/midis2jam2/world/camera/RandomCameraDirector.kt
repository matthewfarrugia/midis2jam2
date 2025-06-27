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

package org.wysko.midis2jam2.world.camera

import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.Instrument
import org.wysko.midis2jam2.instrument.family.ensemble.StageStrings
import org.wysko.midis2jam2.instrument.family.percussion.drumset.DrumSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/** The speed at which to transition from one camera angle to another. */
private val MOVE_SPEED = 3.seconds

/** The amount of time to wait before transitioning to the next camera angle. */
private val WAIT_TIME = 3.seconds

/**
 * The RandomCameraDirector picks camera angles randomly, excluding angles seen recently.
 */
class RandomCameraDirector(private val context: Midis2jam2, private val classicCamera: Boolean): CameraDirector {

    /** The amount of time that has passed since the last camera angle change. */
    private var waiting = 0.seconds

    /** A list of previously used camera angles. */
    private val angles = mutableListOf(AutoCamPosition.GENERAL_A)

    override fun transitionForTick(time: Duration, delta: Duration, moving: Boolean): CameraChange? {
        /* If the camera is not moving, and the song has started, */
        if (!moving && time > 0.seconds) {
            /* Increment the waiting timer */
            waiting += delta

            /* If the instrument dictates that it should no longer be focused on, */
            if (!angles.last().stayHere(time, context.instruments, context)) {
                /* Invalidate the current angle by setting the wait time to the maximum */
                waiting = WAIT_TIME
            }
        }

        /* If we have waited longer than the wait time */
        if (waiting >= WAIT_TIME) {
            /* Reset the waiting timer */
            waiting = Duration.ZERO

            /* Pick a new camera angle */
            val angle = randomCamera(time)

            /* Remember this angle was selected */
            angles.add(angle)

            /* About 1/5 of the time, do not interpolate and just move to the new angle (jump-cut) */
            val transitionTime = if (Math.random() < 0.2) 0.seconds else MOVE_SPEED

            return Pair(angle, transitionTime)
        }

        return null
    }

    private fun randomCamera(time: Duration): AutoCamPosition {
        /* If we are near the end of the song, */
        if (context.sequence.duration - time < WAIT_TIME * 2.5) {
            /* Pick GENERAL_A */
            return AutoCamPosition.GENERAL_A
        }

        if (classicCamera) {
            return AutoCamPosition.values()
                .filter { it.isClassicCamUsed && it != angles.last() && it.pickMe(time, context.instruments, context) }
                .random()
        }

        /* About 1/4 of the time, pick a stage angle */
        return if (Math.random() < 0.25) {
            /* Collect all stage camera angles */
            val stageCameras = AutoCamPosition.values().filter { it.type == AutoCamPositionType.STAGE }

            /* Valid stage cameras are those that are not the current one (and not the overhead) */
            val validStageCameras = stageCameras.filter { it != angles.last() && it != AutoCamPosition.GENERAL_D }

            /* Pick a random valid stage camera */
            validStageCameras.random()
        } else {
            /* Collect all valid instrument camera angles */
            val validInstrumentCameras = AutoCamPosition.values()
                .filter { it.type == AutoCamPositionType.INSTRUMENT && it.pickMe(time, context.instruments, context) }

            /* Collect some the last used instrument camera angles */
            val lastUsedInstrumentCameras = angles.filter { it.type == AutoCamPositionType.INSTRUMENT }
                .takeLast((context.instruments.filter { it.isVisible }.size - 2).coerceAtLeast(1))

            val notRecentlyUsedInstrumentAngles = validInstrumentCameras.minus(lastUsedInstrumentCameras.toSet())

            /* If there are any valid camera angles that are not the last used ones, */
            if (notRecentlyUsedInstrumentAngles.isNotEmpty()) {
                /* Pick a random camera from that list */
                notRecentlyUsedInstrumentAngles.random()
            } else {
                /* Otherwise, just pick the last used camera that has been the longest time since it was used */
                angles.firstOrNull { it.type == AutoCamPositionType.INSTRUMENT && it.pickMe(time, context.instruments, context) }
                    ?: AutoCamPosition.GENERAL_A
            }
        }
    }

    /** External trigger of new camera angle, set the waiting time to invalidate the current angle */
    override fun trigger(moving: Boolean) {
        if (!moving) {
            waiting = WAIT_TIME
        }
    }
}

/** The condition that must be met for the camera to be picked. */
fun AutoCamPosition.pickMe(time: Duration, instruments: List<Instrument>, context: Midis2jam2): Boolean {
    return when (instrumentClass) {
        null -> true
        DrumSet::class.java -> context.drumSetVisibilityManager.isVisible
        StageStrings::class.java ->
            instruments.filterIsInstance<StageStrings>().any { it.isVisible } && visibleNowAndLater(
                instruments,
                StageStrings::class.java,
                time,
                WAIT_TIME
            )
        else -> visibleNowAndLater(instruments, instrumentClass, time, WAIT_TIME * 1.5)
    }
}

fun AutoCamPosition.stayHere(time: Duration, instruments: List<Instrument>, context: Midis2jam2): Boolean {
    return when (instrumentClass) {
        null -> true
        DrumSet::class.java -> context.drumSetVisibilityManager.isVisible
        else -> instruments.filterIsInstance(instrumentClass).any { it.isVisible }
    }
}

/**
 * Determines if the given [instrument] class, given the list of [instruments], is visible at the given [time] and
 * visible [buffer] seconds after time.
 *
 * @param instruments the list of instruments
 * @param instrument the instrument class
 * @param time the current time
 * @param buffer the amount of time to look into the future and see if the instrument is visible
 * @return true if the instrument is visible at the given time and ahead by the buffer, false otherwise
 */
fun visibleNowAndLater(
    instruments: List<Instrument>,
    instrument: Class<out Instrument>,
    time: Duration,
    buffer: Duration
): Boolean = instruments.filterIsInstance(instrument).any {
    it.isVisible && it.calculateVisibility(time + buffer, future = true)
}