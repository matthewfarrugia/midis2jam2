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
package org.wysko.midis2jam2.particle

import kotlin.time.Duration

/**
 * Spawns particle effects.
 */
interface ParticleGenerator {
    /**
     * Updates the animation of this generator.
     *
     * @param delta The amount of time, in seconds, since the last frame.
     * @param active `true` if this particle generator should be generating, `false` otherwise.
     */
    fun tick(delta: Duration, active: Boolean)

    /**
     * A particle that is generated by a [ParticleGenerator].
     */
    interface Particle {
        /**
         * Update animation.
         *
         * @param delta The amount of time, in seconds, since the last frame.
         * @return `true` if this particle should live, `false` otherwise.
         */
        fun tick(delta: Duration): Boolean
    }
}
