package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import com.google.gson.JsonElement
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.game.model.mob.bot.speech.BotSpeechStack

/**
 * Loads runner-first dialogue for a proposed or completed native exchange.
 * Both speakers avoid their latest selection for each context.
 *
 * @author lare96
 */
object BotRuneRunningSpeechPool : BotSpeechPool<BotRuneRunningSpeechPool.Event>(
    "rune_running.jsonc", Event::class.java
) {

    enum class Event {
        INTRODUCTION, DELIVERY, THANKS, INTERRUPTED
    }

    private val Bot.lastSelections by Attr.obj { hashMapOf<Event, List<String>>() }
    private var exchanges = emptyMap<Event, List<List<String>>>()

    override fun read(data: JsonElement) {
        val root = data.asJsonObject
        require(root.keySet() == Event.entries.map { it.name }.toSet()) { "Incomplete rune-running speech contexts." }
        exchanges = Event.entries.associateWith { event ->
            root.getAsJsonArray(event.name).map { exchange ->
                exchange.asJsonArray.map { line ->
                    require(line.isJsonPrimitive && line.asJsonPrimitive.isString) { "Invalid rune-running speech line." }
                    line.asString.also(::validate)
                }.also { lines ->
                    require(lines.size == 2) { "Rune-running dialogue must have two turns." }
                }
            }.distinct().also { require(it.isNotEmpty()) { "Empty rune-running speech context: $event" } }
        }
    }

    fun select(runner: Bot, crafter: Bot, event: Event, altar: Altar, fee: Int): List<String>? {
        if (fee <= 0) return null
        val choices = exchanges[event].orEmpty()
        val selected = choices.filter { it != runner.lastSelections[event] && it != crafter.lastSelections[event] }
            .ifEmpty { choices }.randomOrNull() ?: return null
        runner.lastSelections[event] = selected
        crafter.lastSelections[event] = selected
        return selected.map { BotSpeechStack.normalizeChat(expand(it, altar.name.lowercase(), fee)) }
    }

    private fun validate(template: String) {
        val plain = template.replace("{rune}", "").replace("{fee}", "")
        require('{' !in plain && '}' !in plain) { "Unknown rune-running speech token." }
        val longest = expand(template, "cosmic", Int.MAX_VALUE)
        require(longest.length <= 80 && BotSpeechStack.normalizeChat(longest).isNotBlank()) {
            "Rune-running speech exceeds the native chat limit."
        }
    }

    private fun expand(template: String, rune: String, fee: Int): String = template
        .replace("{rune}", rune)
        .replace("{fee}", fee.toString())
}
