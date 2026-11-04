package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.bot.isRealBot
import engine.bot.speech.BotConversationPool
import engine.bot.speech.BotConversationPool.ConversationSpeech
import io.luna.game.model.EntityType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlin.random.Random

/**
 * Advances paired speech independently of coroutine waits and recruits company at staggered social decision points.
 * Native training owns its actions and timing. This controller only reserves speech and never queues movement or work.
 *
 * @author lare96
 */
object BotAmbientConversations {
    const val IDLE_START_CHANCE = 0.97
    const val WORK_START_CHANCE = 0.80
    const val PARTNER_RADIUS = 8
    const val MAX_PLAYERS_CHECKED = 96
    val IDLE_DECISION_TICKS = 20L..50L
    val WORK_DECISION_TICKS = 40L..80L
    val CONVERSATION_COOLDOWN_TICKS = 25L..65L

    /**
     * Timing lives on the bot, so changing scripts cannot reset a listener's social cooldown.
     *
     * @author lare96
     */
    private class Timing {
        var nextDecision = -1L
        var availableAt = 0L
    }

    private val Bot.ambientTiming by Attr.obj { Timing() }
    private val chunkOffsets = (-1..1).flatMap { x -> (-1..1).map { y -> x to y } }

    /**
     * Called once per native bot tick after reflex decisions, including while its current coroutine is suspended.
     */
    fun tick(bot: Bot) {
        BotConversations.tick(bot)
        val script = bot.scriptStack.current() as? SocialBotScript ?: return
        val topic = script.ambientConversationTopic() ?: return
        val timing = bot.ambientTiming
        val now = bot.world.currentTick
        if (timing.nextDecision < 0) {
            timing.nextDecision = now + (5L..35L).random()
            return
        }
        if (now < timing.nextDecision || now < timing.availableAt) return
        val idle = topic == ConversationSpeech.HOME || topic == ConversationSpeech.IDLE
        timing.nextDecision = now + (if (idle) IDLE_DECISION_TICKS else WORK_DECISION_TICKS).random()
        if (!BotConversations.available(bot, allowWorking = true) ||
            Random.nextDouble() >= if (idle) IDLE_START_CHANCE else WORK_START_CHANCE) return
        val partner = findPartner(bot, now) ?: return
        val lines = BotConversationPool.select(bot, partner, topic) ?: return
        BotConversations.start(bot, partner, lines, allowWorking = true, finished = {
            val endedAt = bot.world.currentTick
            for (participant in listOf(bot, partner)) {
                val state = participant.ambientTiming
                state.availableAt = endedAt + CONVERSATION_COOLDOWN_TICKS.random()
                state.nextDecision = maxOf(state.nextDecision, state.availableAt)
            }
        })?.tick()
    }

    /**
     * Examines at most nine neighboring chunks and a fixed player budget, with random traversal and reservoir choice.
     * Candidates must explicitly opt into ambient company and remain eligible at the moment of acquisition.
     */
    internal fun findPartner(bot: Bot, now: Long): Bot? {
        val center = bot.position.chunk
        val offsets = chunkOffsets.shuffled()
        var checked = 0
        var matches = 0
        var selected: Bot? = null
        for ((x, y) in offsets) {
            val players = bot.world.chunks.load(center.translate(x, y)).getAll<Player>(EntityType.PLAYER)
            for (player in players) {
                if (++checked > MAX_PLAYERS_CHECKED) return selected
                if (!player.isRealBot() || player === bot || !bot.isWithinDistance(player, PARTNER_RADIUS)) continue
                val candidate = player as Bot
                if (bot.ignores.contains(candidate.usernameHash) || candidate.ignores.contains(bot.usernameHash)) continue
                val script = candidate.scriptStack.current() as? SocialBotScript ?: continue
                if (script.ambientConversationTopic() == null || now < candidate.ambientTiming.availableAt ||
                    !BotConversations.available(candidate, allowWorking = true)) continue
                if (Random.nextInt(++matches) == 0) selected = candidate
            }
        }
        return selected
    }
}
