package game.bot.scripts.skills

import io.luna.game.model.mob.bot.Bot
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotGearSet
import game.bot.scripts.combat.CombatBotScript.InitialState
import game.skill.thieving.stealFromStall.StealFromAction
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A thieving bot script that steals from configured stall types inside selected subzones.
 *
 * This script uses the shared skilling/zoned-script lifecycle for zone selection, banking, duration tracking, and
 * target searching. It converts the configured [stalls] into the full-stall object ids that can be clicked, then searches
 * nearby objects for matching stalls during each skilling cycle.
 *
 * The script also adds simple survival behaviour. When the bot becomes nervous about its hitpoints, it attempts to eat
 * any food in its inventory. If no food is available, it forces a banking trip so more food can be withdrawn.
 *
 * A stall's owner or a guard who sees a theft stops it, and a guard attacks the thief. Careful bots only steal from a
 * tile beside the stall that none of them can see, and try another stall when every tile is watched. Smart bots are
 * always careful, a bot that was just caught is careful for a while, and other bots are as often as their intelligence
 * allows. Someone who turns or walks into view during the theft still catches a careful bot now and then.
 *
 * This script answers attackers itself rather than leaving them to the bot's default combat response. A caught bot
 * either fights back or runs until the guard loses sight of it and gives up, then goes back to stealing. A bot that is
 * too tired to outrun the guard, or can't shake it off, turns and fights if it can. A bot caught too often in a short
 * time gives up on the stalls and moves on to something else.
 *
 * @param bot The bot running this script.
 * @param stalls The stall types this script may steal from.
 * @param duration The remaining amount of time this script should run before completing normally.
 * @param zones The candidate subzones this script may operate in.
 * @author lare96
 */
class StealBotScript(
    bot: Bot,
    val stalls: Set<ThievingStallType>,
    duration: Duration,
    zones: MutableList<SubZone>
) : SkillingBotScript<GameObject>(bot, duration, zones, bot.thieving) {

    // Move on if stalls remain unavailable or unsafe, rather than waiting until the entire script expires.
    override val targetSearchTimeout: Duration = 180.seconds

    // Keep running in combat, so the fights started by guards are answered here (see beforeZoneWork).
    override val actionSafety = ActionSafety.COMBAT

    companion object {

        /**
         * How many times a bot may be caught within [CAUGHT_WINDOW] before it gives up on the stalls.
         */
        private const val CAUGHT_LIMIT = 3

        /**
         * The time window for [CAUGHT_LIMIT].
         */
        private val CAUGHT_WINDOW = 10.minutes

        /**
         * How long a bot that was caught takes care with every theft afterwards.
         */
        private val WARY_TIME = 5.minutes

        /**
         * How long a fight may go on without the bot landing an attack before it runs instead, for an attacker it
         * can't reach.
         */
        private val STALLED_FIGHT = 12.seconds

        /**
         * The run energy a bot needs to outrun an attacker. Without it the bot moves no faster than the attacker.
         */
        private const val ESCAPE_ENERGY = 10.0

        /**
         * How many places a bot runs to before it stops trying to lose an attacker that keeps up with it.
         */
        private const val ESCAPE_LEGS = 8

        /**
         * How far from its attacker, in tiles, a running bot tries to get. An NPC stops chasing a target once it's
         * further away than the viewing distance.
         */
        private const val ESCAPE_DISTANCE = Position.VIEWING_DISTANCE + 3

        /**
         * How far, in tiles, a running bot looks for a place to run to.
         */
        private const val ESCAPE_SEARCH_RADIUS = 32

        /**
         * Serializable script data for [StealBotScript].
         *
         * This extends [ZonedBotScriptData] with the stall types selected for this thieving script. The inherited data
         * stores the remaining duration and candidate zones, while [stalls] stores the script-specific target types.
         */
        class StealData : ZonedBotScriptData() {

            /**
             * The stall types this script should restore when resumed or loaded.
             */
            var stalls = emptySet<ThievingStallType>()

            /**
             * Loads the saved zoned-script state and selected thieving stall types.
             *
             * @param data The JSON object containing this script's saved state.
             */
            override fun load(data: JsonObject) {
                super.load(data)
                stalls = loadEnumSet("stalls", data) { ThievingStallType.valueOf(it) }
            }

            /**
             * Saves the current zoned-script state and selected thieving stall types.
             *
             * @param data The JSON object to write this script's state into.
             */
            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("stalls", data, stalls)
            }
        }
    }

    /**
     * Creates a stealing script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved stealing script data to restore from.
     */
    constructor(bot: Bot, data: StealData) : this(bot, data.stalls, data.duration, data.zones)

    /**
     * The full-stall object ids this script can steal from.
     *
     * Each [ThievingStallType] can define multiple stall object states. Only the full object id is searched for because
     * empty/depleted stalls should not be clicked.
     */
    private val stallIds = run {
        val ids = HashSet<Int>()

        for (type in stalls) {
            for ((full, _) in type.stalls) {
                ids.add(full)
            }
        }

        ids
    }

    /**
     * The mob the bot is fighting or running from, or `null` if nothing is after it.
     */
    private var foe: Mob? = null

    /**
     * Whether the bot chose to fight [foe] rather than run from it.
     */
    private var fighting = false

    /**
     * Whether the bot gave up fighting [foe], after which it only runs.
     */
    private var fightFailed = false

    /**
     * When the bot started fighting [foe], in milliseconds.
     */
    private var fightStart = 0L

    /**
     * How many places the bot has run to while running from [foe].
     */
    private var escapeLegs = 0

    /**
     * Where the bot is running to, or `null` if it isn't running.
     */
    private var hideout: Position? = null

    /**
     * When the bot was caught, in milliseconds, oldest first.
     */
    private val caughtTimes = ArrayDeque<Long>()

    /**
     * Until when, in milliseconds, a bot that was just caught takes care with every theft.
     */
    private var waryUntil = 0L

    override suspend fun onExecuteSkilling(searching: Boolean) {
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            bot.log("No food in inventory, banking for more.")
            forceBanking = true
        }
    }
    override suspend fun equipment(): BotGearLocator? {
        return BotGearSelector.find(bot, BotGearSet.ROGUE).fillAll(setOf(BotGearPurpose.SKILLING)).buildLocator()
    }
    override suspend fun onBankOpenSkilling(initial: Boolean) {
        val remainingSpace = bot.inventory.computeRemainingSize()
        if (!handler.banking.withdrawAnyFood((remainingSpace * 0.15).toInt())) {
            bot.log("No food left, ending script.")
            stop()
        }
    }

    /**
     * Finds nearby full stalls. Whether a stall is watched is checked tile by tile, as the bot picks where to stand.
     *
     * @param searchBase The position to search around.
     * @param searchRadius The maximum object-search radius.
     *
     * @return The nearby full-stall objects this script can attempt to steal from.
     */
    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius, true) { it.id in stallIds }
    }

    /**
     * Skips a stall when the bot is in a fight, or when it takes care and every tile beside the stall is watched.
     * Otherwise a careful bot first moves out of sight.
     *
     * @param newFocus The stall about to be stolen from.
     * @return `true` if the bot may steal from [newFocus].
     */
    override suspend fun onAssignFocus(newFocus: GameObject): Boolean =
        !bot.combat.inCombat() && (!isCareful() || standUnseen(newFocus))

    /**
     * Checks the focused stall before it is clicked again. A theft that was seen leaves the stall full, so without
     * this a careful bot would keep stealing from the same tile while someone watches.
     *
     * @return `true` if the bot should look for another stall.
     */
    override suspend fun refocus(): Boolean {
        val stall = focus
        if (stall != null && stall.state == EntityState.ACTIVE) {
            if (bot.isLocked) {
                // A click queued during a theft can start the next one as soon as it ends, without the check below.
                return false
            }
            if (bot.actions.size(ActionType.WEAK) == 0 && !bot.combat.inCombat() && isCareful() &&
                !standUnseen(stall)) {
                bot.log("Someone can see me steal from the stall at ${stall.position}, looking for another stall.")
                return true
            }
        }
        return super.refocus()
    }

    /**
     * Doesn't click a stall during a fight, since stalls can't be stolen from in combat. A fight that starts on the way
     * to the stall ends the walk there, not this script.
     */
    override suspend fun interactWithTarget(option: Int, target: GameObject): Boolean {
        if (bot.combat.inCombat()) {
            return false
        }
        return try {
            super.interactWithTarget(option, target)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            false
        }
    }

    override suspend fun interactionOption(target: GameObject): Int = 2

    override fun levelRequired(): Int = stalls.maxOfOrNull { it.level } ?: 0

    /**
     * Answers attackers before any stall work. The bot's default combat response is off while this script runs, so the
     * guard that catches a thief is fought or outrun here, and stealing carries on once nothing is after the bot.
     *
     * @return `true` if this cycle was spent on a fight.
     */
    override suspend fun beforeZoneWork(): Boolean {
        bot.reflex.isDisableCombatReflex = true
        val attacker = findAttacker()
        if (attacker != null) {
            respondTo(attacker)
            return true
        }
        if (bot.combat.inCombat()) {
            // Stalls can't be stolen from until the fight's timer runs out.
            bot.naturalDelay()
            return true
        }
        if (foe != null) {
            endEncounter()
        }
        return isTerminated()
    }

    /**
     * Hands fights back to the default combat response once this script ends, or once another script takes over the
     * bot. A reflex that pauses this script, like eating or looting, hands the bot straight back to it, so its fight
     * stays here.
     */
    override suspend fun finish() {
        if (!isPaused()) {
            bot.reflex.isDisableCombatReflex = false
            return
        }
        // Pausing runs this at once, before whatever paused this script is in place, so look again next tick.
        world.schedule(1) {
            when {
                isRunning() -> it.cancel()
                isPaused() && bot.state == EntityState.ACTIVE && bot.scriptStack.current() === this -> Unit
                else -> {
                    bot.reflex.isDisableCombatReflex = false
                    it.cancel()
                }
            }
        }
    }

    override fun snapshot(): BotScriptData {
        val data = StealData()
        data.stalls = stalls
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }

    /**
     * Finds the mob after the bot: the last mob it traded blows with while the fight's timer runs, or a mob still
     * chasing it.
     *
     * @return The attacker, or `null` if nothing is after the bot.
     */
    private fun findAttacker(): Mob? {
        val mob = bot.combat.lastCombatWith ?: foe ?: return null
        if (mob.state != EntityState.ACTIVE || !mob.isAlive) {
            return null
        }
        return if (bot.combat.inCombat() || mob.combat.target == bot) mob else null
    }

    /**
     * Fights or runs from [attacker] for one cycle, deciding which when it first attacks.
     *
     * @param attacker The mob after the bot.
     */
    private suspend fun respondTo(attacker: Mob) {
        if (attacker != foe) {
            foe = attacker
            fighting = false
            fightFailed = false
            escapeLegs = 0
            hideout = null
            noteCaught()
            if (canFight(attacker) && bot.emotions.getCombatResponse(attacker) == InitialState.ATTACK) {
                startFight("Fighting back against $attacker.")
            } else {
                bot.log("Running from $attacker.")
            }
        }
        if (!fighting && !fightFailed) {
            // An attacker that keeps up can't be outrun, so a bot that can fight turns and does.
            val stuck = when {
                bot.runEnergy < ESCAPE_ENERGY -> "Too tired to outrun"
                escapeLegs > ESCAPE_LEGS -> "Can't lose"
                else -> null
            }
            if (stuck != null && canFight(attacker)) {
                startFight("$stuck $attacker, fighting back.")
            }
        }
        if (fighting) {
            if (fight(attacker)) {
                return
            }
            fighting = false
            fightFailed = true
        }
        escape(attacker)
    }

    /**
     * Starts fighting [foe].
     *
     * @param reason The log line saying why.
     */
    private fun startFight(reason: String) {
        bot.log(reason)
        fighting = true
        fightStart = System.currentTimeMillis()
    }

    /**
     * Ends a fight once nothing is after the bot, and gives up on the stalls if the bot keeps getting caught.
     */
    private fun endEncounter() {
        bot.log("Nothing is after me anymore.")
        foe = null
        fighting = false
        fightFailed = false
        escapeLegs = 0
        hideout = null
        val since = System.currentTimeMillis() - CAUGHT_WINDOW.inWholeMilliseconds
        caughtTimes.removeAll { it < since }
        if (caughtTimes.size >= CAUGHT_LIMIT) {
            bot.log("Caught ${caughtTimes.size} times in $CAUGHT_WINDOW, trying something else.")
            stop()
        }
    }

    /**
     * Records that the bot was caught, which keeps it careful for [WARY_TIME].
     */
    private fun noteCaught() {
        val now = System.currentTimeMillis()
        caughtTimes.addLast(now)
        waryUntil = now + WARY_TIME.inWholeMilliseconds
    }

    /**
     * Determines if the bot can fight [attacker] at all. Skillers never fight, and neither does a bot whose weapon
     * would train a skill its build avoids. When a bot can, it decides the way it answers any attacker, by
     * personality: confidence makes it fight, and intelligence makes it run from something much stronger.
     *
     * @param attacker The mob that attacked the bot.
     * @return `true` if the bot can fight [attacker].
     */
    private fun canFight(attacker: Mob): Boolean =
        !bot.preferences.isSkiller && attacker is Npc && attacker.combat.isAttackable &&
            handler.combat.prepareTrainingStyle()

    /**
     * Fights [attacker] for one cycle. Gives up when the bot is low with nothing to eat, or when it hasn't landed an
     * attack in a while.
     *
     * @param attacker The mob to fight.
     * @return `false` if the bot should run instead.
     */
    private suspend fun fight(attacker: Mob): Boolean {
        if (bot.healthPercent < 30 && !handler.inventory.eatAnyFood()) {
            bot.log("Losing to $attacker with nothing to eat.")
            return false
        }
        val lastAttack = bot.combat.lastAttackSent?.timestamp?.toEpochMilli() ?: 0
        if (System.currentTimeMillis() - maxOf(lastAttack, fightStart) > STALLED_FIGHT.inWholeMilliseconds) {
            bot.log("My attacks aren't landing on $attacker.")
            return false
        }
        if (bot.combat.target != attacker) {
            bot.combat.attack(attacker)
        }
        bot.naturalDelay()
        return true
    }

    /**
     * Runs from [attacker] for one cycle, to a tile far enough away that it gives up the chase, and waits there until
     * it does. A cornered bot fights if it can; one that can't, or is nearly dead, uses the default escape, which
     * teleports it home when its health is low.
     *
     * @param attacker The mob to run from.
     */
    private suspend fun escape(attacker: Mob) {
        if (bot.combat.target != null) {
            // Auto retaliate turns the bot back toward whatever hits it.
            bot.combat.target = null
            bot.navigator.cancel()
        }
        if (bot.healthPercent < 15) {
            handler.combat.fleeCombat()
            bot.naturalDelay()
            return
        }
        if (attacker.combat.target != bot || !attacker.isViewableFrom(bot)) {
            // It lost sight of the bot. Stay away until it gives up and the fight's timer runs out.
            bot.naturalDelay()
            return
        }
        var tile = hideout
        if (tile == null || bot.position == tile || attacker.isWithinDistance(tile, Position.VIEWING_DISTANCE)) {
            tile = findHideout(attacker)
            if (tile == null) {
                if (!fightFailed && canFight(attacker)) {
                    startFight("Cornered by $attacker, fighting back.")
                    return
                }
                bot.log("Nowhere to run from $attacker.")
                handler.combat.fleeCombat()
                bot.naturalDelay()
                return
            }
            hideout = tile
            escapeLegs++
            bot.log("Running from $attacker to $tile.")
        }
        if (bot.navigator.currentTarget != tile) {
            bot.walking.isRunning = bot.runEnergy > 0
            bot.navigator.navigate(tile, true)
        }
        bot.naturalMicroDelay()
    }

    /**
     * Finds the nearest tile the bot can walk to that is at least [ESCAPE_DISTANCE] tiles from [attacker].
     *
     * @param attacker The mob the bot runs from.
     * @return The tile, or `null` if there is none within [ESCAPE_SEARCH_RADIUS] tiles.
     */
    private fun findHideout(attacker: Mob): Position? {
        val start = bot.position
        val seen = hashSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val tile = queue.removeFirst()
            if (!attacker.isWithinDistance(tile, ESCAPE_DISTANCE - 1)) {
                return tile
            }
            for (direction in Direction.ALL_EXCEPT_NONE) {
                val next = tile.translate(1, direction)
                // Never past the attacker: it would strike as the bot went by.
                if (next.isWithinDistance(start, ESCAPE_SEARCH_RADIUS) && next !in seen &&
                    !attacker.isWithinDistance(next, 1) &&
                    world.collisionManager.traversable(tile, EntityType.PLAYER, direction)) {
                    seen += next
                    queue.addLast(next)
                }
            }
        }
        return null
    }

    /**
     * Whether this theft is made carefully, out of everyone's sight. Smart bots always take care, and so does a bot
     * that was caught a short while ago; others take care as often as their intelligence allows.
     *
     * @return `true` if the bot hides for this theft.
     */
    private fun isCareful(): Boolean = bot.personality.isIntelligent || System.currentTimeMillis() < waryUntil ||
        rand(bot.personality.intelligence)

    /**
     * Makes sure nobody watching [stall] can see the bot steal from it. The bot stays on its tile if it can steal from
     * there unseen, and otherwise walks to the nearest tile beside the stall that nobody can see.
     *
     * @param stall The stall to steal from.
     * @return `true` if the bot can steal from [stall] unseen, or `false` if someone can see every tile.
     */
    private suspend fun standUnseen(stall: GameObject): Boolean {
        val type = ThievingStallType.FULL_STALLS[stall.id] ?: return true
        val tiles = standingTiles(stall)
        val position = bot.position
        if (tiles.isEmpty() || (position in tiles && !isWatched(position, type))) {
            return true
        }
        val tile = tiles.filterNot { isWatched(it, type) }.minByOrNull { it.computeLongestDistance(position) }
            ?: return false
        bot.log("Moving to $tile, out of sight, to steal from the stall at ${stall.position}.")
        try {
            bot.navigator.navigate(tile, true).await()
        } catch (e: CancellationException) {
            // A fight's pursuit replaces the walk; the fight is answered on the next cycle.
            currentCoroutineContext().ensureActive()
            return false
        }
        // Someone may have turned while the bot walked.
        return bot.position == tile && !isWatched(tile, type)
    }

    /**
     * Determines if an owner of a [type] stall or a guard can see [tile], the same way they see a theft.
     *
     * @param tile The tile to check.
     * @param type The stall's type.
     * @return `true` if someone can see [tile].
     */
    private fun isWatched(tile: Position, type: ThievingStallType): Boolean {
        return world.locator.findViewableNpcs(tile) {
            type.owners.contains(it.def().name) || StealFromAction.isGuard(it)
        }.any { StealFromAction.canSee(it, tile) }
    }

    /**
     * Returns the tiles beside [stall] that a bot can stand on and steal from.
     *
     * @param stall The stall.
     * @return The tiles, in no particular order.
     */
    private fun standingTiles(stall: GameObject): List<Position> {
        val view = world.collisionManager.view(false)
        val base = stall.position
        // A square of the longer side covers the stall in either rotation.
        val size = maxOf(stall.sizeX(), stall.sizeY())
        val tiles = ArrayList<Position>()
        for (x in base.x - 1..base.x + size) {
            for (y in base.y - 1..base.y + size) {
                val tile = Position(x, y, base.z)
                // Bots diagonal to the stall's base tile are moved a step before they steal, so they never steal there.
                val stepped = tile.isWithinDistance(base, 1) && Direction.between(tile, base).isDiagonal
                if (!stepped && view.get(x, y, base.z) and (CollisionFlag.LOC or CollisionFlag.FLOOR_BLOCKED) == 0 &&
                    world.collisionManager.reached(tile, stall, InteractionPolicy.STANDARD_SIZE)) {
                    tiles += tile
                }
            }
        }
        return tiles
    }
}
