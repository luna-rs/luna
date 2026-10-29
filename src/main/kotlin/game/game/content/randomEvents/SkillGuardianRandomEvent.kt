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
import api.predef.ctx
import api.drops.DropTable
import api.drops.DropTableItem
import api.drops.DropTableItemList
import io.luna.game.model.Entity
import io.luna.game.model.LocalGraphic
import io.luna.game.model.chunk.ChunkUpdatableView
import api.predef.ext.removeNpc
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.wandering.WanderingFrequency

/**
 * Lost City's owner-bound skill attackers. River trolls and Tree spirits share tier selection,
 * ordinary melee combat, ownership and native death cleanup. Each retains its source warning,
 * appearance and skill-specific selection slot; neither belongs to the general visitor pool.
 * The twenty-tick active AI timer removes lost attackers. The supplied source labels their
 * thousand-active-tick lifetime as an estimate. Native death grants loot; escape and logout do not.
 *
 * @author lare96
 */
class SkillGuardianRandomEvent(override val player: Player, override val npc: Npc,
                              val kind: Kind) : RandomEventEncounter {
    /**
     * Source forms and appearance. Delays include the source engine's extra NPC delay tick.
     *
     * @author lare96
     */
    enum class Kind(val npcIds: IntRange, val warningTicks: Int, val speech: String,
                    val sound: Sound, val appearanceGraphic: Int? = null) {
        RIVER_TROLL(391..396, 3, "Fishies be mine, leave dem fishies!", Sound.WATERSPLASH),
        TREE_SPIRIT(438..443, 2, "Leave these woods and never return!", Sound.DRYAD_APPEAR, 180);

        /**
         * Source brackets: below combat 11,21,41,61,91 and the final high-level tier.
         */
        fun npcId(combatLevel: Int): Int = npcIds.first + when {
            combatLevel < 11 -> 0
            combatLevel < 21 -> 1
            combatLevel < 41 -> 2
            combatLevel < 61 -> 3
            combatLevel < 91 -> 4
            else -> 5
        }
    }

    companion object {
        /**
         * All implemented forms sharing the owner-only combat and death hooks.
         */
        val NPC_IDS = Kind.entries.flatMap { it.npcIds }
    }

    override val resumeKey get() = kind.name
    override val blocksNewEvents get() = false

    /**
     * Source NPC delay pauses its active timer and lifetime. Each instance owns its reward latch.
     */
    private var age = 0
    private var closed = false
    private var deathClaimed = false

    init {
        npc.combat.isDisabled = true
        npc.speak(kind.speech)
        player.playSound(kind.sound)
        kind.appearanceGraphic?.let {
            LocalGraphic(ctx, it, 100, 0, npc.position, ChunkUpdatableView.globalView()).display()
        }
    }

    override fun tick(): Boolean {
        if (closed || npc.state != EntityState.ACTIVE) return false
        // Keep the encounter until native post-death removal; the death hook still needs its ownership.
        if (!npc.isAlive) return true
        if (++age < kind.warningTicks) return true
        if (age == kind.warningTicks) {
            npc.combat.isDisabled = false
            npc.startWandering(3, WanderingFrequency.NORMAL)
            RandomEvents.beginHostile(this)
        }
        val activeAge = age - kind.warningTicks + 1
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
    internal fun canAttack(other: Mob): Boolean = age >= kind.warningTicks && canFight(other) && npc.combat.target === player

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

/**
 * Source members Tree spirit loot, including conditional random-herb and random-jewel rolls.
 * Flattening the nested weights preserves a single native drop and native owner privacy.
 * Ring of Wealth removes empty jewel slots only; the source underground coordinate selects
 * the talisman. Legends' Quest is not implemented, so its completion-only megarare upgrade
 * remains unavailable rather than being granted to every account.
 *
 * @author lare96
 */
internal object TreeSpiritDropTable : DropTable() {
    /**
     * Direct outcomes from the source's 128-slot Tree spirit roll: item, quantity and weight.
     */
    private val direct = listOf(
        Triple(1355, 1, 20), Triple(1353, 1, 10), Triple(1357, 1, 8), Triple(1359, 1, 4),
        Triple(561, 22, 31), Triple(561, 10, 2), Triple(561, 5, 2), Triple(561, 2, 1),
        Triple(1963, 1, 12), Triple(946, 1, 1)
    )

    /**
     * Unidentified herb weights within the source's separate 128-slot herb roll.
     */
    private val herbs = listOf(199 to 32, 201 to 24, 203 to 18, 205 to 14, 207 to 11,
                               209 to 8, 211 to 6, 213 to 5, 215 to 4, 2485 to 3, 217 to 3)

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        // Native NPC death passes the victim as mob and the killer as source.
        val player = source as? Player
        val jewelSlots = if (player?.equipment?.contains(2572) == true) 65 else 128
        val talisman = if ((player?.position?.y ?: 0) > 6400) 1452 else 1462
        return buildList {
            direct.forEach { (id, quantity, weight) -> add(DropTableItem(id, quantity, weight / 128.0)) }
            herbs.forEach { (id, weight) -> add(DropTableItem(id, 1, 29.0 / 128 * weight / 128)) }
            val jewels = listOf(Triple(1623, 1, 32), Triple(1621, 1, 16), Triple(1619, 1, 8),
                                Triple(1617, 1, 2), Triple(830, 5, 1), Triple(985, 1, 1),
                                Triple(987, 1, 1), Triple(talisman, 1, 4))
            jewels.forEach { (id, quantity, weight) ->
                add(DropTableItem(id, quantity, 8.0 / 128 * weight / jewelSlots))
            }
            if (jewelSlots == 128) add(DropTableItem(-1, 1, 8.0 / 128 * 63 / 128))
        }
    }

    override fun computePossibleItems(): DropTableItemList = computeTable(null, null).filterNot { it.isNothing() } +
        DropTableItem(1452, 1, 8.0 / 128 * 4 / 128)
}
