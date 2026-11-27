package api.bot.script

import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.memory.BotPlacesMemory.Event
import io.luna.game.model.mob.bot.memory.BotPlacesMemory.Resource
import kotlin.math.pow

/**
 * Whether the bot stays away from [zone] because it died or nearly died there.
 */
fun Bot.findsDangerous(zone: SubZone): Boolean = memory.places.dangerous(zone.name)

/**
 * Whether others took the [resource] at [zone] so recently that the bot skips it while it has somewhere else to go.
 * Always `false` for work no one else can use up (a `null` resource).
 */
fun Bot.findsPacked(zone: SubZone, resource: Resource?): Boolean = memory.places.packed(zone.name, resource)

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
 * Records that the bot gave up on [zone] because others had taken the [resource] it came for.
 */
fun Bot.rememberCrowding(zone: SubZone, resource: Resource) = memory.places.crowded(zone.name, resource)

/**
 * Puts [options] in a random order that favours the places the bot leans toward for work at [resource], so a place it
 * leans toward twice as strongly is twice as likely to come first.
 */
fun <T> Bot.byRememberedPlaces(options: Collection<T>, resource: Resource? = null, zone: (T) -> SubZone): List<T> =
    options.map { it to rand().nextDouble().pow(1.0 / memory.places.preference(zone(it).name, resource)) }
        .sortedByDescending { it.second }
        .map { it.first }
