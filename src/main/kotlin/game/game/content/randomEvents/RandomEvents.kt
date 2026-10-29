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

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.castleWars.CastleWarsGame
import game.content.duelArena.DuelArena
import game.content.fishingTrawler.FishingTrawler
import game.content.gnomeBall.GnomeBall
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.fishingSpot.FishingSpotHandler
import io.luna.Luna
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import kotlin.random.Random

/**
 * Shared selection, ownership and login/logout lifecycle for Lost City random encounters.
 * Native gameplay is shared by players and bots. Bot response policy is a separate consumer.
 *
 * General selection retains all six slots from the supplied members enum: unimplemented results
 * are skipped rather than rerolled into extra genies or Old Men. Native activity hooks retain
 * each skill's additional hazard slots; remaining encounters are subsequent content additions.
 *
 * @author lare96
 */
object RandomEvents {
    /**
     * Source identities and gift rules shared by the dispatcher and encounters. The general
     * slot is zero-based within Lost City's six-entry members table; enum names are save identifiers.
     *
     * @author lare96
     */
    enum class Kind(val npcId: Int, val generalSlot: Int, val followsLogout: Boolean = true) {
        SWARM(411, 0, false),
        GENIE(409, 1),
        MYSTERIOUS_OLD_MAN(410, 3),
        DRUNKEN_DWARF(956, 2, false),
        STRANGE_PLANT(407, 4, false);

        /**
         * Chooses gifts from the source members reward tables. The old man's 150 equally likely
         * results are 16 kebabs, 14 spinach rolls, six 240-coin gifts and 114 caskets. The dwarf
         * always gives both a kebab and a beer, without a reward roll.
         */
        fun rewards(random: (Int) -> Int = { Random.nextInt(it) }): List<Item> = when (this) {
            SWARM, STRANGE_PLANT -> emptyList()
            GENIE -> listOf(Item(2528))
            DRUNKEN_DWARF -> listOf(Item(1971), Item(1917))
            MYSTERIOUS_OLD_MAN -> listOf(when (random(150)) {
                in 0..15 -> Item(1971)
                in 16..29 -> Item(1969)
                in 30..35 -> Item(995, 240)
                else -> Item(405)
            })
        }
    }

    /**
     * Extra skill-specific slots preceding the six general members events in Lost City's dispatchers.
     * Rune essence and other rocks without a gas replacement exclude rock gas. Pickpockets and stalls share
     * one watchman slot; trapped chests add poison gas as well. Bone burial
     * adds zombie and shade slots. Unimplemented outcomes consume readiness without a replacement roll.
     *
     * @author lare96
     */
    enum class Activity(private val specialSlots: Int) {
        MINING(3),
        MINING_WITHOUT_GAS(2),
        WOODCUTTING(3),
        FISHING(3),
        THIEVING(1),
        THIEVING_CHEST(2),
        PRAYER(2);

        /**
         * Preserves the source's two-stage choice: skill hazard versus general pool, then one
         * of the six general events. A missing native implementation does not favor other events.
         */
        internal fun select(random: (Int) -> Int): Kind? {
            if (random(6 + specialSlots) < specialSlots) return null
            val slot = random(6)
            return Kind.entries.firstOrNull { it.generalSlot == slot }
        }
    }

    /**
     * Lost City's general timer interval, distinct from the world readiness polling boundary.
     */
    private const val GENERAL_TIMER = 500

    /**
     * Persisted activity areas and restartable encounter identity; NPC references are never saved.
     */
    internal val Player.randomEventClock by Attr.obj { RandomEventClock() }.persist("random_event_clock")
    internal var Player.pendingRandomEvent by Attr.string().persist("random_event_pending")
    private var Player.generalEventTicks by Attr.int()

    /**
     * Live encounter ownership. Keeping exact instances prevents stale callbacks granting rewards.
     */
    private val encounters = mutableMapOf<Player, RandomEventEncounter>()

    /**
     * Encounters that released their personal-event slot but still own a live NPC, such as an angry dwarf.
     * They keep ticking and receiving identity-checked clicks until their own lifecycle ends.
     */
    private val lingering = mutableMapOf<Player, MutableList<RandomEventEncounter>>()

    /**
     * Lost City's shared area restrictions. Castle Wars separately prevents spawning, but does not
     * belong here because existing visitors can follow their owner into its arena in the source.
     */
    fun areaAllowed(position: Position): Boolean = !DuelArena.arena(position) && !GnomeBall.inside(position) &&
        !FishingTrawler.atSea(position) && !FishingTrawler.inWreck(position) &&
        !(position.x in 3053..3156 && position.y in 3056..3136) &&
        !(position.x in 3072..3118 && position.y in 9492..9535)

    /**
     * Checks native lifecycle, source exclusions and the personal event slot before reading readiness.
     * Banks and the Wilderness are eligible. Bots are not excluded.
     */
    fun allowed(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        areaAllowed(player.position) && !CastleWarsGame.inArena(player.position) &&
        !CastleWarsGame.inWaitingRoom(player.position) && encounters[player]?.blocksNewEvents != true

    /**
     * Soft content triggers wait for a player-owned delay or modal interaction to finish.
     */
    internal fun canAct(player: Player): Boolean = !player.isLocked &&
        player.actions.size(ActionType.STRONG) == 0 && !player.overlays.hasWindow()

    /**
     * Consumes a source readiness flag and selects the general event once. Failed selection or an
     * unavailable spawn tile does not grant another roll. Activity dispatchers supply their own pool.
     */
    fun offerGeneral(player: Player): Boolean {
        return offer(player) {
            val slot = Random.nextInt(6)
            Kind.entries.firstOrNull { it.generalSlot == slot }
        }
    }

    /**
     * Offers the general source pool before a valid production attempt consumes its ingredients.
     * Callers check levels and tools first and invoke this only on initial submission, not each
     * Make-X cycle. Missing supplies preserve readiness for the next eligible interaction.
     */
    fun offerProduction(player: Player, ingredients: List<Item>): Boolean =
        player.inventory.containsAll(ingredients) && offerGeneral(player)

    /**
     * Offers an encounter at a native skill boundary, independently of success rolls and XP changes.
     * A successful start lets the caller stop its attempt before consuming tools, bait or supplies.
     * Bone burial instead calls after its normal item consumption and experience award.
     */
    fun offerActivity(player: Player, activity: Activity): Boolean =
        offer(player) { activity.select { Random.nextInt(it) } }

    /**
     * Offers the source fishing pool with its live spot. A whirlpool transforms that shared NPC,
     * rather than owning a personal visitor slot. River trolls use their owner-bound combat lifecycle;
     * the big fish outcome remains unimplemented.
     * Existing whirlpools do not consume another readiness flag when a player clicks them again.
     */
    fun offerFishing(player: Player, npc: Npc, spot: FishingSpot): Boolean =
        offerFishing(player, npc, spot) { Random.nextInt(it) }

    /**
     * Resolves one fishing outcome after shared eligibility, preserving both source random rolls.
     * Unavailable and unimplemented outcomes consume readiness without a replacement roll.
     */
    internal fun offerFishing(player: Player, npc: Npc, spot: FishingSpot, random: (Int) -> Int): Boolean {
        if (npc.state != EntityState.ACTIVE || npc.id !in spot.npcs || spot.whirlpoolId == null) return false
        return offerOutcome(player) {
            when (random(9)) {
                0 -> false
                1 -> startRiverTroll(player)
                2 -> FishingSpotHandler.startWhirlpool(player, npc, spot)
                else -> {
                    val slot = random(6)
                    Kind.entries.firstOrNull { it.generalSlot == slot }
                        ?.let { startEncounter(player, it) } ?: false
                }
            }
        }
    }

    /**
     * Applies shared eligibility and consumes one readiness flag before selecting an outcome.
     * Native locks, modal windows and current encounters defer consumption to a later eligible trigger.
     */
    internal fun offer(player: Player, select: () -> Kind?): Boolean {
        return offerOutcome(player) { select()?.let { startEncounter(player, it) } ?: false }
    }

    /**
     * Shares the readiness boundary between personal encounters and changes to world resources.
     */
    private fun offerOutcome(player: Player, select: () -> Boolean): Boolean {
        if (!allowed(player) || !canAct(player) || !player.randomEventClock.consume()) return false
        if (!Luna.settings().game().betaMode() && player.rights >= RIGHTS_ADMIN) return false
        return select()
    }

    /**
     * Starts the implemented encounter through the same eligibility and ownership path as natural rolls.
     * Administrators may use the command to inspect behavior without altering event probabilities.
     */
    fun startGenie(player: Player): Boolean = startEncounter(player, Kind.GENIE)

    /**
     * Creates an encounter with source NPC metadata and saves source-approved following types for login recovery.
     * Genie save identifiers remain unchanged as the catalogue grows.
     */
    fun startEncounter(player: Player, kind: Kind): Boolean =
        spawnEncounter(player, kind.npcId, kind.followsLogout) { npc ->
            when (kind) {
                Kind.SWARM -> SwarmRandomEvent(player, npc)
                Kind.STRANGE_PLANT -> StrangePlantRandomEvent(player, npc)
                else -> VisitorRandomEvent(player, npc, kind)
            }
        }

    /**
     * Starts the source fishing attacker at the owner's current combat bracket. It is absent from
     * the general visitor pool and never creates a saved gift or following entitlement.
     */
    fun startRiverTroll(player: Player): Boolean =
        spawnEncounter(player, RiverTrollRandomEvent.npcId(player.combatLevel), false) { RiverTrollRandomEvent(player, it) }

    /**
     * Shares safe placement and exact encounter ownership between general visitors and skill attackers.
     */
    private fun spawnEncounter(player: Player, npcId: Int, followsLogout: Boolean,
                               create: (Npc) -> RandomEventEncounter): Boolean {
        if (!allowed(player) || !canAct(player)) return false
        val position = nearbySquare(player) ?: return false
        val npc = Npc(ctx, npcId, position).also { it.respawnTicks = -1 }
        if (!world.npcs.add(npc)) return false
        encounters.remove(player)?.let { previous ->
            if (previous.blocksNewEvents) previous.close()
            else lingering.getOrPut(player) { mutableListOf() }.add(previous)
        }
        val encounter = create(npc)
        encounters[player] = encounter
        player.pendingRandomEvent = if (followsLogout) encounter.resumeKey else ""
        return true
    }

    /**
     * Finds an adjacent, unblocked tile with a traversable step to the owner, rather than spawning
     * through a wall. This is the one-tile line-of-walk search used by the general source dispatcher.
     */
    internal fun nearbySquare(player: Player): Position? = Direction.ALL_EXCEPT_NONE.shuffled().firstNotNullOfOrNull {
        val position = player.position.translate(1, it)
        if (!world.collisionManager.isBlocked(position, false) &&
            world.collisionManager.traversable(player.position, EntityType.NPC, it, false)) position else null
    }

    /**
     * Routes clicks by exact live NPC identity, so unrelated or finished visitors cannot pay out.
     */
    fun talk(player: Player, npc: Npc) {
        ownerOf(npc)?.talk(player)
    }

    /**
     * Resolves exact live NPC identity across current and lingering encounters, including combat hooks.
     */
    internal fun ownerOf(npc: Npc): RandomEventEncounter? =
        (encounters.values.asSequence() + lingering.values.asSequence().flatten()).firstOrNull { it.npc === npc }

    /**
     * Confirms that an encounter still owns its NPC even after releasing its player's event slot.
     */
    internal fun owns(encounter: RandomEventEncounter): Boolean =
        encounters[encounter.player] === encounter || lingering[encounter.player]?.any { it === encounter } == true

    /**
     * Starts an owned hostile NPC using the source's NPC-combat takeover. Recent player-combat
     * ownership, cooldowns and queued impacts remain under the native combat rules.
     */
    internal fun beginHostile(encounter: RandomEventEncounter) {
        val combat = encounter.player.combat
        if (combat.lastCombatWith is Npc) combat.lastCombatWith = encounter.npc
        encounter.npc.combat.attack(encounter.player)
    }

    /**
     * Returns the current owned encounter for bot response and native identity checks.
     */
    fun encounter(player: Player): RandomEventEncounter? = encounters[player]

    /**
     * Clears a satisfied entitlement before inventory or dialogue callbacks can run again.
     */
    internal fun answered(encounter: RandomEventEncounter) {
        if (encounters[encounter.player] === encounter) encounter.player.pendingRandomEvent = ""
    }

    /**
     * Advances live encounters and the general soft timer. The engine poll is world-aligned; the
     * general timer starts at login and can leave readiness queued while an event or interface blocks it.
     */
    fun tick(player: Player) {
        val clock = player.randomEventClock
        if (world.currentTick % GENERAL_TIMER == 0L) clock.poll()
        val encounter = encounters[player]
        if (encounter != null && (!player.isAlive || !areaAllowed(player.position) || !encounter.tick())) {
            encounters.remove(player)
            player.pendingRandomEvent = ""
            encounter.close()
        }
        lingering[player]?.let { retired ->
            retired.removeAll { previous ->
                if (!player.isAlive || !areaAllowed(player.position) || !previous.tick()) {
                    previous.close()
                    true
                } else false
            }
            if (retired.isEmpty()) lingering.remove(player)
        }
        if (player.generalEventTicks < GENERAL_TIMER) player.generalEventTicks++
        if (player.generalEventTicks >= GENERAL_TIMER && canAct(player)) {
            player.generalEventTicks = 0
            offerGeneral(player)
        }
        clock.observe(player.position.x, player.position.y, player.isPendingPlacement)
    }

    /**
     * Resets runtime readiness and restores only source-approved following encounters after login.
     * A blocked restoration remains queued until the player can accept a soft trigger.
     */
    fun login(player: Player) {
        player.randomEventClock.consume()
        player.generalEventTicks = 0
        if (Kind.entries.none { it.followsLogout && it.name == player.pendingRandomEvent }) player.pendingRandomEvent = ""
    }

    /**
     * Retries a saved encounter without another random roll. Death clears the saved entitlement.
     */
    fun restore(player: Player) {
        if (!player.isAlive) player.pendingRandomEvent = ""
        val kind = Kind.entries.firstOrNull { it.followsLogout && it.name == player.pendingRandomEvent }
        if (kind != null && player !in encounters) startEncounter(player, kind)
    }

    /**
     * Detaches world resources on logout; an unanswered following encounter remains saved.
     */
    fun logout(player: Player) {
        encounters.remove(player)?.close()
        lingering.remove(player)?.forEach { it.close() }
    }
}
