package engine.bot.speech

import com.google.gson.JsonElement
import game.skill.mining.Ore
import io.luna.game.model.mob.bot.speech.BotSpeechStack

/**
 * How a miner talks. Each voice follows the bot's mining personality, so a kind bot sounds kind in every line.
 * [IRON_TRAINER] is a power miner dropping iron; it also borrows [POWER_MINER] lines.
 *
 * @author TheLining
 */
enum class MiningVoice { KIND, MEAN, COMPLAINER, POWER_MINER, IRON_TRAINER, BALANCED }

/**
 * Shared loading and selection for mining chat. A line is either plain text, or an object with `text` and an
 * `ores` list that limits it to those rocks. `{ore}` is replaced with the rock's chat name, such as "addy".
 *
 * @author TheLining
 */
object MiningLines {

    class Line(val text: String, val ores: Set<Ore>) {
        fun fits(ore: Ore) = ores.isEmpty() || ore in ores
        fun render(ore: Ore) = BotSpeechStack.normalizeChat(text.replace("{ore}", chatName(ore)))
    }

    /** Longest chat name, used to check that every line fits the 80 character chat limit. */
    private const val LONGEST_NAME = "limestone"

    /** Player slang for each rock, as people typed it in 2005. */
    fun chatName(ore: Ore): String = when (ore) {
        Ore.RUNE_ESSENCE, Ore.PURE_ESSENCE -> "ess"
        Ore.GEM_ROCK -> "gem"
        Ore.MITHRIL -> "mith"
        Ore.ADAMANT -> "addy"
        Ore.PUNISHMENT_ROCK -> "rock"
        else -> ore.name.lowercase()
    }

    /** Reads `{ "VOICE": [ line, ... ] }`. */
    fun readVoices(data: JsonElement, where: String): Map<MiningVoice, List<Line>> =
        data.asJsonObject.entrySet().associate { (voice, lines) ->
            MiningVoice.valueOf(voice) to lines.asJsonArray.map { readLine(it, "$where.$voice") }
        }

    private fun readLine(element: JsonElement, where: String): Line {
        val line = if (element.isJsonPrimitive) Line(element.asString, emptySet()) else {
            val obj = element.asJsonObject
            Line(obj.get("text").asString, obj.getAsJsonArray("ores")?.map { Ore.valueOf(it.asString) }?.toSet()
                ?: emptySet())
        }
        require(line.text.isNotBlank() && line.text.replace("{ore}", LONGEST_NAME).length <= 80) {
            "Invalid mining line in $where: ${line.text}"
        }
        return line
    }

    /** Lines a voice may use; iron trainers also use power-miner lines. */
    fun linesFor(pool: Map<MiningVoice, List<Line>>?, voice: MiningVoice): List<Line> {
        pool ?: return emptyList()
        val own = pool[voice].orEmpty()
        return if (voice == MiningVoice.IRON_TRAINER) own + pool[MiningVoice.POWER_MINER].orEmpty() else own
    }

    /** Picks a line for this ore, preferring ones nobody nearby has heard recently. Ore-specific lines win more. */
    fun pick(lines: List<Line>, ore: Ore, recent: Collection<String>): String? {
        val fitting = lines.filter { it.fits(ore) }
        if (fitting.isEmpty()) return null
        val fresh = fitting.filter { it.render(ore) !in recent }.ifEmpty { fitting }
        val special = fresh.filter { it.ores.isNotEmpty() }
        val chosen = if (special.isNotEmpty() && Math.random() < 0.4) special.random() else fresh.random()
        return chosen.render(ore)
    }
}
