package api.bot.script

import api.attr.Attr
import api.bot.zone.SubZone
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.memory.BotPeopleMemory
import io.luna.game.model.mob.bot.memory.BotPlacesMemory
import kotlin.time.Duration.Companion.minutes

/**
 * The last danger a bot left or tried to leave. Zoned scripts and the skilling factories read it so the work the bot
 * goes back to doesn't take it straight back to the same attacker.
 *
 * @property position Where the bot was when it encountered the danger.
 * @property attacker The attacker, for the bot's log.
 * @property time When the escape was recorded, in milliseconds.
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
        if (zone.area.plane != -1 && zone.area.plane != position.z) return false
        val southWest = zone.area.southWest
        val northEast = zone.area.northEast
        val dx = maxOf(southWest.x - position.x, 0, position.x - northEast.x)
        val dy = maxOf(southWest.y - position.y, 0, position.y - northEast.y)
        return maxOf(dx, dy) <= AVOID_RADIUS
    }

    /**
     * Whether a destination is in the escaped zone or within [AVOID_RADIUS] tiles of the witnessed danger.
     */
    fun avoids(destination: Position, now: Long = System.currentTimeMillis()): Boolean = isRecent(now) &&
        destination.z == position.z &&
        (zone?.area?.contains(destination) == true || destination.isWithinDistance(position, AVOID_RADIUS))

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

/**
 * Records that the bot got away from [attacker] near [position]. It keeps away from there for [BotEscape.AVOID_FOR],
 * remembers [zone] as more dangerous for days, and holds a small grudge against a player who drove it off. Another
 * escape from the same attacker and zone while the last is recent is the same escape and only renews it.
 */
fun Bot.escapedFrom(attacker: Mob, position: Position, zone: SubZone?) {
    if (!continuesEscape(attacker.toString(), zone)) {
        zone?.let { rememberPlace(it, BotPlacesMemory.Event.FLED) }
        if (attacker is Player) memory.people.remember(attacker.username, BotPeopleMemory.Event.CHASED_ME)
    }
    lastEscape = BotEscape(position, attacker.toString(), System.currentTimeMillis(), zone)
}

/**
 * Records that the bot left after seeing [attacker] attack someone near [position]. It keeps away from there for
 * [BotEscape.AVOID_FOR] and remembers [zone] as a little more dangerous. The attacker didn't attack the bot, so
 * nothing is held against them.
 */
fun Bot.leftWitnessedAttack(attacker: String, position: Position, zone: SubZone?) {
    if (!continuesEscape(attacker, zone)) zone?.let { rememberPlace(it, BotPlacesMemory.Event.WITNESSED_ATTACK) }
    lastEscape = BotEscape(position, attacker, System.currentTimeMillis(), zone)
}

/**
 * Whether an escape from [attacker] in [zone] is the recent one going on.
 */
private fun Bot.continuesEscape(attacker: String, zone: SubZone?): Boolean =
    recentEscape?.let { it.attacker == attacker && it.zone == zone } == true
