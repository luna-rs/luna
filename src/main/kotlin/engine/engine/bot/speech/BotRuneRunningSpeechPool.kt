package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import com.google.gson.JsonElement
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.game.model.mob.bot.speech.BotSpeechStack

/**
 * Loads coherent runner-first exchanges for real rune-running work and its observed outcomes.
 * Both speakers avoid their latest selection for each context, independently of other conversation pools.
 *
 * @author lare96
 */
object BotRuneRunningSpeechPool : BotSpeechPool<BotRuneRunningSpeechPool.Event>(
    "rune_running.jsonc", Event::class.java
) {

    /**
     * Separates proposed terms, starter funding and confirmed outcomes so speech cannot imply an unpaid reward.
     *
     * @author lare96
     */
    enum class Event {
        INTRODUCTION, STARTER, DELIVERY, STARTER_RECEIVED, THANKS, INTERRUPTED, HOST_FINISHED, FAREWELL
    }

    /**
     * Runtime-only history belongs to each participant and survives changes between running scripts.
     */
    private val Bot.lastSelections by Attr.obj { hashMapOf<Event, List<String>>() }

    /**
     * Authored templates retain their tokens until the actual contract supplies its rune output and fare.
     */
    private var exchanges = emptyMap<Event, List<List<String>>>()

    override fun read(data: JsonElement) {
        val root = data.asJsonObject
        require(root.keySet() == Event.entries.map { it.name }.toSet()) { "Incomplete rune-running speech contexts." }
        val loaded = Event.entries.associateWith { event ->
            root.getAsJsonArray(event.name).map { exchange ->
                exchange.asJsonArray.map { line ->
                    require(line.isJsonPrimitive && line.asJsonPrimitive.isString) { "Invalid rune-running speech line." }
                    line.asString.also(::validate)
                }.also { lines ->
                    require(lines.size == 2 || lines.size == 4) { "Rune-running dialogue must have two or four turns." }
                }
            }.distinctBy { lines ->
                lines.map { BotSpeechStack.normalizeChat(expand(it, "nature", Int.MAX_VALUE, Int.MAX_VALUE)) }
            }.also { choices ->
                require(choices.isNotEmpty()) { "Empty rune-running speech context: $event" }
            }
        }
        exchanges = loaded
    }

    /**
     * Selects all turns together. The optional fare suffix disappears for journeys without a native fare.
     */
    fun select(runner: Bot, crafter: Bot, event: Event, altar: Altar,
               runes: Int = 25, fare: Int = 0): List<String>? {
        if (runes <= 0 || fare < 0) return null
        val rune = altar.name.lowercase()
        val choices = exchanges[event].orEmpty()
        val selected = choices.filter { it != runner.lastSelections[event] && it != crafter.lastSelections[event] }
            .ifEmpty { choices }.randomOrNull() ?: return null
        val lines = selected.map { BotSpeechStack.normalizeChat(expand(it, rune, runes, fare)) }
        runner.lastSelections[event] = selected
        crafter.lastSelections[event] = selected
        return lines
    }

    /**
     * Rejects unknown or unmatched tokens and oversized expansions before the native codec could truncate a line.
     */
    private fun validate(template: String) {
        val plain = template.replace("{rune}", "").replace("{runes}", "").replace("{fare}", "")
        require('{' !in plain && '}' !in plain) { "Unknown rune-running speech token." }
        val maximum = expand(template, "nature", Int.MAX_VALUE, Int.MAX_VALUE)
        val minimum = expand(template, "air", 1, 0)
        require(listOf(minimum, maximum).all {
            it.isNotBlank() && it.length <= 80 && BotSpeechStack.normalizeChat(it).isNotBlank()
        }) {
            "Rune-running speech exceeds the native chat limit."
        }
    }

    /**
     * Uses only bounded native item quantities rather than item names or untrusted player-supplied text.
     */
    private fun expand(template: String, rune: String, runes: Int, fare: Int): String = template
        .replace("{rune}", rune)
        .replace("{runes}", runes.toString())
        .replace("{fare}", if (fare > 0) " plus $fare gp fare" else "")
}
