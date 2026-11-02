package engine.item.developerItem

import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.webwalk.walk.WebWalker
import api.bot.zone.SubZone
import api.bot.zone.Zone
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import java.util.Locale

/**
 * Resolves developer destinations without changing player state. Named destinations use the same anchors
 * as botwebwalk; duplicate names must be qualified instead of silently choosing a registry.
 *
 * @author lare96
 */
internal object DeveloperNavigation {
    /**
     * A registry name and its established travel anchor.
     *
     * @author lare96
     */
    data class Destination(val kind: String, val name: String, val position: Position)

    /**
     * A resolution result suitable for the existing input overlay.
     *
     * @author lare96
     */
    data class Resolution(val position: Position? = null, val message: String = "", val matches: List<Destination> = emptyList())

    val destinations: List<Destination> by lazy {
        Zone.entries.map { Destination("zone", it.name, it.anchor) } +
            SubZone.entries.map { Destination("subzone", it.name, it.inside) }
    }

    private fun key(value: String) = value.trim().uppercase(Locale.ROOT).replace(Regex("[ _-]+"), "_")

    fun resolve(input: String, omittedHeight: Int, registry: List<Destination> = destinations): Resolution {
        val value = input.replace('_', ' ').trim()
        if (value.isEmpty()) return Resolution(message = "Enter x y [height], a zone name, or a subzone name.")
        if (',' in value || value.first().isDigit() || value.first() in "+-") {
            val parts = if (',' in value) value.split(',').map(String::trim) else value.split(Regex("\\s+"))
            val numbers = parts.map { it.toIntOrNull() }
            if (parts.size !in 2..3 || numbers.any { it == null }) {
                return Resolution(message = "Use x y or x y height with whole numbers.")
            }
            val x = numbers[0]!!
            val y = numbers[1]!!
            val height = numbers.getOrNull(2) ?: omittedHeight
            // Packed world positions use fourteen bits per tile coordinate and two bits for the plane.
            if (x !in 0..16383 || y !in 0..16383 || !Position.HEIGHT_LEVELS.contains(height)) {
                return Resolution(message = "Coordinates require x/y 0..16383 and height 0..3.")
            }
            return Resolution(Position(x, y, height))
        }
        val qualifier = value.substringBefore(':', "").lowercase(Locale.ROOT)
        if (qualifier.isNotEmpty() && qualifier !in listOf("zone", "subzone")) {
            return Resolution(message = "Qualify a name with zone: or subzone:.")
        }
        val name = key(value.substringAfter(':', value))
        val eligible = registry.filter { qualifier.isEmpty() || it.kind == qualifier }
        val exact = eligible.filter { key(it.name) == name }
        val matches = exact.ifEmpty { eligible.filter { key(it.name).startsWith(name) } }
        return when (matches.size) {
            0 -> Resolution(message = "Unknown destination '$value'. Enter a zone or subzone name prefix.")
            1 -> Resolution(matches.single().position)
            else -> Resolution(message = "Ambiguous name. Choose a destination.", matches = matches)
        }
    }

    fun walkScript(bot: Bot, destination: Position): BotScript = object : DynamicBotScript(bot) {
        override suspend fun run(): Boolean {
            val result = WebWalker.webWalk(bot, destination)
            bot.sendMessage("${if (result.success) "Navigation" else "Navigation failed"}: ${result.message}")
            return true
        }
    }
}
