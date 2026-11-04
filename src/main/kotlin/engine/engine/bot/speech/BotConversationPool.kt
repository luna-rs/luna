package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import com.google.gson.JsonElement
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.speech.BotSpeechPool
import io.luna.game.model.mob.bot.speech.BotSpeechStack
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Loads coherent alternating dialogue, including exchanges relevant to the initiating bot's current activity.
 * Both participants remember recent exchanges and lines across script changes without persisting live social state.
 * Scenario-specific scam pools can supply the same line-list format to the shared runner separately.
 *
 * @author lare96
 */
object BotConversationPool : BotSpeechPool<BotConversationPool.ConversationSpeech>(
    "conversations.jsonc", ConversationSpeech::class.java
) {
    /**
     * Activity of the first speaker. IDLE contains general dialogue suitable for any safe social activity.
     *
     * @author lare96
     */
    enum class ConversationSpeech {
        IDLE, HOME, ALCHEMY, FISHING, WOODCUTTING, COOKING, FLETCHING, CRAFTING, SMITHING
    }

    /**
     * Bounded recent history for each participant, including lines heard from their partners.
     *
     * @author lare96
     */
    internal class History {
        private val exchanges = ArrayDeque<List<String>>()
        private val lines = ArrayDeque<String>()

        fun score(exchange: List<String>): Int = (if (exchange in exchanges) 10_000 else 0) +
            exchange.count { it in lines }

        fun remember(exchange: List<String>) {
            if (exchanges.size == RECENT_EXCHANGES) exchanges.removeFirst()
            exchanges.addLast(exchange)
            exchange.forEach {
                if (lines.size == RECENT_LINES) lines.removeFirst()
                lines.addLast(it)
            }
        }
    }

    private val Bot.recentConversation by Attr.obj { History() }
    private const val RECENT_EXCHANGES = 12
    private const val RECENT_LINES = 64
    private const val CANDIDATE_LIMIT = 48

    /**
     * Chance of preferring activity dialogue when the current script supplies a relevant topic.
     */
    const val CONTEXT_CHANCE = 0.85

    /**
     * Validated native-codec lines. Each conversation keeps both speakers and fits the runner's bounded turn count.
     */
    internal var conversations: List<List<String>> = emptyList()
        private set

    private var topics: Map<ConversationSpeech, List<List<String>>> = emptyMap()

    override fun read(data: JsonElement) {
        val loaded = linkedMapOf<List<String>, MutableSet<ConversationSpeech>>()
        data.asJsonArray.forEach { conversation ->
            val contexts = if (conversation.isJsonArray) setOf(ConversationSpeech.IDLE) else {
                conversation.asJsonObject.getAsJsonArray("topics").map {
                    ConversationSpeech.valueOf(it.asString)
                }.toSet().also { require(it.isNotEmpty()) { "Conversation needs at least one topic." } }
            }
            val turns = if (conversation.isJsonArray) conversation.asJsonArray else {
                conversation.asJsonObject.getAsJsonArray("lines")
            }
            val raw = turns.map { it.asString }
            val lines = raw.map { BotSpeechStack.normalizeChat(it) }
            if (lines.size in 2..24 && lines.size % 2 == 0 && raw.all { it.length <= 80 } &&
                lines.all { it.isNotBlank() }) {
                loaded.getOrPut(lines) { mutableSetOf() }.addAll(contexts)
            }
        }
        require(loaded.isNotEmpty()) { "Conversation corpus contains no valid exchanges." }
        val grouped = ConversationSpeech.entries.associateWith { topic ->
            loaded.filterValues { topic in it }.keys.toList()
        }
        conversations = loaded.keys.toList()
        topics = grouped
    }

    /**
     * Chooses whole exchanges with bounded sampling. Contextual lines describe the first speaker's actual activity.
     * General conversations provide variety and a fallback when a small activity pool was heard recently.
     */
    fun select(first: Bot, second: Bot, topic: ConversationSpeech = ConversationSpeech.IDLE): List<String>? =
        select(topic, first.recentConversation, second.recentConversation)

    internal fun select(topic: ConversationSpeech, first: History, second: History,
                        random: Random = Random.Default): List<String>? {
        val contextual = topics[topic].orEmpty()
        val general = topics[ConversationSpeech.IDLE].orEmpty()
        val choices = if (topic != ConversationSpeech.IDLE && contextual.isNotEmpty() &&
            (general.isEmpty() || random.nextDouble() < CONTEXT_CHANCE)) contextual else general
        var selected = choose(choices.ifEmpty { contextual }, first, second, random) ?: return null
        if (first.score(selected) >= 10_000 || second.score(selected) >= 10_000) {
            val fallback = choose(general, first, second, random)
            if (fallback != null && first.score(fallback) + second.score(fallback) <
                first.score(selected) + second.score(selected)) selected = fallback
        }
        first.remember(selected)
        if (second !== first) second.remember(selected)
        return selected
    }

    /**
     * Visits at most 48 distinct entries, retaining the least recently heard exchange and avoiding repeated lines.
     */
    private fun choose(choices: List<List<String>>, first: History, second: History,
                       random: Random): List<String>? {
        if (choices.isEmpty()) return null
        val start = random.nextInt(choices.size)
        var best: List<String>? = null
        var score = Int.MAX_VALUE
        repeat(minOf(CANDIDATE_LIMIT, choices.size)) { offset ->
            val candidate = choices[(start + offset) % choices.size]
            val candidateScore = first.score(candidate) + second.score(candidate)
            if (candidateScore < score) {
                best = candidate
                score = candidateScore
            }
        }
        return best
    }
}
