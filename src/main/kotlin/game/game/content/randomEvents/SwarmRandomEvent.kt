/*
 * Random-event behavior adapted from LostCityRS/Content (377-wip) and LostCityRS/Engine-TS,
 * engine commit 1d25566cb53e7af1b1cb18ade8af996316c19614.
 * MIT License
 *
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package game.content.randomEvents

import api.predef.world
import api.predef.ext.removeNpc
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Lost City's owner-bound swarm. It attacks immediately, has no gift or attack option, and releases
 * the personal-event slot at spawn. Native combat owns melee accuracy, pursuit, the seven-tick cooldown
 * and damage delivery. Every twenty ticks it leaves after losing its owner beyond three tiles.
 * Logout and death remove the temporary NPC; this encounter never restores on login.
 *
 * @author lare96
 */
class SwarmRandomEvent(override val player: Player, override val npc: Npc) : RandomEventEncounter {
    override val resumeKey get() = RandomEvents.Kind.SWARM.name
    override val blocksNewEvents get() = false

    /**
     * Source AI timer cadence, independent of combat cooldown and world-aligned readiness polling.
     */
    private var age = 0

    init {
        player.playSound(Sound.SWARM_APPEAR)
        RandomEvents.beginHostile(this)
    }

    override fun tick(): Boolean {
        if (npc.state != EntityState.ACTIVE) return false
        if (++age % 20 != 0) return true
        if (npc.position.z != player.position.z ||
            npc.combat.target == null && !npc.position.isWithinDistance(player.position, 3)) return false
        if (npc.combat.target !== player) npc.combat.attack(player)
        return true
    }

    override fun talk(visitor: Player) = Unit

    override fun close() {
        npc.combat.target = null
        npc.navigator.cancel()
        if (npc.state != EntityState.INACTIVE) world.removeNpc(npc)
    }

    /**
     * Rejects unrelated, stale or removed swarms before a native attack can commit or play audio.
     * The exact owner remains the only victim, including when another personal event has started.
     */
    internal fun canAttack(target: Mob): Boolean = target === player && RandomEvents.owns(this) &&
        npc.state == EntityState.ACTIVE && npc.isAlive && npc.combat.target === player &&
        player.state == EntityState.ACTIVE && player.isAlive
}
