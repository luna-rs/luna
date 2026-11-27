package api.bot.script

import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.memory.BotPlacesMemory.Event
import kotlin.math.pow

/**
 * Whether the bot stays away from [zone] because it died or nearly died there.
 */
fun Bot.findsDangerous(zone: SubZone): Boolean = memory.places.dangerous(zone.name)

/**
 * Whether the bot gave up on [zone] for lack of room recently enough to skip it while it has somewhere else to go.
 */
fun Bot.findsPacked(zone: SubZone): Boolean = memory.places.packed(zone.name)

/**
 * The zone of the bot's zoned work that something at [position] happened in, or `null` if it happened elsewhere.
 */
fun Bot.workZoneAt(position: Position): SubZone? =
    scriptStack.findActive(ZonedBotScript::class.java).orElse(null)?.zoneOf(position)

/**
 * Records [event] at [zone].
 *
 * @return Whether the bot now stays away from [zone].
 */
fun Bot.rememberPlace(zone: SubZone, event: Event): Boolean = memory.places.remember(zone.name, event)

/**
 * Puts [options] in a random order that favours the places the bot leans toward, so a place it leans toward twice as
 * strongly is twice as likely to come first.
 */
fun <T> Bot.byRememberedPlaces(options: Collection<T>, zone: (T) -> SubZone): List<T> =
    options.map { it to rand().nextDouble().pow(1.0 / memory.places.preference(zone(it).name)) }
        .sortedByDescending { it.second }
        .map { it.first }
