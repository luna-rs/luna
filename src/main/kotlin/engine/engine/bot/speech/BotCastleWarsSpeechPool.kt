package engine.bot.speech

import io.luna.game.model.mob.bot.speech.BotSpeechPool

/**
 * Short waiting-room remarks, delivered through the cancellable conversation runner.
 *
 * @author lare96
 */
object BotCastleWarsSpeechPool : BotSpeechPool<BotCastleWarsSpeechPool.Line>("castle_wars.jsonc", Line::class.java) {
    /**
     * Waiting-room context, separate from active-match combat speech.
     *
     * @author lare96
     */
    enum class Line { WAITING }
}
