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
import game.content.randomEvents.VisitorRandomEvent.Kind
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
     * Applies shared eligibility and consumes one readiness flag before selecting an outcome.
     * Native locks, modal windows and current encounters defer consumption to a later eligible trigger.
     */
    internal fun offer(player: Player, select: () -> Kind?): Boolean {
        if (!allowed(player) || !canAct(player) || !player.randomEventClock.consume()) return false
        if (!Luna.settings().game().betaMode() && player.rights >= RIGHTS_ADMIN) return false
        val kind = select() ?: return false
        return startVisitor(player, kind)
    }

    /**
     * Starts the implemented encounter through the same eligibility and ownership path as natural rolls.
     * Administrators may use the command to inspect behavior without altering event probabilities.
     */
    fun startGenie(player: Player): Boolean = startVisitor(player, Kind.GENIE)

    /**
     * Creates a visitor with source NPC metadata and saves source-approved following types for login recovery.
     * Genie save identifiers remain unchanged as the catalogue grows.
     */
    fun startVisitor(player: Player, kind: Kind): Boolean {
        if (!allowed(player) || !canAct(player)) return false
        val position = nearbySquare(player) ?: return false
        val npc = Npc(ctx, kind.npcId, position).also { it.respawnTicks = -1 }
        if (!world.npcs.add(npc)) return false
        encounters.remove(player)?.let { previous ->
            if (previous.blocksNewEvents) previous.close()
            else lingering.getOrPut(player) { mutableListOf() }.add(previous)
        }
        val encounter = VisitorRandomEvent(player, npc, kind)
        encounters[player] = encounter
        player.pendingRandomEvent = if (kind.followsLogout) encounter.resumeKey else ""
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
        if (kind != null && player !in encounters) startVisitor(player, kind)
    }

    /**
     * Detaches world resources on logout; an unanswered following encounter remains saved.
     */
    fun logout(player: Player) {
        encounters.remove(player)?.close()
        lingering.remove(player)?.forEach { it.close() }
    }
}
