package game.bot.scripts

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.script.ReflexBotScript
import api.bot.webwalk.walk.WebWalker
import api.bot.zone.SubZone
import api.bot.zone.Zone
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import java.util.EnumSet

/**
 * A [ReflexBotScript] that returns a bot to its death location.
 *
 * Captures the death position and zone context, then waits for the bot to respawn before attempting an exact
 * webwalk. If that fails, the captured subzones and zone provide alternative travel routes.
 *
 * This script only handles recovery travel. Once the bot returns, [LootItemReflexScript] can recover nearby items.
 *
 * @param bot The bot running this script.
 * @author lare96
 */
class DeathReflexScript(bot: Bot) : ReflexBotScript(bot) {

    /**
     * The death position, zone, and a snapshot of the subzones occupied when death was detected.
     *
     * The subzones are copied because the bot's active set changes as it moves. Zone travel provides fallback
     * routes when the web-walker cannot reach the death position directly.
     */
    private var deathLocation: Triple<Position, Zone?, EnumSet<SubZone>>? = null

    override fun shouldReact(): Boolean {
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
}