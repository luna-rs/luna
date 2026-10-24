package engine.bot.speech

import com.google.gson.JsonElement
import engine.bot.speech.BotPriceCheckSpeechPool.PriceCheckSpeech
import io.luna.game.model.mob.bot.speech.BotSpeechPool

/**
 * The price check lines in `pricecheck.jsonc`: questions bots ask at the markets and the answers they give.
 *
 * Loading checks every line. Questions must read as a price check of their `<item>`, answers must never read as one,
 * and every line must fit in a chat message. A file that fails a check is not loaded.
 *
 * @author TheLining
 */
object BotPriceCheckSpeechPool :
    BotSpeechPool<PriceCheckSpeech>("pricecheck.jsonc", PriceCheckSpeech::class.java) {

    /**
     * The kinds of price check line.
     */
    enum class PriceCheckSpeech {

        /**
         * A bot asking about one of its own items. Supports `<item>`.
         */
        ASK,

        /**
         * A confident answer. Supports `<item>` and `<price>`.
         */
        ANSWER,

        /**
         * An unsure answer. Supports `<item>` and `<price>`.
         */
        UNSURE,

        /**
         * No idea of the price.
         */
        NO_IDEA,

        /**
         * A rare item nobody has a price for.
         */
        RARE,

        /**
         * An item that can't be traded.
         */
        UNTRADEABLE,

        /**
         * Agreeing with the previous answer. Supports `<price>`.
         */
        AGREE,

        /**
         * Disagreeing with the previous answer. Supports `<price>`.
         */
        DISAGREE,

        /**
         * A bot thanking whoever answered its question.
         */
        THANKS
    }

    /**
     * A long item name used to test each line.
     */
    private const val SAMPLE_ITEM = "dragon longsword"

    /**
     * A long price used to test each line.
     */
    private const val SAMPLE_PRICE = "1.2m ea"

    override fun read(data: JsonElement) {
        val lines = data.asJsonObject
        for (kind in PriceCheckSpeech.entries) {
            val kindLines = lines.getAsJsonArray(kind.name)
            require(kindLines != null && kindLines.size() > 0) { "No $kind lines." }
            for (element in kindLines) {
                val line = element.asString
                val sample = line.replace("<item>", SAMPLE_ITEM).replace("<price>", SAMPLE_PRICE)
                require(sample.length <= 80) { "Too long: $line" }
                val question = BotPriceChecks.parse(sample)
                if (kind == PriceCheckSpeech.ASK) {
                    require(question?.items == listOf(SAMPLE_ITEM)) { "Not a price check of <item>: $line" }
                } else {
                    require(question == null) { "Reads as a price check: $line" }
                }
            }
        }
        super.read(data)
    }
}
