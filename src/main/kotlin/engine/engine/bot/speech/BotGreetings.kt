package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import api.predef.*
import engine.bot.speech.BotReactionSpeechPool.ReactionSpeech
import engine.controllers.Controllers.inWilderness
import engine.trade.tradingWith
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotAttention
import io.luna.game.model.mob.bot.brain.BotAttention.Notice
import io.luna.game.model.mob.bot.memory.BotPeopleMemory.Event
import io.luna.game.model.mob.bot.speech.BotSpeech
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Bots answering a real player who greets the people around them ("hi all") or says a bot's name.
 *
 * Only real players are answered. Bot speech goes through the same chat router, so bots answering bots would set
 * each other off. A general greeting gets one answer, sometimes two, from bots that can see the speaker, pass a roll
 * on their social trait and haven't greeted anyone for a while. A bot that hears its own name at the start or end of
 * a message, with or without the digits on the end, answers more readily. One player is heard at most once every
 * [SPEAKER_COOLDOWN] ticks.
 *
 * A bot remembers who greeted it. It greets someone it has met before as someone it knows, and it does not answer
 * a player it holds a grudge against.
 *
 * Each answer goes to the bot's [BotAttention], so it waits a few ticks, is dropped if the speaker has gone by then,
 * and is said through the speech stack. The script the bot is running is never paused.
 *
 * @author TheLining
 */
object BotGreetings : BotChatHandler {

    /**
     * The most bots that answer one message.
     */
    private const val MAX_REPLIES = 2

    /**
     * The chance that a general greeting gets a second answer.
     */
    private const val SECOND_REPLY_CHANCE = 0.35

    /**
     * How many ticks pass before bots hear the same player again.
     */
    private const val SPEAKER_COOLDOWN = 50L

    /**
     * How many ticks pass before a bot answers another general greeting.
     */
    private const val GREETING_COOLDOWN = 200L

    /**
     * How many ticks pass before a bot answers to its name again.
     */
    private const val NAMED_COOLDOWN = 50L

    /**
     * How many ticks a reply may still be said in, if the bot is busy when its delay ends.
     */
    private const val REPLY_LIFETIME = 15

    /**
     * The fewest ticks between two answers to one message.
     */
    private const val REPLY_GAP = 2

    /**
     * The fewest letters a name without its digits needs before bots answer to it.
     */
    private const val MIN_SHORT_NAME = 4

    /**
     * A greeting of one word, in the spellings players use: "hi", "heyy", "helo", "hiya", "sup", "wazzup".
     */
    private const val GREETING_WORD = "h+i+|h+e+y+|h+[ae]l+o+|h?ello+|hullo|heya+|hiya+|heyo|hiyo|hai|yo+|sup+|" +
        "w[au][sz]+up|whas+up|wh?atsup|howdy|greetings|gday|hola|hoi|hej|ahoy|aloha|bonjour|morning|afternoon|evening"

    /**
     * A greeting of several words: "whats up", "good morning", "how are you", "hows it going".
     */
    private const val GREETING_PHRASE = "wh?at ?s? up|good (?:morning|afternoon|evening|day)|g day|" +
        "how (?:are|r) (?:you|u|ya)|how(?: is| s|s)? it going"

    /**
     * A greeting at the start of a message whose words are joined by single spaces.
     */
    private val GREETING = Regex("^(?:$GREETING_WORD|$GREETING_PHRASE)(?= |$)")

    /**
     * A word that is a greeting by itself.
     */
    private val GREETING_ALONE = Regex(GREETING_WORD)

    /**
     * How long of a bot's time online must pass since it last dealt with a player before it greets them as someone
     * it has met before, in milliseconds.
     */
    private const val MET_BEFORE_MILLIS = 30 * 60_000L

    /**
     * Words that may follow a greeting meant for everyone: "hi all", "hey guys", "sup every1", "hi how are you".
     */
    private val AUDIENCE = setOf("all", "guys", "every1", "everyone", "everybody", "every", "one", "body", "ppl",
                                 "people", "there", "peeps", "folks", "lads", "mates", "m8s", "dudes", "u", "you",
                                 "ya", "yall", "again", "how", "are", "r", "is", "it", "going", "doing", "whats",
                                 "what", "s", "up", "today", "2day", "tonight")

    /**
     * The most words that may follow a greeting meant for everyone.
     */
    private const val MAX_AUDIENCE_WORDS = 6

    /**
     * What separates the words of a message.
     */
    private val SEPARATORS = Regex("[^a-z0-9]+")

    /**
     * The digits and spaces on the end of a name.
     */
    private val NAME_TAIL = Regex("[0-9 ]+$")

    /**
     * The world tick from which bots hear this player's greetings again.
     */
    private var Player.nextGreetingHeard by Attr.long()

    /**
     * The world tick from which this bot answers another general greeting.
     */
    private var Bot.nextGreetingReply by Attr.long()

    /**
     * The world tick from which this bot answers to its name again.
     */
    private var Bot.nextNamedReply by Attr.long()

    /**
     * The player this bot is answering, for the `<name>` tag of [ReactionSpeech.GREETED] and
     * [ReactionSpeech.GREETED_AGAIN] lines.
     */
    var Bot.lastGreetedBy by Attr.string { "null" }

    /**
     * Greets the speaker back.
     */
    private val greet = BotAttention.Reaction { bot, source -> say(bot, source, ReactionSpeech.GREETED) }

    /**
     * Answers a speaker who said the bot's name without greeting it.
     */
    private val answer = BotAttention.Reaction { bot, source -> say(bot, source, ReactionSpeech.CALLED) }

    override fun handle(message: BotChatMessage): Boolean {
        val speaker = message.speaker
        val now = world.currentTick
        if (speaker.isBot || now < speaker.nextGreetingHeard) {
            return false
        }
        val words = words(message.text)
        val opening = greetingWords(words)
        val greeted = opening > 0
        val rest = words.drop(opening)
        val general = greeted && isGeneral(rest)
        val named = if (general) emptyList() else message.hearers.filter { names(rest, it.username) }
        if (!general && named.isEmpty()) {
            return false
        }
        speaker.nextGreetingHeard = now + SPEAKER_COOLDOWN

        val willing = if (general) {
            message.hearers.filter {
                now >= it.nextGreetingReply && onSpeakingTerms(it, speaker) && rand(social(it))
            }
        } else {
            named.filter {
                now >= it.nextNamedReply && onSpeakingTerms(it, speaker) && rand(0.5 + 0.5 * social(it))
            }
        }
        val limit = if (general && !rand(SECOND_REPLY_CHANCE)) 1 else MAX_REPLIES
        var delay = 0
        var answered = false
        for (bot in BotReactions.pickResponders(willing, limit)) {
            delay = maxOf(replyTicks(bot.personality.dexterity), delay + REPLY_GAP)
            val kind = if (greeted) Notice.GREETING else Notice.NAMED
            if (!bot.attention.offer(kind, speaker, delay, REPLY_LIFETIME, if (greeted) greet else answer)) {
                continue
            }
            if (general) {
                bot.nextGreetingReply = now + GREETING_COOLDOWN
            } else {
                bot.nextNamedReply = now + NAMED_COOLDOWN
            }
            answered = true
        }
        return answered
    }

    /**
     * Splits a lowercase message into its words, dropping punctuation.
     *
     * @param text The message.
     * @return The words, in order.
     */
    internal fun words(text: String): List<String> = text.split(SEPARATORS).filter { it.isNotEmpty() }

    /**
     * Counts the words a message opens with that greet: one word ("hi", "sup", "hiya") or a set phrase ("whats up",
     * "good morning", "how are you").
     *
     * @param words The words of the message.
     * @return How many leading words are the greeting, or 0 if the message doesn't open with one.
     */
    internal fun greetingWords(words: List<String>): Int {
        val opening = GREETING.find(words.joinToString(" ")) ?: return 0
        return opening.value.count { it == ' ' } + 1
    }

    /**
     * Whether the words after a greeting leave it meant for everyone: nothing, more greeting ("hi hello"), or
     * words such as "all", "guys" and "how are you".
     *
     * @param rest The words after the greeting.
     */
    internal fun isGeneral(rest: List<String>): Boolean =
        rest.size <= MAX_AUDIENCE_WORDS && rest.all { it in AUDIENCE || GREETING_ALONE.matches(it) }

    /**
     * Whether a message addresses a player by name: it starts or ends with the name, or with the name less the
     * digits on its end ("ollie" for "ollie47") if that leaves at least [MIN_SHORT_NAME] letters. A name in the middle
     * of a message is not counted, so a bot called "bones" stays quiet when bones are for sale.
     *
     * @param words The words of the message, without a leading greeting.
     * @param username The name to look for.
     */
    internal fun names(words: List<String>, username: String): Boolean {
        if (words.isEmpty()) {
            return false
        }
        val full = words(username.lowercase())
        val short = words(shortName(username))
        return full.joinToString("").length >= 3 && atEitherEnd(words, full) ||
            short != full && short.joinToString("").length >= MIN_SHORT_NAME && atEitherEnd(words, short)
    }

    /**
     * A name as other players would say it: lowercase, spaces for underscores, and no digits on the end. A name
     * that is nothing but digits is kept whole.
     *
     * @param username The name.
     */
    internal fun shortName(username: String): String {
        val name = username.lowercase().replace('_', ' ').trim()
        return name.replace(NAME_TAIL, "").ifEmpty { name }
    }

    /**
     * How many ticks a bot takes to notice a message and type a short answer: 2 to 6 for the most dexterous bots,
     * 6 to 10 for the least.
     *
     * @param dexterity The bot's dexterity trait.
     * @param roll A roll from 0 to 1 that spreads the delay.
     */
    internal fun replyTicks(dexterity: Double, roll: Double = Random.nextDouble()): Int {
        val quickness = if (dexterity.isFinite()) dexterity.coerceIn(0.0, 1.0) else 0.5
        return (2 + 4 * (1.0 - quickness) + 4 * roll).roundToInt()
    }

    /**
     * Whether [name] opens or closes [words].
     */
    private fun atEitherEnd(words: List<String>, name: List<String>): Boolean =
        name.isNotEmpty() && words.size >= name.size &&
            (words.subList(0, name.size) == name || words.subList(words.size - name.size, words.size) == name)

    /**
     * A bot's social trait as a chance from 0 to 1.
     */
    private fun social(bot: Bot): Double {
        val social = bot.personality.social
        return if (social.isFinite()) social.coerceIn(0.0, 1.0) else 0.0
    }

    /**
     * Whether a bot is free to chat: not an event bot, muted, fighting a player, in the Wilderness, trading, in the
     * middle of a reflex or in a conversation of its own. Skilling, walking and fighting monsters don't stop it.
     */
    private fun canTalk(bot: Bot): Boolean = bot.isAlive && !bot.isEventBot && !bot.speechStack.isAllDisabled &&
        bot.combat.target !is Player && !bot.inWilderness() && bot.reflex.activeReflex == null &&
        bot.tradingWith == -1 && !BotConversations.engaged(bot) && !BotSocialInteractions.engaged(bot)

    /**
     * Says one line to [source] if the bot is free to chat, or leaves the notice waiting if it isn't.
     */
    private fun onSpeakingTerms(bot: Bot, speaker: Player): Boolean =
        canTalk(bot) && !bot.memory.people.holdsGrudge(speaker.username)

    private fun say(bot: Bot, source: Mob, kind: ReactionSpeech): Boolean {
        if (source !is Player) {
            return true
        }
        if (!canTalk(bot)) {
            return false
        }
        val people = bot.memory.people
        val metBefore = people.sinceLast(source.username) >= MET_BEFORE_MILLIS
        people.remember(source.username, Event.GREETED_ME)
        bot.lastGreetedBy = shortName(source.username)
        val line = if (kind == ReactionSpeech.GREETED && metBefore) ReactionSpeech.GREETED_AGAIN else kind
        BotReactionSpeechPool.take(bot, line)?.let { bot.speechStack.pushHead(BotSpeech(it, 0)) }
        return true
    }
}
