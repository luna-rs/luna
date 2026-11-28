package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.script.BotGoals
import api.predef.*
import engine.bot.speech.BotReactionSpeechPool.ReactionSpeech
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotAttention
import io.luna.game.model.mob.bot.brain.BotAttention.Notice
import io.luna.game.model.mob.bot.memory.BotOutlookMemory
import io.luna.game.model.mob.bot.memory.BotOutlookMemory.Run
import io.luna.game.model.mob.bot.memory.BotPeopleMemory
import io.luna.game.model.mob.bot.speech.BotSpeech

/**
 * Bots answering a real player who asks how they are doing.
 *
 * A question put to a bot by name ("bob how are you") is answered by that bot. A question to everyone gets one answer,
 * from a bot that can see the speaker, is free to chat and passes a roll on its social trait. The answer follows the
 * bot's outlook: a good run, a bad run or nothing much, sometimes with the reason. A question no bot takes is left for
 * the greeting handler, which treats "how are you" as a greeting.
 *
 * Each answer goes to the bot's [BotAttention], like a greeting, so it waits a few ticks and is dropped if the speaker
 * has gone.
 *
 * @author TheLining
 */
object BotMoodQuestions : BotChatHandler {

    /**
     * How many ticks pass before bots hear the same player ask again.
     */
    private const val SPEAKER_COOLDOWN = 50L

    /**
     * How many ticks pass before a bot answers another question about how it is doing.
     */
    private const val ANSWER_COOLDOWN = 300L

    /**
     * How many ticks an answer may still be said in, if the bot is busy when its delay ends.
     */
    private const val REPLY_LIFETIME = 15

    /**
     * How far back a bot looks for the reason for its mood, in milliseconds of real time.
     */
    private const val LATELY_MILLIS = 24 * 3_600_000L

    /**
     * How far back a bot counts its kills as today's, in milliseconds of real time.
     */
    private const val TODAY_MILLIS = 12 * 3_600_000L

    /**
     * Words after "how are you" that make it a question about something else: "how are you so rich", "how are you
     * supposed to get there", "how are you doing that".
     */
    private const val NOT_ABOUT_MOOD = "so|supposed|going to|gonna|getting|making|meant|able|not|still|even|that|" +
        "this|lvl|level|a|an|the|only|already|doing (?:that|this|it)"

    /**
     * The questions, in the words players use: "how are you", "how r u doing", "hows it going", "hows ur day",
     * "hru", or just "u ok".
     */
    private val QUESTION = Regex(
        "\\bhow (?:are|r) (?:you|u|ya)\\b(?! (?:$NOT_ABOUT_MOOD)\\b)|" +
            "\\bhow (?:you|u|ya) (?:doing|doin|been)\\b(?! (?:that|this|it)\\b)|" +
            "\\bhow(?: is| s|s)? (?:it going|things|life|everything)\\b(?! to\\b)|" +
            "\\bhow(?: is| s|s| was)? (?:your|ur|yer) (?:day|night|evening)\\b|" +
            "\\bhow goes it\\b|" +
            "(?:^| )hru(?: |$)|" +
            "^(?:are |r )?(?:you|u|ya) (?:ok|okay|alright|good)$")

    /**
     * The world tick from which bots hear this player's questions again.
     */
    private var Player.nextMoodQuestionHeard by Attr.long()

    /**
     * The world tick from which this bot answers another question about how it is doing.
     */
    private var Bot.nextMoodAnswer by Attr.long()

    /**
     * Answers the speaker.
     */
    private val answer = BotAttention.Reaction { bot, source -> say(bot, source) }

    override fun handle(message: BotChatMessage): Boolean {
        val speaker = message.speaker
        val now = world.currentTick
        if (speaker.isBot || now < speaker.nextMoodQuestionHeard) {
            return false
        }
        val words = BotGreetings.words(message.text)
        if (!asks(words)) {
            return false
        }
        speaker.nextMoodQuestionHeard = now + SPEAKER_COOLDOWN
        val rest = words.drop(BotGreetings.greetingWords(words))
        val named = message.hearers.filter { BotGreetings.names(rest, it.username) }
        val willing = if (named.isNotEmpty()) {
            named.filter { BotGreetings.onSpeakingTerms(it, speaker) && rand(0.5 + 0.5 * social(it)) }
        } else {
            message.hearers.filter {
                now >= it.nextMoodAnswer && BotGreetings.onSpeakingTerms(it, speaker) && rand(social(it))
            }
        }
        for (bot in BotReactions.pickResponders(willing, 1)) {
            val delay = BotGreetings.replyTicks(bot.personality.dexterity)
            if (bot.attention.offer(Notice.QUESTION, speaker, delay, REPLY_LIFETIME, answer)) {
                bot.nextMoodAnswer = now + ANSWER_COOLDOWN
                return true
            }
        }
        return false
    }

    /**
     * Whether a message asks the people around how they are doing.
     *
     * @param words The words of the message, in lowercase.
     */
    internal fun asks(words: List<String>): Boolean =
        words.isNotEmpty() && QUESTION.containsMatchIn(words.joinToString(" "))

    /**
     * Says in a few words why a bot is on a good or a bad run, for the `<why>` tag of [ReactionSpeech.MOOD_GOOD] and
     * [ReactionSpeech.MOOD_BAD] lines: the latest thing it remembers that fits the run.
     *
     * @param bot The bot.
     * @return The reason, or [BotGoals.MISSING] if the bot has none to give.
     */
    internal fun why(bot: Bot): String {
        val outlook = bot.memory.outlook
        val run = outlook.run()
        if (run == Run.FAIR) {
            return BotGoals.MISSING
        }
        return when (outlook.latest(run == Run.GOOD, LATELY_MILLIS)) {
            BotOutlookMemory.Event.KILLED_BY_PLAYER, BotOutlookMemory.Event.KILLED_BY_MONSTER ->
                when (val deaths = BotReactions.deathsToday(bot)) {
                    0, 1 -> "died"
                    2 -> "died twice"
                    else -> "died $deaths times"
                }
            BotOutlookMemory.Event.SCAMMED -> "got scammed"
            BotOutlookMemory.Event.LURED -> "got lured"
            BotOutlookMemory.Event.LOST_WEALTH -> "lost a load of gp"
            BotOutlookMemory.Event.KILLED_PLAYER ->
                when (val kills = outlook.count(BotOutlookMemory.Event.KILLED_PLAYER, TODAY_MILLIS)) {
                    0, 1 -> "got a kill"
                    else -> "got $kills kills"
                }
            BotOutlookMemory.Event.REACHED_GOAL -> "hit my goal"
            BotOutlookMemory.Event.RARE_DROP -> "got a nice drop"
            BotOutlookMemory.Event.GAINED_WEALTH -> "made good money"
            else -> BotGoals.MISSING
        }
    }

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
        // The greeting handler would have counted this message as a greeting.
        bot.memory.people.remember(source.username, BotPeopleMemory.Event.GREETED_ME)
        val kind = when (bot.memory.outlook.run()) {
            Run.GOOD -> ReactionSpeech.MOOD_GOOD
            Run.BAD -> ReactionSpeech.MOOD_BAD
            else -> ReactionSpeech.MOOD_OK
        }
        BotReactionSpeechPool.take(bot, kind, BotGoals::complete)?.let { bot.speechStack.pushHead(BotSpeech(it, 0)) }
        return true
    }
}
