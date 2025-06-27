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

import com.jme3.math.Quaternion
import com.jme3.math.Vector3f
import org.wysko.midis2jam2.Midis2jam2
import org.wysko.midis2jam2.instrument.Instrument
import org.wysko.midis2jam2.instrument.family.animusic.SpaceLaser
import org.wysko.midis2jam2.instrument.family.brass.*
import org.wysko.midis2jam2.instrument.family.chromaticpercussion.*
import org.wysko.midis2jam2.instrument.family.ensemble.PizzicatoStrings
import org.wysko.midis2jam2.instrument.family.ensemble.StageChoir
import org.wysko.midis2jam2.instrument.family.ensemble.StageStrings
import org.wysko.midis2jam2.instrument.family.ensemble.Timpani
import org.wysko.midis2jam2.instrument.family.ethnic.BagPipe
import org.wysko.midis2jam2.instrument.family.guitar.Banjo
import org.wysko.midis2jam2.instrument.family.guitar.BassGuitar
import org.wysko.midis2jam2.instrument.family.guitar.Guitar
import org.wysko.midis2jam2.instrument.family.guitar.Shamisen
import org.wysko.midis2jam2.instrument.family.organ.Accordion
import org.wysko.midis2jam2.instrument.family.organ.Harmonica
import org.wysko.midis2jam2.instrument.family.percussion.drumset.DrumSet
import org.wysko.midis2jam2.instrument.family.percussive.*
import org.wysko.midis2jam2.instrument.family.piano.Keyboard
import org.wysko.midis2jam2.instrument.family.pipe.*
import org.wysko.midis2jam2.instrument.family.reed.Clarinet
import org.wysko.midis2jam2.instrument.family.reed.Oboe
import org.wysko.midis2jam2.instrument.family.reed.sax.AltoSax
import org.wysko.midis2jam2.instrument.family.reed.sax.BaritoneSax
import org.wysko.midis2jam2.instrument.family.reed.sax.SopranoSax
import org.wysko.midis2jam2.instrument.family.reed.sax.TenorSax
import org.wysko.midis2jam2.instrument.family.soundeffects.BirdTweet
import org.wysko.midis2jam2.instrument.family.soundeffects.TelephoneRing
import org.wysko.midis2jam2.instrument.family.strings.*
import org.wysko.midis2jam2.starter.configuration.SettingsConfiguration
import org.wysko.midis2jam2.starter.configuration.get
import org.wysko.midis2jam2.util.Utils
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** <camera angle, transition time> */
typealias CameraChange = Pair<AutoCamPosition, Duration>

/** delegate to make camera angle changes */
interface CameraDirector {
    /** For the given tick, return next the camera angle and panning speed transition */
    fun transitionForTick(time: Duration, delta: Duration, moving: Boolean): CameraChange?

    /** External message to trigger a new camera position */
    fun trigger(moving: Boolean)
}

/**
 * The auto-cam controller is responsible for controlling the automatic movement of the camera. It picks camera angles
 * randomly and moves the camera to them.
 */
class AutoCamController(
    private val context: Midis2jam2,
    private val director: CameraDirector,
    startEnabled: Boolean
) {

    /** When true, the auto-cam controller is enabled. */
    var enabled: Boolean = startEnabled
        set(value) {
            if (value && !field) { // If the field is being set true from a false state
                startLocation = context.app.camera.location.clone()
                startRotation = context.app.camera.rotation.clone()
            }
            field = value
        }

    /** True if the camera is currently moving to a new angle, false otherwise. */
    private var moving = false

    /** The current amount of transition, from 0 to 1. */
    private var transitionProgress = 0f

    /** The location at which the camera started at in this transition. */
    private var startLocation: Vector3f = AutoCamPosition.GENERAL_A.location.clone()

    /** The rotation at which the camera started at in this transition. */
    private var startRotation: Quaternion = AutoCamPosition.GENERAL_A.rotation.clone()

    /** The location at which the camera started at in this transition. */
    var currentLocation: Vector3f = AutoCamPosition.GENERAL_A.location.clone()

    /** The rotation at which the camera started at in this transition. */
    var currentRotation: Quaternion = AutoCamPosition.GENERAL_A.rotation.clone()

    var currentCameraTransition: CameraChange = Pair(AutoCamPosition.GENERAL_A, 0.seconds)

    /** Performs a tick of the auto-cam controller. */
    fun tick(time: Duration, delta: Duration): Boolean {
        if (!enabled) return false

        /* Check the director for a new transition */
        val transition = director.transitionForTick(time, delta, moving)

        if (transition != null && transition.first != currentCameraTransition.first) {
            /* director has asked for a new angle */
            moving = true
            currentCameraTransition = transition

            /* Copy down the current location and rotation so that we can do some interpolation */
            startLocation = context.app.camera.location.clone()
            startRotation = context.app.camera.rotation.clone()
        }

        val (currentAngle, transitionDuration) = currentCameraTransition

        /* If we are in the process of moving to a new camera angle, */
        if (moving) {
            if (transitionDuration == 0.seconds) {
                transitionProgress = 1f // jump cut
            } else {
                transitionProgress += (delta / transitionDuration).toFloat() // Increment interpolation index
            }
        }

        /* Set the camera location and rotation to the interpolated values */
        cam.location = Vector3f().interpolateLocal(startLocation, currentAngle.location, transitionProgress.smooth()).also {
            currentLocation = it
        }
        cam.rotation = quaternionInterpolation(startRotation, currentAngle.rotation, transitionProgress.smooth()).also {
            currentRotation = it
        }

        /* If we have reached the end of the interpolation, */
        if (transitionProgress >= 1f) {
            /* Reset the interpolation index */
            transitionProgress = 0f

            /* We are no longer moving */
            moving = false

            startLocation = context.app.camera.location.clone()
            startRotation = context.app.camera.rotation.clone()
        }

        return true
    }

    /**
     * Applies cubic-ease-in-out interpolation to a value.
     */
    private fun Float.smooth(): Float = when (context.configs[SettingsConfiguration::class].isClassicCamera) {
        true -> this
        false -> if (this < 0.5) 4 * this.pow(3) else 1 - (-2 * this + 2).pow(3) / 2
    }

    fun trigger() {
        if (!enabled) {
            transitionProgress = 0f
        }
        enabled = true
        director.trigger(moving)
    }

    /** The camera. */
    private val cam
        get() = context.app.camera
}

/** Defines what type of angle an [AutoCamPosition] is. */
enum class AutoCamPositionType {
    /** Focuses on one of the instruments. */
    INSTRUMENT,

    /** Focuses on many instruments, or on the stage. */
    STAGE
}

@Suppress("KDocMissingDocumentation")
enum class AutoCamPosition(
    /** The location of the camera. */
    val location: Vector3f,
    /** The rotation of the camera. */
    val rotation: Quaternion,
    /** The type of camera. */
    val type: AutoCamPositionType,
    /** The instrument the camera angle focuses on */
    val instrumentClass: Class<out Instrument>? = null,
    val isClassicCamUsed: Boolean = false,
) {
    GENERAL_A(
        Vector3f(-2.00f, 92.00f, 134.00f),
        Quaternion(-0.00f, 0.99f, -0.16f, -0.00f),
        type = AutoCamPositionType.STAGE,
        isClassicCamUsed = true
    ),

    GENERAL_B(
        Vector3f(60.00f, 92.00f, 124.00f),
        Quaternion(-0.03f, 0.97f, -0.15f, -0.18f),
        type = AutoCamPositionType.STAGE,
        isClassicCamUsed = true
    ),

    GENERAL_C(
        Vector3f(-59.50f, 90.80f, 94.40f),
        Quaternion(0.03f, 0.97f, -0.18f, 0.15f),
        type = AutoCamPositionType.STAGE,
        isClassicCamUsed = true
    ),

    GENERAL_D(
        Vector3f(5f, 432f, 24f),
        Quaternion().fromAngles(Utils.rad(82.875f), Utils.rad(180f), 0f),
        type = AutoCamPositionType.STAGE,
        isClassicCamUsed = true
    ),

    BASS_GUITAR(
        Vector3f(0.20f, 81.10f, 32.20f),
        Quaternion(0.07f, 0.90f, -0.17f, 0.40f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = BassGuitar::class.java,
        isClassicCamUsed = true
    ),

    BASS_GUITAR_2(
        Vector3f(35f, 25.4f, -19f),
        Quaternion().fromAngles(2.268928f, -1.0646509f, 3.0979594f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = BassGuitar::class.java,
        isClassicCamUsed = true
    ),

    GUITAR(
        Vector3f(17.00f, 30.50f, 42.90f),
        Quaternion(-0.02f, 0.95f, 0.06f, 0.31f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Guitar::class.java,
        isClassicCamUsed = true,
    ),

    DRUM_SET(
        Vector3f(-0.2f, 61.6f, 38.6f),
        Quaternion(-5.8945218E-9f, 0.9908659f, -0.13485093f, -4.3312124E-8f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = DrumSet::class.java,
        isClassicCamUsed = true
    ),

    DRUM_SET_2(
        Vector3f(-19.6f, 78.7f, 3.8f),
        Quaternion().fromAngles(Utils.rad(27.7), Utils.rad(163.8), 0f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = DrumSet::class.java,
        isClassicCamUsed = true
    ),

    KEYBOARDS(
        Vector3f(-32.76f, 59.79f, 38.55f),
        Quaternion(-0.06f, 0.94f, -0.27f, -0.20f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Keyboard::class.java,
        isClassicCamUsed = true
    ),

    KEYBOARDS_2(
        Vector3f(-35f, 76.4f, 33.6f),
        Quaternion().fromAngles(Utils.rad(55.8), Utils.rad(198.5), 0f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Keyboard::class.java,
        isClassicCamUsed = true
    ),

    SOPRANO_SAX(
        Vector3f(18.91f, 40.76f, -11.10f),
        Quaternion(-0.04f, 0.96f, -0.19f, -0.19f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = SopranoSax::class.java,
    ),

    ALTO_SAX(
        Vector3f(0.14f, 51.05f, -18.93f),
        Quaternion(-0.05f, 0.95f, -0.21f, -0.24f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = AltoSax::class.java,
    ),

    TENOR_SAX(
        Vector3f(-1.57f, 44.77f, 43.48f),
        Quaternion(0.00f, 0.98f, -0.20f, 0.02f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = TenorSax::class.java,
    ),

    BARITONE_SAX(
        Vector3f(18.66f, 58.02f, 37.77f),
        Quaternion(0.01f, 0.98f, -0.19f, 0.06f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = BaritoneSax::class.java,
    ),

    MALLETS(
        Vector3f(-13.29f, 53.30f, 86.90f),
        Quaternion(-0.02f, 0.98f, -0.18f, -0.11f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Mallets::class.java,
    ),

    MUSIC_BOX(
        Vector3f(20.54f, 15.92f, 27.50f),
        Quaternion(0.01f, 0.97f, -0.06f, 0.22f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = MusicBox::class.java,
    ),

    TELEPHONE_RING(
        Vector3f(-10.25f, 14.42f, -30.20f),
        Quaternion(0.04f, 0.96f, -0.18f, 0.22f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = TelephoneRing::class.java,
    ),

    SPACE_LASER(
        Vector3f(-32.91f, 1.40f, 4.15f),
        Quaternion(-0.05f, 0.95f, 0.18f, 0.24f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = SpaceLaser::class.java,
    ),

    ACOUSTIC_BASS(
        Vector3f(-35.41f, 76.72f, -13.02f),
        Quaternion(-0.02f, 0.98f, -0.20f, -0.09f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = AcousticBass::class.java,
    ),

    VIOLIN(
        Vector3f(6.86f, 67.31f, 24.77f),
        Quaternion(0.01f, 0.99f, -0.12f, 0.05f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Violin::class.java,
    ),

    VIOLA(
        Vector3f(-10.58f, 39.79f, 17.41f),
        Quaternion(0.02f, 0.98f, -0.18f, 0.12f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Viola::class.java,
    ),

    CELLO(
        Vector3f(-49.57f, 62.85f, 10.76f),
        Quaternion(-0.03f, 0.97f, -0.21f, -0.15f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Cello::class.java,
    ),

    HARP(
        Vector3f(-70.15f, 78.19f, 33.63f),
        Quaternion(-0.06f, 0.94f, -0.18f, -0.29f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Harp::class.java,
    ),

    CHOIR(
        Vector3f(28.63f, 74.08f, -7.62f),
        Quaternion(0.01f, 0.99f, -0.10f, 0.08f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = StageChoir::class.java,
    ),

    TUBULAR_BELLS(
        Vector3f(-57.10f, 95.29f, -52.64f),
        Quaternion(-0.01f, 0.99f, -0.10f, -0.05f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = TubularBells::class.java,
    ),

    STAGE_STRINGS_1(
        Vector3f(-76.30f, 76.00f, -57.11f),
        Quaternion(-0.02f, 0.98f, -0.09f, -0.19f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = StageStrings::class.java,
    ),

    STAGE_STRINGS_2(
        Vector3f(-68.73f, 81.60f, -51.70f),
        Quaternion(-0.05f, 0.92f, -0.12f, -0.38f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = StageStrings::class.java,
    ),

    STAGE_STRINGS_3_PLUS(
        Vector3f(-34.77f, 87.41f, -13.06f),
        Quaternion(-0.06f, 0.86f, -0.10f, -0.49f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = StageStrings::class.java,
    ),

    STAGE_HORNS(
        Vector3f(-52.16f, 67.44f, -51.44f),
        Quaternion(-0.01f, 0.99f, -0.09f, -0.07f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = StageHorns::class.java,
    ),

    PIZZICATO_STRINGS(
        Vector3f(-68.83f, 56.76f, -52.56f),
        Quaternion(-0.05f, 0.95f, -0.24f, -0.19f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = PizzicatoStrings::class.java,
    ),

    ACCORDION(
        Vector3f(-55.93f, 44.35f, -16.56f),
        Quaternion(-0.03f, 0.97f, -0.16f, -0.16f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Accordion::class.java,
    ),

    BANJO(
        Vector3f(32.83f, 62.14f, 46.33f),
        Quaternion(0.03f, 0.97f, -0.12f, 0.23f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Banjo::class.java,
    ),

    SHAMISEN(
        Vector3f(40.64f, 73.55f, 30.32f),
        Quaternion(0.03f, 0.97f, -0.11f, 0.24f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Shamisen::class.java,
    ),

    TIMPANI(
        Vector3f(43.85f, 55.59f, -38.73f),
        Quaternion(0.01f, 0.98f, -0.21f, 0.07f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Timpani::class.java,
    ),

    MELODIC_TOM(
        Vector3f(54.01f, 83.03f, -52.03f),
        Quaternion(0.01f, 0.98f, -0.19f, 0.04f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = MelodicTom::class.java,
    ),

    SYNTH_DRUM(
        Vector3f(10.73f, 103.13f, -78.50f),
        Quaternion(0.06f, 0.90f, -0.13f, 0.42f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = SynthDrum::class.java,
    ),

    TAIKO_DRUM(
        Vector3f(19.72f, 99.17f, -121.31f),
        Quaternion(0.03f, 0.91f, -0.07f, 0.42f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = TaikoDrum::class.java,
    ),

    TROMBONE(
        Vector3f(28.212189f, 86.88116f, 41.177956f),
        Quaternion(0.026606327f, 0.97332513f, -0.1550696f, 0.16698427f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Trombone::class.java,
    ),

    FLUTE(
        Vector3f(5.84f, 54.35f, 9.74f),
        Quaternion(-0.00f, 1.00f, -0.06f, -0.03f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Flute::class.java,
    ),

    PICCOLO(
        Vector3f(5.84f, 60.25f, 9.74f),
        Quaternion(-0.00f, 1.00f, -0.06f, -0.03f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Piccolo::class.java,
    ),

    RECORDER(
        Vector3f(-4.77f, 48.54f, 9.77f),
        Quaternion(0.01f, 0.99f, -0.07f, 0.09f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Recorder::class.java,
    ),

    HARMONICA(
        Vector3f(56.07f, 38.10f, -30.10f),
        Quaternion(0.10f, 0.86f, -0.18f, 0.47f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Harmonica::class.java,
    ),

    PAN_FLUTE(
        Vector3f(58.05f, 37.40f, 0.82f),
        Quaternion(0.03f, 0.97f, -0.16f, 0.18f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = PanFlute::class.java,
    ),

    WHISTLES(
        Vector3f(58.05f, 37.40f, 0.82f),
        Quaternion(0.03f, 0.97f, -0.20f, 0.17f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Whistles::class.java,
    ),

    BLOWN_BOTTLE(
        Vector3f(58.05f, 31.78f, 0.82f),
        Quaternion(0.03f, 0.97f, -0.17f, 0.16f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = BlownBottle::class.java,
    ),

    AGOGOS(
        Vector3f(55.97f, 32.62f, 11.38f),
        Quaternion(0.02f, 0.98f, -0.17f, 0.11f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Agogos::class.java,
    ),

    WOODBLOCKS(
        Vector3f(54.60f, 29.79f, 17.34f),
        Quaternion(0.01f, 0.99f, -0.13f, 0.11f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Woodblocks::class.java,
    ),

    TUBA(
        Vector3f(-75.00f, 33.61f, 9.40f),
        Quaternion(-0.02f, 0.97f, -0.11f, -0.22f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Tuba::class.java,
    ),

    FRENCH_HORN(
        Vector3f(-82.14993f, 43.444687f, 30.638006f),
        Quaternion(-0.05368753f, 0.9447794f, -0.19924761f, -0.2545779f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = FrenchHorn::class.java,
    ),

    TRUMPET(
        Vector3f(-0.17f, 61.50f, 30.30f),
        Quaternion(-0.01f, 0.93f, -0.03f, -0.37f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Trumpet::class.java,
    ),

    OBOE(
        Vector3f(18.71f, 53.21f, 35.69f),
        Quaternion(-0.01f, 0.98f, -0.17f, -0.05f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Oboe::class.java,
    ),

    CLARINET(
        Vector3f(-13.75f, 52.54f, 37.02f),
        Quaternion(-0.01f, 0.99f, -0.15f, -0.04f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Clarinet::class.java,
    ),

    STEEL_DRUMS(
        Vector3f(46.01f, 62.34f, -29.49f),
        Quaternion(0.02f, 0.97f, -0.18f, 0.13f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = SteelDrums::class.java,
    ),

    FIDDLE(
        Vector3f(-7.67f, 79.05f, 19.95f),
        Quaternion(-0.01f, 0.98f, -0.18f, -0.04f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Fiddle::class.java,
    ),

    OCARINA(
        Vector3f(36.71f, 54.71f, 33.44f),
        Quaternion(0.04f, 0.96f, -0.18f, 0.21f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Ocarina::class.java,
    ),

    BAG_PIPE(
        Vector3f(-49.76239f, 33.13658f, 82.276375f),
        Quaternion(-0.0023348634f, 0.9805548f, -0.011687864f, -0.19588324f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = BagPipe::class.java,
    ),

    KALIMBA(
        Vector3f(20.016724f, 50.83171f, 53.29627f),
        Quaternion(-0.0011894251f, 0.9525223f, -0.30444378f, -0.0037213443f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = Kalimba::class.java,
    ),

    TINKLE_BELL(
        Vector3f(34.142365f, 50.831703f, 54.312134f),
        Quaternion(0.024830274f, 0.94041014f, -0.33174983f, 0.070386335f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = TinkleBell::class.java,
    ),

    BIRD_TWEET(
        Vector3f(126.65341f, 55.186848f, -5.279584f),
        Quaternion(0.027039362f, 0.9644452f, -0.1090079f, 0.2392313f),
        type = AutoCamPositionType.INSTRUMENT,
        instrumentClass = BirdTweet::class.java,
    ),

}

/**
 * Performs an interpolation between two quaternions.
 */
fun quaternionInterpolation(start: Quaternion, end: Quaternion, x: Float): Quaternion = Quaternion(
    start.x + (end.x - start.x) * x,
    start.y + (end.y - start.y) * x,
    start.z + (end.z - start.z) * x,
    start.w + (end.w - start.w) * x
).apply { this.normalizeLocal() }
