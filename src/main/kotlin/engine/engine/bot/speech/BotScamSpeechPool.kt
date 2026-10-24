package engine.bot.speech

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.game.model.mob.bot.speech.BotSpeechStack

/**
 * Authored bot-only scam advertisements and coherent two-speaker exchanges, loaded through the normal speech pool.
 * Each exchange starts with the actor; alternate lines belong to the prospective victim.
 * Missing or invalid data leaves the previous usable pool intact and never manufactures a successful scam.
 *
 * @author lare96
 */
object BotScamSpeechPool : BotSpeechPool<BotScamSpeechPool.ScamSpeech>("scams.jsonc", ScamSpeech::class.java) {
    /**
     * Scenario and stage of the advertisement or complete paired exchange.
     *
     * @author lare96
     */
    enum class ScamSpeech {
        GOLD, GEAR, PK,
        LURE_ADVERT, LURE_PITCH, LURE_TRAVEL,
        DOUBLING_ADVERT, TRIMMING_ADVERT,
        DOUBLING_PITCH, TRIMMING_PITCH,
        DOUBLING_SUCCESS, TRIMMING_SUCCESS, REFUSAL
    }

    /**
     * Whole exchanges; single-line advertisements continue to use the inherited phrase selection.
     */
    private var exchanges = emptyMap<ScamSpeech, List<List<String>>>()

    override fun read(data: JsonElement) {
        val phrases = JsonObject()
        val loaded = mutableMapOf<ScamSpeech, List<List<String>>>()
        for ((key, value) in data.asJsonObject.entrySet()) {
            val context = ScamSpeech.valueOf(key)
            if (context in phrasesOnly) {
                require(value.asJsonArray.all { it.asString.isNotBlank() && it.asString.length <= 80 })
                phrases.add(key, value)
            } else {
                loaded[context] = value.asJsonArray.map { exchange ->
                    exchange.asJsonArray.map { BotSpeechStack.normalizeChat(it.asString) }.also { lines ->
                        require(lines.size in 2..24 && lines.size % 2 == 0 && lines.all { it.isNotBlank() })
                    }
                }.distinct()
            }
        }
        require((ScamSpeech.entries - setOf(ScamSpeech.GOLD, ScamSpeech.GEAR, ScamSpeech.PK)).all { context ->
            if (context in phrasesOnly)
                phrases.getAsJsonArray(context.name)?.size()?.let { it > 0 } == true
            else loaded[context]?.isNotEmpty() == true
        })
        super.read(phrases)
        exchanges = loaded
    }

    /**
     * Selects all turns together so an answer cannot be drawn from an unrelated conversation.
     */
    fun exchange(context: ScamSpeech): List<String>? = exchanges[context]?.randomOrNull()

    /**
     * Retains the existing gold, gear and future lure advertisements alongside scenario-specific dialogue additions.
     */
    private val phrasesOnly = setOf(ScamSpeech.GOLD, ScamSpeech.GEAR, ScamSpeech.PK,
        ScamSpeech.DOUBLING_ADVERT, ScamSpeech.TRIMMING_ADVERT, ScamSpeech.LURE_ADVERT)
}
