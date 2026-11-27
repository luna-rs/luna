package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.BotGoals
import api.bot.script.BotGoals.goalLine
import api.predef.*
import engine.bot.speech.BotReactionSpeechPool.ReactionSpeech
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotAttention
import io.luna.game.model.mob.bot.brain.BotAttention.Notice
import io.luna.game.model.mob.bot.speech.BotSpeech

/**
 * Bots answering a real player who asks what they are training for or going for.
 *
 * A question put to a bot by name ("bob what are you training") is answered by that bot, even if it has no aim. A
 * question to everyone gets one answer, from a bot that can see the speaker, has an aim, is free to chat and passes a
 * roll on its social trait. The answer names the aim, sometimes with what it unlocks or the ambition behind it.
 *
 * Each answer goes to the bot's [BotAttention], like a greeting, so it waits a few ticks and is dropped if the speaker
 * has gone.
 *
 * @author TheLining
 */
object BotGoalQuestions : BotChatHandler {

    /**
     * How many ticks pass before bots hear the same player ask again.
     */
    private const val SPEAKER_COOLDOWN = 50L

    /**
     * How many ticks pass before a bot answers another question about its goals.
     */
    private const val ANSWER_COOLDOWN = 300L

    /**
     * How many ticks an answer may still be said in, if the bot is busy when its delay ends.
     */
    private const val REPLY_LIFETIME = 15

    /**
     * The questions, in the words players use: "what are you training for", "what u going for", "whats ur goal",
     * "what lvl are you going for", or just "goals?".
     */
    private val QUESTION = Regex(
        "\\b(?:what|wat|wut|wot) (?:(?:are|r) )?(?:you|u|ya) (?:training|going for|aiming for|working on|" +
            "working towards?)\\b|" +
            "\\b(?:whats|what s|what is|wats|wuts) (?:your|ur|yer|ya) (?:goal|goals|aim|next goal)\\b|" +
            "\\b(?:what|wat) (?:lvl|level) (?:are |r )?(?:you|u|ya) (?:going for|aiming for)\\b|" +
            "^(?:any |ur |your )?goals?$")

    /**
     * The world tick from which bots hear this player's questions again.
     */
    private var Player.nextGoalQuestionHeard by Attr.long()

    /**
     * The world tick from which this bot answers another question about its goals.
     */
    private var Bot.nextGoalAnswer by Attr.long()

    /**
     * Answers the speaker.
     */
    private val answer = BotAttention.Reaction { bot, source -> say(bot, source) }

    override fun handle(message: BotChatMessage): Boolean {
        val speaker = message.speaker
        val now = world.currentTick
        if (speaker.isBot || now < speaker.nextGoalQuestionHeard) {
            return false
        }
        val words = BotGreetings.words(message.text)
        if (!asks(words)) {
            return false
        }
        speaker.nextGoalQuestionHeard = now + SPEAKER_COOLDOWN
        val rest = words.drop(BotGreetings.greetingWords(words))
        val named = message.hearers.filter { BotGreetings.names(rest, it.username) }
        val willing = if (named.isNotEmpty()) {
            named.filter { BotGreetings.onSpeakingTerms(it, speaker) && rand(0.5 + 0.5 * social(it)) }
        } else {
            message.hearers.filter {
                now >= it.nextGoalAnswer && it.memory.goals.hasAim() && BotGreetings.onSpeakingTerms(it, speaker) &&
                    rand(social(it))
            }
        }
        for (bot in BotReactions.pickResponders(willing, 1)) {
            val delay = BotGreetings.replyTicks(bot.personality.dexterity)
            if (bot.attention.offer(Notice.QUESTION, speaker, delay, REPLY_LIFETIME, answer)) {
                bot.nextGoalAnswer = now + ANSWER_COOLDOWN
                return true
            }
        }
        return false
    }

    /**
     * Whether a message asks what the people around are going for.
     *
     * @param words The words of the message, in lowercase.
     */
    internal fun asks(words: List<String>): Boolean =
        words.isNotEmpty() && QUESTION.containsMatchIn(words.joinToString(" "))

    /**
     * A bot's social trait as a chance from 0 to 1.
     */
    private fun social(bot: Bot): Double {
        val social = bot.personality.social
        return if (social.isFinite()) social.coerceIn(0.0, 1.0) else 0.0
    }

    private fun say(bot: Bot, source: Mob): Boolean {
        if (source !is Player) {
            return true
        }
        if (!BotGreetings.canTalk(bot)) {
            return false
        }
        val line = BotGoals.answer(bot)
        val text = if (line == null) {
            BotReactionSpeechPool.take(bot, ReactionSpeech.GOAL_NONE)
        } else {
            bot.goalLine = line
            BotReactionSpeechPool.take(bot, ReactionSpeech.GOAL_ANSWER, BotGoals::complete)
        }
        text?.let { bot.speechStack.pushHead(BotSpeech(it, 0)) }
        return true
    }
}
