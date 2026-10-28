package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import com.google.gson.JsonElement
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.game.model.mob.bot.speech.BotSpeechStack

/** Camp dialogue is authored by attitude and delivered by the existing conversation runner. */
object BotCampSpeechPool : BotSpeechPool<BotCampSpeechPool.Line>("camps.jsonc", Line::class.java) {
    enum class Tone { FRIENDLY, COMPLAINER, AGGRAVATED, MEAN }

    enum class Line {
        OPEN_FRIENDLY, OPEN_COMPLAINER, OPEN_AGGRAVATED, OPEN_MEAN,
        REPLY_FRIENDLY, REPLY_COMPLAINER, REPLY_AGGRAVATED, REPLY_MEAN,
        HELLO_FRIENDLY, HELLO_COMPLAINER, HELLO_AGGRAVATED, HELLO_MEAN,
        LEVEL, DYING
    }

    private var phrases: Map<Line, List<String>> = emptyMap()
    private var Bot.lastCampLine by Attr.string { "" }

    override fun read(data: JsonElement) {
        val loaded = Line.entries.associateWith { kind ->
            val lines = data.asJsonObject.getAsJsonArray(kind.name)
                ?: error("Missing camp speech: $kind")
            lines.map { it.asString }.distinct().also { values ->
                require(values.isNotEmpty() && values.all {
                    it.isNotBlank() && it.replace("<skill>", "strength").replace("<level>", "126").length <= 80
                }) { "Invalid camp speech: $kind" }
            }
        }
        phrases = loaded
    }

    fun line(bot: Bot, kind: Line, skill: String = "combat", level: Int = bot.combatLevel): String? {
        val choices = phrases[kind].orEmpty()
        val text = choices.filter { it != bot.lastCampLine }.ifEmpty { choices }.randomOrNull() ?: return null
        bot.lastCampLine = text
        return BotSpeechStack.normalizeChat(text.replace("<skill>", skill).replace("<level>", level.toString()))
    }

    /** Both speakers choose their own tone; no friendly bot is assigned the mean bot's answer. */
    fun exchange(first: Bot, firstTone: Tone, second: Bot, secondTone: Tone): List<String>? {
        val opening = line(first, Line.valueOf("OPEN_$firstTone")) ?: return null
        val response = line(second, Line.valueOf("REPLY_$secondTone")) ?: return null
        return listOf(opening, response)
    }
}
