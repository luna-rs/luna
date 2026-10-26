package game.bot.scripts

import api.attr.Attr
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.script.ReflexBotScript
import api.bot.webwalk.walk.WebWalker
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.combat.death.DeathHookHandler.deathItems
import api.predef.*
import engine.controllers.WildernessLocatableController.levelAt
import game.content.duelArena.DuelController
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import java.util.EnumSet
import kotlin.time.Duration.Companion.minutes

/**
 * A [ReflexBotScript] that returns a bot to its death location.
 *
 * Captures the death position and zone context, then waits for the bot to respawn and decides whether to go back
 * ([decide]). A bot that goes back attempts an exact webwalk. If that fails, the captured subzones and zone provide
 * alternative travel routes.
 *
 * This script only handles recovery travel. Once the bot returns, [LootItemReflexScript] can recover nearby items.
 * Safe Duel Arena deaths remain with their controller and do not create a recovery trip.
 *
 * @param bot The bot running this script.
 * @author lare96
 */
class DeathReflexScript(bot: Bot) : ReflexBotScript(bot) {

    companion object {

        /**
         * The mob that last killed this bot, or `null` if nothing did. The player death hook sets it, and the bot
         * reads it once it has respawned.
         */
        var Bot.lastKiller by Attr.nullableObj(Mob::class)

        /**
         * How long an earlier death near the same spot counts against going back.
         */
        private val REPEAT_DEATH_WINDOW = 10.minutes

        /**
         * How near an earlier death, or a dropped item, has to be to the death position to count as the same spot.
         */
        private const val SAME_SPOT_RADIUS = Position.VIEWING_DISTANCE

        /**
         * How many ticks a dropped pile stays private before it turns public, as the ground item list counts them.
         */
        private const val PRIVATE_PILE_TICKS = 100

        /**
         * How many ticks a public pile stays on the ground, as the ground item list counts them.
         */
        private const val PUBLIC_PILE_TICKS = 300

        /**
         * How many ticks the bot takes to cover a tile. It walks one a tick and may run, but its route is longer than a
         * straight line.
         */
        private const val TICKS_PER_TILE = 1

        /**
         * How far north of the surface the dungeons sit.
         */
        private const val DUNGEON_OFFSET = 6400
    }

    /**
     * Where and when (in milliseconds) the bot died within the last [REPEAT_DEATH_WINDOW], oldest first.
     */
    private val recentDeaths = ArrayDeque<Pair<Position, Long>>()

    /**
     * The death position, zone, and a snapshot of the subzones occupied when death was detected.
     *
     * The subzones are copied because the bot's active set changes as it moves. Zone travel provides fallback
     * routes when the web-walker cannot reach the death position directly.
     */
    private var deathLocation: Triple<Position, Zone?, EnumSet<SubZone>>? = null

    override fun shouldReact(): Boolean {
        if (bot.controllers.primary is DuelController) return false
        if (bot.health < 1) {
            deathLocation = Triple(bot.position, bot.zone, EnumSet.copyOf(bot.subZones))
            return true
        }
        return false
    }

    override suspend fun run(): Boolean {
        // Wait until the death process has finished.
        if (bot.health < 1) {
            bot.log("Waiting for death process to finish.")
            bot.naturalDelay()
            return false
        }

        val location = deathLocation
        if (location == null) {
            bot.log("No valid death location could be found.")
            return true
        }
        val (position, zone, subZones) = location
        deathLocation = null

        val killer = bot.lastKiller
        bot.lastKiller = null
        val (goesBack, reason) = decide(position, killer)
        if (!goesBack) {
            bot.log("Not going back to $position: $reason.")
            return true
        }
        bot.log("Going back to $position: $reason.")

        // Try the exact position before using zone travel fallbacks.
        bot.log("Attempting webwalk to exact position $position.")
        if (WebWalker.webWalk(bot, position).success) {
            return true
        }

        for (candidate in subZones) {
            bot.naturalDecisionDelay()
            bot.log("Attempting travel to subzone candidate $candidate.")
            if (handler.travelTo(candidate) && WebWalker.webWalk(bot, position).success) {
                return true
            }
        }

        if (zone != null) {
            bot.naturalDecisionDelay()
            bot.log("Attempting travel to last zone candidate $zone.")
            if (handler.travelTo(zone) && WebWalker.webWalk(bot, position).success) {
                return true
            }
        }

        bot.log("Could not reach the exact death position. Recovery travel will now end.")
        return true
    }

    /**
     * Decides, once after respawning, whether the bot goes back to where it died.
     *
     * No bot goes back when the items it dropped there are gone, are worth less than its loot threshold, or would be
     * gone before it arrives. No bot goes back after an earlier death near there within [REPEAT_DEATH_WINDOW], except a
     * stupidly confident one after a single earlier death. Otherwise, in this order:
     * - A scared bot (one roll) never goes back to a threat or into the Wilderness.
     * - A greedy bot (one roll) goes back, even when its killer was a threat.
     * - A stupidly confident bot goes back, ignoring the threat.
     * - No other bot goes back to a threat.
     * - An intelligent bot doesn't go back deeper into the Wilderness than it would scavenge.
     * - Any other bot goes back.
     *
     * @param position Where the bot died.
     * @param killer The mob that killed the bot, or `null` if nothing did.
     * @return Whether the bot goes back, and why.
     */
    private fun decide(position: Position, killer: Mob?): Pair<Boolean, String> {
        val now = System.currentTimeMillis()
        while (recentDeaths.isNotEmpty() &&
            now - recentDeaths.first().second > REPEAT_DEATH_WINDOW.inWholeMilliseconds) {
            recentDeaths.removeFirst()
        }
        val earlierDeaths = recentDeaths.count { it.first.isWithinDistance(position, SAME_SPOT_RADIUS) }
        recentDeaths.addLast(position to now)

        val pile = bot.deathItems.filter {
            it.state == EntityState.ACTIVE && it.isWithinDistance(position, SAME_SPOT_RADIUS)
        }
        if (pile.isEmpty()) {
            return false to "nothing I dropped is left there"
        }
        val value = pile.sumOf { world.economy.getTotalPrice(it.toItem()) }
        val threshold = LootItemReflexScript.calculateMinLootValue(bot)
        if (value < threshold) {
            return false to "what I dropped is worth $value, under the $threshold I bother with"
        }
        val ticksAway = surface(bot.position).computeLongestDistance(surface(position)) * TICKS_PER_TILE
        val ticksLeft = pile.maxOf { ticksLeft(it) }
        if (ticksAway >= ticksLeft) {
            return false to "it is $ticksAway ticks away, and what I dropped is gone in $ticksLeft"
        }
        val personality = bot.personality
        if (earlierDeaths > 1 || (earlierDeaths == 1 && !personality.isStupidlyConfident)) {
            return false to "I died near there ${earlierDeaths + 1} times in $REPEAT_DEATH_WINDOW"
        }

        val threat = killer != null && handler.combat.isThreat(killer)
        val wildernessLevel = levelAt(position)
        val scared = bot.emotions.isFeeling(EmotionType.SCARED)
        val greedy = bot.emotions.isFeeling(EmotionType.GREEDY)
        return when {
            scared && threat -> false to "too scared of $killer"
            scared && wildernessLevel > 0 -> false to "too scared of level $wildernessLevel Wilderness"
            greedy -> true to "greedy for what I dropped, worth $value"
            personality.isStupidlyConfident -> true to "not scared, and what I dropped is worth $value"
            threat -> false to "$killer is too strong for me"
            personality.isIntelligent && wildernessLevel >
                    WildernessScavengeBotScript.maximumDepth(personality.confidence, personality.intelligence) ->
                false to "level $wildernessLevel Wilderness is deeper than I go"
            else -> true to "what I dropped is worth $value"
        }
    }

    /**
     * Works out how many more ticks [item] stays on the ground: the rest of [PRIVATE_PILE_TICKS] while it is private,
     * then [PUBLIC_PILE_TICKS] once it is public.
     *
     * @param item The dropped item.
     * @return The ticks left, or [Int.MAX_VALUE] if it never goes.
     */
    private fun ticksLeft(item: GroundItem): Int = when {
        !item.isExpiring -> Int.MAX_VALUE
        item.isLocal -> PRIVATE_PILE_TICKS + PUBLIC_PILE_TICKS - item.expireTicks
        else -> PUBLIC_PILE_TICKS - item.expireTicks
    }

    /**
     * Moves [position] to the surface, so a straight line to a dungeon doesn't count the [DUNGEON_OFFSET] tiles
     * between it and the surface above it. The plane is ignored.
     *
     * @param position The position to move.
     * @return The position on the surface.
     */
    private fun surface(position: Position): Position =
        Position(position.x, if (position.y > DUNGEON_OFFSET) position.y - DUNGEON_OFFSET else position.y)
}
