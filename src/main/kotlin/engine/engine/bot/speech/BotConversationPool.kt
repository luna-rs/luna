package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import com.google.gson.JsonElement
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.game.model.mob.bot.speech.BotSpeechStack

/**
 * Loads authored alternating dialogue from the existing conversation corpus.
 * Duplicate conversations do not gain hidden weight, and neither participant immediately repeats its last selection.
 * Scenario-specific scam pools can supply the same line-list format to the shared runner separately.
 *
 * @author lare96
 */
object BotConversationPool : BotSpeechPool<BotConversationPool.ConversationSpeech>(
    "conversations.jsonc", ConversationSpeech::class.java
) {
    /**
     * Identifies the idle dialogue supplied by this structured speech pool.
     *
     * @author lare96
     */
    enum class ConversationSpeech {
        IDLE
    }

    /**
     * Runtime-only selection history; interrupted exchanges are not replayed after logout.
     */
    private var Bot.lastConversation by Attr.int { -1 }

    /**
     * Validated native-codec lines. Each conversation keeps both speakers and fits the runner's bounded turn count.
     */
    internal var conversations: List<List<String>> = emptyList()
        private set

    override fun read(data: JsonElement) {
        conversations = data.asJsonArray.map { conversation ->
            conversation.asJsonArray.map { BotSpeechStack.normalizeChat(it.asString) }
        }.filter { it.size in 2..24 && it.size % 2 == 0 && it.all { line -> line.isNotBlank() } }.distinct()
    }

    /**
     * Selects one coherent exchange, avoiding the latest selection of both participants when alternatives exist.
     */
    fun select(first: Bot, second: Bot): List<String>? {
        val indices = conversations.indices.filter { it != first.lastConversation && it != second.lastConversation }
            .ifEmpty { conversations.indices.toList() }
        val selected = indices.randomOrNull() ?: return null
        first.lastConversation = selected
        second.lastConversation = selected
        return conversations[selected]
    }
}
