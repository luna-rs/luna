package game.bot.scripts.combat

import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import engine.bot.gear.BotGearPurpose
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.combat.npcHooks.rockCrab.RockCrabs
import game.skill.slayer.SlayerMaster
import game.skill.slayer.SlayerTaskType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.CombatSpell
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Power-trains on the rock crabs north of Rellekka. The bot picks a spot next to the disguised rocks, stands there and
 * fights whatever wakes up. It never loots. Eating and prayer are handled the same way as in [NpcCombatScript].
 *
 * After [RockCrabs.TOLERANCE_MINUTES] the crabs stop waking for the bot. Once it notices, it walks far enough away for
 * them to forget it, pauses, then comes back, sometimes to a different spot.
 *
 * Personality:
 * - Intelligent bots pick spots with the most rocks around them, and notice quickly when the crabs stop waking.
 *   Careless bots can stand next to sleeping rocks for minutes first.
 * - Dextrous bots wake the next rock straight away. Clumsy bots take idle breaks between fights.
 * - Less confident bots are more likely to move to a new spot after a reset.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param purpose The combat discipline used for this session.
 * @param spell The elemental spell autocast in a Magic session.
 * @param slayerTask An optional rock crab Slayer task this session works on.
 * @param slayerMaster The master that assigned [slayerTask].
 * @author lare96
 */
class RockCrabBotScript(bot: Bot,
                        duration: Duration,
                        purpose: BotGearPurpose = BotGearPurpose.MELEE,
                        spell: CombatSpell? = null,
                        slayerTask: SlayerTaskType? = null,
                        slayerMaster: SlayerMaster? = null) :
    NpcCombatScript(bot, duration, mutableListOf(SubZone.ROCK_CRABS),
                    ImmutableSetMultimap.of(SubZone.ROCK_CRABS, NAME), purpose, spell, slayerTask, slayerMaster) {

    companion object {

        /**
         * The name of an awake rock crab.
         */
        const val NAME = "Rock Crab"

        /**
         * How far around a spot, in tiles, the bot counts and wakes rocks.
         */
        const val CAMP_RADIUS = 3

        /**
         * How far from its spot the bot walks to make the crabs forget it. Tolerance resets 10 tiles away from where
         * the bot settled, so this keeps well clear of that, about a minimap away.
         */
        val RESET_DISTANCE = 22..30

        /**
         * How many places the bot tries to walk to before giving up on a reset.
         */
        const val RESET_ATTEMPTS = 4
    }

    /**
     * Recreates a rock crab script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved script data.
     */
    constructor(bot: Bot, data: NpcCombatData) : this(bot, data.duration, data.purpose, data.spell, data.slayerTask,
                                                      data.slayerMaster)

    /**
     * The tile the bot trains from, or `null` if it hasn't picked one.
     */
    private var spot: Position? = null

    /**
     * The rock crabs around [spot].
     */
    private var rocks: List<Npc> = emptyList()

    /**
     * When the bot started waiting next to a sleeping rock, and where it was standing.
     */
    private var waiting: Pair<Long, Position>? = null

    /**
     * How long the bot waits next to a sleeping rock before it realizes the crabs have stopped waking, in milliseconds.
     */
    private var noticeAfter = rollNoticeAfter()

    // Waiting out respawns and walking off to reset the crabs all happen without a target.
    override val targetSearchTimeout: Duration = 15.minutes

    override fun isLoot(item: GroundItem) = false

    // Reactive pickups must leave this power-training session alone too.
    override fun allowsLoot(item: GroundItem) = false

    override suspend fun refocus(): Boolean {
        val current = focus
        if (current != null && RockCrabs.isDisguised(current)) {
            // It went back to sleep, so stop trying to attack it.
            return true
        }
        return super.refocus()
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        val options = super.find(searchBase, searchRadius)
        if (!isActionSafe() || forceBanking || !prepareCombat()) {
            return options
        }
        if (bot.combat.inCombat()) {
            waiting = null
            return options
        }
        val waking = options.filter { it.combat.target == bot }
        if (waking.isNotEmpty()) {
            waiting = null
            return waking.toMutableList()
        }
        camp()
        return mutableListOf()
    }

    /**
     * Stands at the bot's spot and gets the rocks around it to wake, or walks off to reset them once they stop.
     */
    private suspend fun camp() {
        val zone = activeZone ?: return
        val spot = spot ?: pickSpot(zone) ?: return
        if (!bot.isWithinDistance(spot, CAMP_RADIUS + 1)) {
            waiting = null
            if (!handler.webWalkTo(spot, bank = BankAccess.NONE)) {
                bot.log("Could not walk to rock crab spot $spot. Picking another one.")
                this.spot = null
            }
            return
        }

        val sleeping = rocks.filter { it.state == EntityState.ACTIVE && RockCrabs.isDisguised(it) }
        if (sleeping.any { bot.isWithinDistance(it, 1) }) {
            // A crab that still minds us wakes the moment we step next to it.
            val (since, tile) = waiting ?: (System.currentTimeMillis() to bot.position).also { waiting = it }
            if (tile != bot.position) {
                waiting = null
            } else if (System.currentTimeMillis() - since >= noticeAfter) {
                resetCrabs(spot)
            } else {
                idle()
            }
            return
        }
        waiting = null
        if (sleeping.isEmpty()) {
            // Everything around us is dead or busy, wait for a respawn.
            idle()
            return
        }
        if (!rand(bot.personality.dexterity) && rand(0.3)) {
            idle(10.seconds, 40.seconds)
            if (bot.combat.inCombat()) return
        }
        wake(sleeping)
    }

    /**
     * Steps next to one of the [sleeping] rocks so that it wakes up.
     */
    private suspend fun wake(sleeping: List<Npc>) {
        val rock = if (rand(bot.personality.dexterity)) sleeping.minBy { bot.position.computeLongestDistance(it.position) }
        else sleeping.random()
        val tile = standingTiles(rock.position).minByOrNull { bot.position.computeLongestDistance(it) } ?: return
        handler.webWalkTo(tile, bank = BankAccess.NONE)
    }

    /**
     * Walks far enough away for the crabs to forget the bot, pauses, then lets [camp] bring it back.
     */
    private suspend fun resetCrabs(spot: Position) {
        bot.log("Rock crabs have stopped waking. Walking away so they forget about us.")
        val started = System.currentTimeMillis()
        var reset = false
        for (destination in resetDestinations(spot).take(RESET_ATTEMPTS)) {
            if (!isActionSafe() || forceBanking) return
            if (!handler.webWalkTo(destination, radius = 2, bank = BankAccess.NONE)) continue
            // The tolerance timer restarts on the way out, so it's now shorter than the walk.
            if (bot.tolerance.duration.toMillis() < System.currentTimeMillis() - started) {
                reset = true
                break
            }
        }
        if (!reset) {
            bot.log("Could not get far enough away from the rock crabs to reset them.")
        }
        idle(3.seconds, 15.seconds)
        waiting = null
        noticeAfter = rollNoticeAfter()
        if (!reset || rand(1.0 - bot.personality.confidence)) {
            // Try somewhere else this time.
            this.spot = null
        }
    }

    /**
     * Picks a tile next to a rock in [zone]. Intelligent bots pick the tiles with the most rocks around them, everyone
     * avoids tiles other players are already using.
     */
    private fun pickSpot(zone: SubZone): Position? {
        val crabs = world.npcs.filter { RockCrabs.isRockCrab(it) && zone.area.contains(it.basePosition) }
        val scores = HashMap<Position, Int>()
        for (crab in crabs) {
            for (tile in standingTiles(crab.basePosition)) {
                if (!zone.area.contains(tile) || crabs.any { it.basePosition == tile }) continue
                val nearby = crabs.count { it.basePosition.isWithinDistance(tile, CAMP_RADIUS) }
                val crowd = world.locator.findPlayers(tile, 2) { it != bot }.size
                scores[tile] = nearby - crowd * 2
            }
        }
        if (scores.isEmpty()) {
            bot.log("No rock crabs found in $zone.")
            return null
        }
        val best = scores.values.max()
        val picked = if (rand(bot.personality.intelligence)) {
            scores.filterValues { it >= best - 1 }.keys.random()
        } else {
            scores.keys.random()
        }
        spot = picked
        rocks = crabs.filter { it.basePosition.isWithinDistance(picked, CAMP_RADIUS) }
        bot.log("Picked rock crab spot $picked with ${rocks.size} rock(s) around it.")
        return picked
    }

    /**
     * Finds places around [spot] that are far enough away to reset the crabs, closest first after a shuffle.
     */
    private fun resetDestinations(spot: Position): List<Position> {
        val destinations = ArrayList<Position>()
        repeat(100) {
            val distance = rand(RESET_DISTANCE)
            val dx = rand(-distance, distance)
            val dy = if (rand().nextBoolean()) distance else -distance
            val tile = if (rand().nextBoolean()) spot.translate(dx, dy) else spot.translate(dy, dx)
            if (!world.collisionManager.isBlocked(tile, false) &&
                world.locator.findNpcs(tile, 2) { RockCrabs.isRockCrab(it) }.isEmpty()) {
                destinations += tile
            }
        }
        return destinations.distinct().shuffled()
    }

    /**
     * Returns the open tiles next to [position].
     */
    private fun standingTiles(position: Position): List<Position> {
        val tiles = ArrayList<Position>(8)
        for (dx in -1..1) {
            for (dy in -1..1) {
                val tile = position.translate(dx, dy)
                if (tile != position && !world.collisionManager.isBlocked(tile, false)) {
                    tiles += tile
                }
            }
        }
        return tiles
    }

    /**
     * Stands around while looking after hitpoints and prayer, until something attacks the bot.
     */
    private suspend fun idle(min: Duration = 2.seconds, max: Duration = 5.seconds) {
        val end = System.currentTimeMillis() + rand(min.inWholeMilliseconds.toInt(), max.inWholeMilliseconds.toInt())
        while (System.currentTimeMillis() < end && isActionSafe() && !bot.combat.inCombat() &&
            rocks.none { it.combat.target == bot }) {
            if (!tactics.watch()) {
                forceBanking = true
                return
            }
        }
    }

    /**
     * Rolls how long the bot waits next to a sleeping rock before it realizes the crabs have stopped waking. Careless
     * bots take minutes.
     */
    private fun rollNoticeAfter(): Long {
        val careless = 1.0 - bot.personality.intelligence
        return (rand(8, 15).seconds + rand(30, 180).seconds * careless).inWholeMilliseconds
    }
}
