package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.bot.Bot

/**
 * Exclusive runtime ownership shared by social activities. A bot keeps its current script and coroutine;
 * trades, journeys and future social activities supply their own protocol through [Session].
 *
 * @author lare96
 */
object BotSocialInteractions {
    private var Bot.interaction: Session? by Attr.nullableObj(Session::class)

    /**
     * A cooperative, game-thread-owned interaction. Cancellation must release only its own resources.
     *
     * @author lare96
     */
    interface Session {
        fun valid(): Boolean
        suspend fun participate(bot: Bot)
        fun close()
    }

    /**
     * Acquires every participant together without preempting a running activity or another live interaction.
     */
    fun claim(bots: List<Bot>, session: Session): Boolean {
        if (bots.isEmpty() || bots.distinct().size != bots.size || bots.any { engaged(it) }) return false
        bots.forEach { it.interaction = session }
        return true
    }

    /**
     * Authorizes continuation dialogue and releases stale ownership before a new social approach.
     */
    fun engaged(bot: Bot): Boolean {
        val session = bot.interaction ?: return false
        if (!session.valid()) session.close()
        return bot.interaction === session
    }

    /**
     * Tests identity without recursively validating the session's own conversation.
     */
    fun owns(bot: Bot, session: Session): Boolean = bot.interaction === session

    /**
     * Gives the participant's normal script loop one turn of its shared activity.
     */
    suspend fun participate(bot: Bot): Boolean {
        val session = bot.interaction ?: return false
        if (!session.valid()) session.close() else session.participate(bot)
        return true
    }

    /**
     * Cancels a paused or replaced script's current social activity.
     */
    fun cancel(bot: Bot) {
        bot.interaction?.close()
    }

    /**
     * Releases only this owner, so a late cleanup cannot clear a replacement interaction.
     */
    fun release(bots: List<Bot>, session: Session) {
        bots.filter { it.interaction === session }.forEach { it.interaction = null }
    }
}
