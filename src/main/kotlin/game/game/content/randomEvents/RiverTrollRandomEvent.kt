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
import io.luna.game.model.mob.movement.wandering.WanderingFrequency

/**
 * Lost City's fishing attacker. Six tiers follow the owner's current combat bracket. The troll
 * warns before its three-tick NPC delay ends, then uses ordinary owner-bound melee combat.
 * Its twenty-tick active AI timer removes it after losing its owner; the supplied source labels
 * its thousand-active-tick lifetime as an estimate. It releases the personal slot immediately.
 * Native death owns the corpse and drop sequence; logout or escape cleanup never gives loot.
 *
 * @author lare96
 */
class RiverTrollRandomEvent(override val player: Player, override val npc: Npc) : RandomEventEncounter {
    companion object {
        /**
         * All six source NPC forms, shared by combat hooks and the native drop definitions.
         */
        val NPC_IDS = 391..396

        /**
         * Source brackets: below combat 11,21,41,61,91 and the final high-level tier.
         */
        fun npcId(combatLevel: Int): Int = when {
            combatLevel < 11 -> 391
            combatLevel < 21 -> 392
            combatLevel < 41 -> 393
            combatLevel < 61 -> 394
            combatLevel < 91 -> 395
            else -> 396
        }
    }

    override val resumeKey get() = "RIVER_TROLL"
    override val blocksNewEvents get() = false

    /**
     * Source NPC delay pauses its active timer and lifetime. Each instance owns its reward latch.
     */
    private var age = 0
    private var closed = false
    private var deathClaimed = false

    init {
        npc.combat.isDisabled = true
        npc.speak("Fishies be mine, leave dem fishies!")
        player.playSound(Sound.WATERSPLASH)
    }

    override fun tick(): Boolean {
        if (closed || npc.state != EntityState.ACTIVE) return false
        // Keep the encounter until native post-death removal; the death hook still needs its ownership.
        if (!npc.isAlive) return true
        if (++age < 3) return true
        if (age == 3) {
            npc.combat.isDisabled = false
            npc.startWandering(3, WanderingFrequency.NORMAL)
            RandomEvents.beginHostile(this)
        }
        val activeAge = age - 2
        if (activeAge >= 1000) return false
        if (activeAge % 20 == 0 && (npc.position.z != player.position.z ||
                npc.combat.target == null && !npc.position.isWithinDistance(player.position, 3))) return false
        return true
    }

    override fun talk(visitor: Player) = Unit

    override fun close() {
        closed = true
        npc.combat.target = null
        npc.navigator.cancel()
        if (npc.state != EntityState.INACTIVE) world.removeNpc(npc)
    }

    /**
     * Restricts admission and damage to the exact live owner, including before the warning ends.
     */
    internal fun canFight(other: Mob): Boolean = !closed && other === player && RandomEvents.owns(this) &&
        npc.state == EntityState.ACTIVE && npc.isAlive && player.state == EntityState.ACTIVE && player.isAlive

    /**
     * Native melee may launch only after the warning, against the current owned target.
     */
    internal fun canAttack(other: Mob): Boolean = age >= 3 && canFight(other) && npc.combat.target === player

    /**
     * Claims one native death drop for the recorded player killer. Dead NPCs remain owned until
     * post-death removal, while stale, escaped, unowned and repeated callbacks cannot grant items.
     */
    internal fun claimDeath(killer: Mob?): Boolean {
        if (closed || deathClaimed || killer !== player || npc.state != EntityState.ACTIVE ||
            npc.isAlive || !RandomEvents.owns(this)) return false
        deathClaimed = true
        return true
    }
}
