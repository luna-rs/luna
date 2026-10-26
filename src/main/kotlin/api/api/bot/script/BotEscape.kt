package api.bot.script

import api.attr.Attr
import api.bot.zone.SubZone
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.minutes

/**
 * The last time a bot teleported home to get away from an attacker. Zoned scripts and the skilling factories read it
 * so the work the bot goes back to doesn't take it straight back to the same attacker.
 *
 * @property position Where the bot was when the attacker went for it.
 * @property attacker The attacker, for the bot's log.
 * @property time When the bot teleported, in milliseconds.
 * @property zone The zone the bot was working in or travelling to when it was attacked, if any.
 * @author lare96
 */
class BotEscape(val position: Position, val attacker: String, val time: Long, val zone: SubZone?) {

    companion object {

        /**
         * How long a bot keeps away from where it escaped.
         */
        val AVOID_FOR = 10.minutes

        /**
         * How close, in tiles, a zone's area may come to where the bot was attacked before the bot keeps away from it.
         */
        const val AVOID_RADIUS = 8
    }

    /**
     * Whether this escape is recent enough that the bot still keeps away from it.
     */
    fun isRecent(now: Long = System.currentTimeMillis()) = now - time < AVOID_FOR.inWholeMilliseconds

    /**
     * Whether the bot keeps away from [zone]: the escape is recent, and [zone] is the zone the bot was chased out of
     * or its area comes within [AVOID_RADIUS] tiles of where the bot was attacked.
     */
    fun avoids(zone: SubZone, now: Long = System.currentTimeMillis()): Boolean {
        if (!isRecent(now)) return false
        if (zone == this.zone) return true
        if (zone.plane != null && zone.plane != position.z) return false
        val southWest = zone.area.southWest
        val northEast = zone.area.northEast
        val dx = maxOf(southWest.x - position.x, 0, position.x - northEast.x)
        val dy = maxOf(southWest.y - position.y, 0, position.y - northEast.y)
        return maxOf(dx, dy) <= AVOID_RADIUS
    }

    override fun toString() = "BotEscape(attacker=$attacker, position=$position, zone=$zone)"
}

/**
 * The bot's last escape from an attacker, or `null` if it hasn't had one.
 */
var Bot.lastEscape by Attr.nullableObj(BotEscape::class)

/**
 * The bot's last escape from an attacker if it is recent enough to keep away from, otherwise `null`.
 */
val Bot.recentEscape: BotEscape? get() = lastEscape?.takeIf { it.isRecent() }
