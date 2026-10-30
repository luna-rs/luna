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
import api.drops.ProbabilityTable
import io.luna.game.model.item.Item
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
 * Lost City's owner-bound skill attackers share tier selection,
 * ordinary melee combat, ownership and native death cleanup. Each retains its source warning,
 * appearance and skill-specific selection slot; none belongs to the general visitor pool.
 * The twenty-tick active AI timer removes lost attackers. The supplied source labels their
 * thousand-active-tick lifetime as an estimate. Native death grants loot; escape and logout do not.
 *
 * @author lare96
 */
class SkillGuardianRandomEvent(override val player: Player, override val npc: Npc,
                              val kind: Kind) : RandomEventEncounter {
    /**
     * Source forms, appearance and defence sounds. Missing sounds remain silent and each graphic
     * retains its source height. Speech may address the owner with {name}. Delays include the
     * source engine's extra NPC delay tick.
     *
     * @author lare96
     */
    enum class Kind(val npcIds: IntRange, val warningTicks: Int, val speech: String,
                    val sound: Sound?, val appearanceGraphic: Int? = null, val appearanceHeight: Int = 100,
                    val defendSound: Sound? = null) {
        RIVER_TROLL(391..396, 3, "Fishies be mine, leave dem fishies!", Sound.WATERSPLASH),
        TREE_SPIRIT(438..443, 2, "Leave these woods and never return!", Sound.DRYAD_APPEAR, 180),
        SHADE(425..430, 4, "Leave this place mortal!", sound = null, appearanceGraphic = 78, appearanceHeight = 0,
              defendSound = Sound.GHOST_HIT),
        ZOMBIE(419..424, 4, "Brainsssss...", Sound.ZOMBIE_MOAN, appearanceGraphic = 78, appearanceHeight = 0,
               defendSound = Sound.ZOMBIE_HIT),
        WATCHMAN(431..436, 3, "{name}, you are under arrest!", Sound.SMOKEPUFF,
                 appearanceGraphic = 86, appearanceHeight = 124, defendSound = Sound.HUMAN_HIT4);

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
        npc.speak(kind.speech.replace("{name}", player.username))
        kind.sound?.let { player.playSound(it) }
        kind.appearanceGraphic?.let {
            LocalGraphic(ctx, it, kind.appearanceHeight, 0, npc.position, ChunkUpdatableView.globalView()).display()
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
 * Scales a nested table's marginal weights while leaving unrelated rewards untouched. Herb and
 * jewel consumers share this conversion; their native rolls keep using the same table definitions.
 */
private fun DropTable.weighted(mob: Mob?, source: Entity?, chance: Double): DropTableItemList =
    computeTable(mob, source).map { DropTableItem(it.id, it.amount, it.chance * chance) }

/**
 * Source members Tree spirit loot, including conditional random-herb and random-jewel rolls.
 * Flattening the nested weights preserves a single native drop and native owner privacy.
 * Shared jewel selection preserves Ring of Wealth and underground talisman behavior without
 * adding a second reward or changing the direct and herb probabilities.
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

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        return buildList {
            direct.forEach { (id, quantity, weight) -> add(DropTableItem(id, quantity, weight / 128.0)) }
            addAll(GuardianHerbDropTable.weighted(mob, source, 29.0 / 128))
            addAll(GuardianJewelDropTable.weighted(mob, source, 8.0 / 128))
        }
    }

    override fun computePossibleItems(): DropTableItemList = computeTable(null, null).filterNot { it.isNothing() } +
        DropTableItem(1452, 1, 8.0 / 128 * 4 / 128)
}

/**
 * Lost City's members randomherb subtable. Tree spirits and Watchmen share the same 128-slot
 * unidentified-herb weights; the choice is independent of equipment and Ring of Wealth.
 *
 * @author lare96
 */
internal object GuardianHerbDropTable : DropTable() {
    /**
     * Item identities and source weights, totaling 128 slots.
     */
    private val herbs = listOf(199 to 32, 201 to 24, 203 to 18, 205 to 14, 207 to 11,
                               209 to 8, 211 to 6, 213 to 5, 215 to 4, 2485 to 3, 217 to 3)

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList =
        herbs.map { (id, weight) -> DropTableItem(id, 1, weight / 128.0) }

    override fun computePossibleItems(): DropTableItemList = computeTable(null, null)
}

/**
 * Lost City's members randomjewel subtable, shared by skill guardians. Native NPC death passes
 * the victim as mob and the killer as source. Ring of Wealth removes empty slots within this
 * subroll only; underground coordinates change its talisman. Legends' Quest is not implemented,
 * so the completion-only megarare upgrade remains unavailable rather than being given to all.
 *
 * @author lare96
 */
internal object GuardianJewelDropTable : DropTable() {
    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList {
        val player = source as? Player
        val slots = if (player?.equipment?.contains(2572) == true) 65 else 128
        val talisman = if ((player?.position?.y ?: 0) > 6400) 1452 else 1462
        return buildList {
            val jewels = listOf(Triple(1623, 1, 32), Triple(1621, 1, 16), Triple(1619, 1, 8),
                                Triple(1617, 1, 2), Triple(830, 5, 1), Triple(985, 1, 1),
                                Triple(987, 1, 1), Triple(talisman, 1, 4))
            jewels.forEach { (id, quantity, weight) -> add(DropTableItem(id, quantity, weight.toDouble() / slots)) }
            if (slots == 128) add(DropTableItem(-1, 1, 63.0 / 128))
        }
    }

    override fun computePossibleItems(): DropTableItemList = computeTable(null, null).filterNot { it.isNothing() } +
        DropTableItem(1452, 1, 4.0 / 128)
}

/**
 * Lost City's members zombie loot. The source marks its 256-slot rates as estimates, including
 * the swordfish outcome. Every death gives one default big bone plus one correlated reward
 * bundle. Duplicate bone entries stay separate as in the source; the final slot rolls randomjewel.
 * Preview entries show marginal chances, while native rolling preserves whole bundles together.
 *
 * @author lare96
 */
internal object ZombieDropTable : DropTable() {
    /**
     * Source weights and item/quantity pairs. The one-slot empty bundle requests randomjewel;
     * it never suppresses the default big bone. Stored pairs become fresh item stacks for each roll.
     */
    private val bundles: List<Pair<Int, List<Pair<Int, Int>>>> = listOf(
        36 to listOf(313 to 50),
        24 to listOf(313 to 27),
        32 to listOf(562 to 35),
        8 to listOf(562 to 50),
        28 to listOf(373 to 1),
        16 to listOf(564 to 5),
        16 to listOf(995 to 26),
        40 to listOf(532 to 1),
        32 to listOf(532 to 2),
        8 to listOf(532 to 6, 526 to 3),
        5 to listOf(536 to 1, 532 to 1),
        1 to listOf(536 to 2, 532 to 10, 526 to 10),
        1 to listOf(536 to 3),
        8 to listOf(532 to 4, 532 to 2),
        1 to emptyList()
    )

    override fun roll(mob: Mob?, source: Entity?): MutableList<Item> {
        val bundle = ProbabilityTable(bundles.map { (weight, items) -> weight / 256.0 to items }).roll().orEmpty()
        return mutableListOf(Item(532)).apply {
            if (bundle.isEmpty()) addAll(GuardianJewelDropTable.roll(mob, source))
            else bundle.forEach { (id, quantity) -> add(Item(id, quantity)) }
        }
    }

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList = buildList {
        add(DropTableItem(532, 1, 1.0))
        bundles.forEach { (weight, items) ->
            items.forEach { (id, quantity) -> add(DropTableItem(id, quantity, weight / 256.0)) }
        }
        addAll(GuardianJewelDropTable.weighted(mob, source, 1.0 / 256))
    }

    override fun computePossibleItems(): DropTableItemList = computeTable(null, null).filterNot { it.isNothing() } +
        DropTableItem(1452, 1, 1.0 / 256 * 4 / 128)
}

/**
 * Lost City's members Watchman loot. Its source labels these rates as guesses: a default bone
 * accompanies one 128-slot reward, with uniform one-to-three steel bars and separate herb/jewel
 * subrolls. Shared subtable weights preserve conditional bonuses without changing direct loot.
 *
 * @author lare96
 */
internal object WatchmanDropTable : DropTable() {
    /**
     * Direct item, quantity-range and weight entries from the supplied script, totaling 120 slots.
     */
    private val direct = listOf(
        Triple(1281, 1..1, 8), Triple(1141, 1..1, 6), Triple(453, 1..1, 6), Triple(2353, 1..3, 8),
        Triple(995, 2..2, 16), Triple(995, 16..16, 14), Triple(995, 20..20, 12), Triple(995, 30..30, 10),
        Triple(995, 50..50, 8), Triple(995, 70..70, 6), Triple(995, 105..105, 4),
        Triple(555, 50..50, 7), Triple(559, 22..22, 5), Triple(563, 3..3, 4), Triple(560, 3..3, 4),
        Triple(561, 8..8, 2)
    )

    override fun computeTable(mob: Mob?, source: Entity?): DropTableItemList = buildList {
        add(DropTableItem(526, 1, 1.0))
        direct.forEach { (id, amount, weight) -> add(DropTableItem(id, amount, weight / 128.0)) }
        addAll(GuardianHerbDropTable.weighted(mob, source, 4.0 / 128))
        addAll(GuardianJewelDropTable.weighted(mob, source, 4.0 / 128))
    }

    override fun computePossibleItems(): DropTableItemList = computeTable(null, null).filterNot { it.isNothing() } +
        DropTableItem(1452, 1, 4.0 / 128 * 4 / 128)
}
