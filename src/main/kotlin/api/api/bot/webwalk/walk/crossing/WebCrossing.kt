package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.generate.SpecialCrossings
import api.bot.webwalk.plan.BotConditions
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.bot.zone.SubZone
import game.obj.webs.Web
import io.luna.game.model.Position
import kotlin.time.Duration.Companion.seconds

/**
 * Crosses a web by slashing it until it opens and walking through, as a player does (see [Web]).
 *
 * The bot slashes with its weapon if that cuts, and otherwise uses the first blade in its inventory on the web, such as
 * a knife. Half of all tries cut, and a cut locks the bot until the web opens. A web that's already open, because
 * somebody else slashed it, is walked through at once.
 *
 * @author TheLining
 */
object WebCrossing : CrossingHandler {

    /**
     * The name that the data gives this handler.
     */
    const val ID = "web"

    /**
     * The number of the web's Slash option.
     */
    private const val SLASH_OPTION = 1

    /**
     * How many tries before giving up. At a 50% chance, all of them fail about one time in a million.
     */
    private const val ATTEMPTS = 20

    /**
     * How long to wait for a try to cut or miss.
     */
    private val ATTEMPT_WAIT = 2.seconds

    /**
     * How many times the web is slashed and walked through before giving up, in case it grows back on the way.
     */
    private const val PASSES = 3

    /**
     * The sub-zones with a web in them. A bot going to one of them should carry a blade, so that it can reach all of
     * the zone (see [expected]).
     */
    val zones: List<SubZone> by lazy {
        SubZone.entries.filter { zone -> SpecialCrossings.WEBS.placements.any { zone.area.contains(it.pos) } }
    }

    /**
     * Finds the conditions that a bot should meet when it arrives at [destination], beyond what the trip there needs.
     *
     * @return [BotConditions.SLASH_WEB] if [destination] is in one of the [zones], or nothing.
     */
    fun expected(destination: Position): Set<String> =
        if (zones.any { it.area.contains(destination) }) setOf(BotConditions.SLASH_WEB) else emptySet()

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val position = leg.action?.objectPosition ?: return "the leg has no web position"
        val bot = context.bot
        repeat(PASSES) {
            if (context.arrived(leg, 0)) {
                return null
            }
            context.walk(leg.from, 0)?.let { return it }

            var tries = 0
            while (true) {
                val web = context.find(position, Web.WEB) ?: break
                if (tries++ >= ATTEMPTS) {
                    return "could not slash the web at $position in $ATTEMPTS tries"
                }
                val (blade, wielded) = Web.blade(bot) ?: return "the bot carries nothing that cuts the web at $position"
                if (wielded) {
                    if (!context.handler.interactions.interactOnLeg(SLASH_OPTION, web)) {
                        return "could not reach the web at $position"
                    }
                } else {
                    bot.output.useItemOnObject(bot.inventory.computeIndexForId(blade), blade, web)
                }
                waitFor(ATTEMPT_WAIT) { bot.isLocked || context.find(position, Web.WEB) == null }
                if (!waitFor(LegContext.ACTION_TIMEOUT) { !bot.isLocked }) {
                    return "the bot is still busy slashing the web at $position"
                }
            }

            if (context.walk(leg.to, 0) == null && context.arrived(leg, 0)) {
                return null
            }
        }
        return "could not get through the web at $position after $PASSES passes"
    }
}
