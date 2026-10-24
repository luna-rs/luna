package engine.bot.speech

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.trading.BotMarketplace
import api.bot.script.BotScript.ActionSafety
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import api.bot.social.SocialBotScript
import api.predef.*
import api.predef.ext.*
import engine.bot.speech.BotPriceCheckSpeechPool.PriceCheckSpeech
import engine.controllers.Controllers.inWilderness
import engine.trade.tradingWith
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemNicknameDefinition
import io.luna.game.model.item.economy.ItemPriceData
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.mob.bot.speech.BotSpeech
import io.luna.util.GsonUtils
import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import java.nio.file.Paths
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Bots answering price checks in public chat ("pc d long?" → "60k ea"), and asking each other at the markets.
 *
 * Anyone's price check can get up to two answers from bots that can see the speaker and aren't busy. Social bots
 * answer more often. A second bot sometimes follows, disagreeing if its own guess is far off. Answers come from the
 * world economy, shaped by personality: clever bots are close, dumb bots guess and greedy bots lowball. Each reply
 * waits roughly as long as reading the question and typing the answer takes, and is dropped if a real player answers
 * first or the bot gets busy. Replies only ever contain template text, item nicknames and prices, never the asker's
 * words.
 *
 * While a real player can see them, bots waiting at a [BotMarketplace] ask about something they own or want every 20
 * to 40 seconds per market, each bot at most once every few minutes. Their questions go through the same chat path as
 * anyone else's.
 *
 * @author TheLining
 */
object BotPriceChecks : BotChatHandler {

    /**
     * A price check read from chat.
     *
     * @property quantity How many of each item were asked about.
     * @property items The item names as typed, up to [MAX_ITEMS].
     */
    class Question(val quantity: Int, val items: List<String>)

    /**
     * A question bots are answering.
     *
     * @property asker Who asked.
     * @property position Where they asked.
     * @property expires When replies stop being useful, in epoch milliseconds.
     */
    private class Asked(val asker: Player, val position: Position, val expires: Long) {

        /**
         * Whether a real player answered, which cancels bot replies that haven't gone out yet.
         */
        var answeredByPlayer = false

        /**
         * How many bot replies went out.
         */
        var replies = 0
    }

    /**
     * A bot's answer.
     *
     * @property text What it says.
     * @property estimate The price each it named, or `null` if it named none.
     * @property scale How many items the named price covered: the quantity for a total, otherwise 1.
     * @property suffix What followed the price when it was a price each for several items (" ea").
     */
    private class Reply(val text: String, val estimate: Double?, val scale: Int = 1, val suffix: String = "")

    /**
     * The logger.
     */
    private val logger = LogManager.getLogger()

    /**
     * The coins item id.
     */
    private const val COINS = 995

    /**
     * The most items answered in one question.
     */
    private const val MAX_ITEMS = 3

    /**
     * How long, in milliseconds, before the same player's next price check is answered.
     */
    private const val ASKER_COOLDOWN = 10_000L

    /**
     * How long, in milliseconds, before a bot answers another price check.
     */
    private const val ANSWER_COOLDOWN = 30_000L

    /**
     * How long, in milliseconds, before a bot thanks anyone again.
     */
    private const val THANKS_COOLDOWN = 20_000L

    /**
     * How long, in milliseconds, a question stays open for replies.
     */
    private const val QUESTION_LIFETIME = 20_000L

    /**
     * The chance that a second bot follows the first answer.
     */
    private const val SECOND_ANSWER_CHANCE = 0.4

    /**
     * How far apart, as a fraction, two guesses must be before the second bot disagrees.
     */
    private const val DISAGREE_GAP = 0.15

    /**
     * The cheapest item bots ask about at the markets.
     */
    private const val MIN_ASK_PRICE = 1_000.0

    /**
     * Seconds per game tick.
     */
    private const val TICK_SECONDS = 0.6

    /**
     * The gap between questions at one market, in milliseconds.
     */
    private val ASK_GAP = 20_000L..40_000L

    /**
     * How long, in milliseconds, before the same bot asks again.
     */
    private val ASKER_GAP = 180_000L..360_000L

    /**
     * Words that start a price check, longer forms before the shorter forms they begin with.
     */
    private val PREFIXES = listOf("pc on ", "pc for ", "pc of ", "pc ", "p c ", "price check on ", "price check for ",
                                  "price check of ", "price check ", "price of ", "how much is ", "how much are ",
                                  "how much r ", "how much for ", "how much do ", "how much does ", "whats ",
                                  "what is ", "wats ", "what are ", "what r ", "what do ", "what does ")

    /**
     * Words that can follow the item after one of the [PREFIXES].
     */
    private val SUFFIXES = listOf(" worth", " going for", " goes for", " go for", " sell for", " cost", " price",
                                  " pc", " plz", " pls", " please", " anyone", " any1", " ea", " each", " now")

    /**
     * Words that end a price check with no opening words: "d long price?".
     */
    private val ENDINGS = listOf(" price", " pc", " worth")

    /**
     * Openers before the question itself.
     */
    private val OPENERS = listOf("anyone know ", "any1 know ", "does anyone know ", "do u know ", "do you know ")

    /**
     * Words dropped from the start of an item name.
     */
    private val ARTICLES = setOf("a", "an", "the", "ur", "your", "my", "some", "one", "noted")

    /**
     * One of the words every price check contains, tested before anything else.
     */
    private val TRIGGER = Regex("""\b(pc|p c|price|how much|worth|go(ing|es)? for)\b""")

    /**
     * Runs of spaces.
     */
    private val SPACES = Regex(" {2,}")

    /**
     * A quantity, such as "100" or "1k".
     */
    private val QUANTITY = Regex("""(\d{1,6}(?:\.\d)?)([km]?)""")

    /**
     * Separators between several items in one question.
     */
    private val SEPARATORS = Regex(""" and | & | n |,""")

    /**
     * A price in a player's message, which means they answered.
     */
    private val PRICE = Regex("""\b\d+(\.\d+)?\s?(k|m|gp)\b|\b\d{3,}\b""")

    /**
     * When each player's next price check gets answered.
     */
    private var Player.nextPriceCheck by Attr.long()

    /**
     * When each bot may answer again.
     */
    private var Bot.nextPriceAnswer by Attr.long()

    /**
     * When each bot may thank someone again.
     */
    private var Bot.nextPriceThanks by Attr.long()

    /**
     * When each bot may ask a price check again.
     */
    private var Bot.nextPriceAsk by Attr.long()

    /**
     * Questions still open for replies.
     */
    private val open = ArrayList<Asked>()

    /**
     * When each market asks its next question.
     */
    private val nextAsk = EnumMap<BotMarketplace, Long>(BotMarketplace::class.java)

    /**
     * Rare items with no set price (`showoff.jsonc`). Bots answer "offer" until one has traded.
     */
    private val rares: Set<Int> by lazy {
        try {
            Files.newBufferedReader(Paths.get("data", "game", "bots", "items", "showoff.jsonc")).use {
                GsonUtils.GSON.fromJson(it, IntArray::class.java).toSet()
            }
        } catch (e: Exception) {
            logger.catching(e)
            emptySet()
        }
    }

    /**
     * The center and radius of the area searched for askers at each market.
     */
    private val marketBounds: Map<BotMarketplace, Pair<Position, Int>> by lazy {
        BotMarketplace.entries.associateWith { market ->
            val west = market.anchors.minOf { it.southWest.x }
            val south = market.anchors.minOf { it.southWest.y }
            val east = market.anchors.maxOf { it.northEast.x }
            val north = market.anchors.maxOf { it.northEast.y }
            Position((west + east) / 2, (south + north) / 2) to maxOf(east - west, north - south) / 2 + 1
        }
    }

    override fun handle(message: BotChatMessage): Boolean {
        val now = System.currentTimeMillis()
        val question = parse(message.text)
        if (question == null) {
            if (open.isNotEmpty()) {
                noteAnswer(message, now)
            }
            return false
        }
        val items = question.items.mapNotNull { name -> BotItemNames.resolve(name)?.let { pick(it) } }
            .filter { it != COINS }.distinct()
        if (items.isEmpty()) {
            return false
        }
        val asker = message.speaker
        // A bot asking inside a conversation gets its answer from its partner.
        if (now >= asker.nextPriceCheck && !(asker is Bot && BotConversations.engaged(asker))) {
            asker.nextPriceCheck = now + ASKER_COOLDOWN
            answer(message, question, items, now)
        }
        return true
    }

    /**
     * Reads a price check from a lowercase chat message.
     *
     * @param text The message.
     * @return The question, or `null` if the message isn't a price check.
     */
    fun parse(text: String): Question? {
        if (!TRIGGER.containsMatchIn(text)) {
            return null
        }
        var line = SPACES.replace(text.replace(':', ' '), " ").trim().trimEnd('?', '!', '.').trim()
        OPENERS.firstOrNull { line.startsWith(it) }?.let { line = line.substring(it.length) }
        val prefix = PREFIXES.firstOrNull { line.startsWith(it) }
        val body = if (prefix != null) trimSuffixes(line.substring(prefix.length))
        else ENDINGS.firstOrNull { line.endsWith(it) }?.let { line.dropLast(it.length).trim() } ?: return null

        val words = body.split(' ').filter { it.isNotEmpty() }.toMutableList()
        var quantity = 1
        if (words.size > 1) {
            val leading = QUANTITY.matchEntire(words.first())
            val trailing = if (leading == null && words.last().startsWith("x"))
                QUANTITY.matchEntire(words.last().drop(1)) else null
            val amount = leading ?: trailing
            if (amount != null) {
                val multiplier = when (amount.groupValues[2]) {
                    "k" -> 1_000
                    "m" -> 1_000_000
                    else -> 1
                }
                quantity = (amount.groupValues[1].toDouble() * multiplier).toLong().coerceIn(1, 1_000_000_000).toInt()
                words.removeAt(if (leading != null) 0 else words.lastIndex)
            }
        }
        val items = words.joinToString(" ").split(SEPARATORS).map { part ->
            part.trim().split(' ').dropWhile { it in ARTICLES }.joinToString(" ")
        }.filter { it.isNotBlank() }.take(MAX_ITEMS)
        return if (items.isEmpty()) null else Question(quantity, items)
    }

    /**
     * Removes trailing [SUFFIXES] from the words after the item: "d long worth pls" → "d long".
     */
    private fun trimSuffixes(text: String): String {
        var result = text
        while (true) {
            val suffix = SUFFIXES.firstOrNull { result.endsWith(it) } ?: return result
            result = result.dropLast(suffix.length).trim()
        }
    }

    /**
     * Asks one question at each market that is due. Runs on a short repeating timer.
     */
    fun askAtMarkets() {
        val now = System.currentTimeMillis()
        for (market in BotMarketplace.entries) {
            val due = nextAsk.getOrPut(market) { now + ASK_GAP.random() }
            if (now < due) {
                continue
            }
            nextAsk[market] = now + ASK_GAP.random()
            ask(market, now)
        }
    }

    /**
     * Has a waiting bot that a real player can see ask about one of its own items.
     */
    private fun ask(market: BotMarketplace, now: Long) {
        val (center, radius) = marketBounds.getValue(market)
        val askers = world.locator.findPlayers(center, radius) {
            it is Bot && now >= it.nextPriceAsk && market.contains(it.position) && it.localHumans.isNotEmpty() &&
                BotConversations.available(it)
        }.filterIsInstance<Bot>().shuffled()
        for (asker in askers) {
            val item = askable(asker) ?: continue
            val text = line(asker, PriceCheckSpeech.ASK, ItemNicknameDefinition.getNickname(item)) ?: return
            asker.speechStack.pushHead(BotSpeech(text, rand(1, 3)))
            asker.nextPriceAsk = now + ASKER_GAP.random()
            return
        }
    }

    /**
     * Picks an item worth asking about: one the bot carries or banks, or one it wants to buy.
     */
    private fun askable(bot: Bot): Int? {
        val owned = (bot.inventory.asSequence() + bot.bank.asSequence()).filterNotNull()
            .filter { !it.isDynamic && it.id != COINS }.map { it.itemDef.unnotedId.orElse(it.id) }
        val wanted = bot.preferences.wantedItems.asSequence().map { it.id() }
        return (owned + wanted).distinct()
            .filter { itemDef(it).isTradeable && (it !in rares || sampled(it)) && price(it) >= MIN_ASK_PRICE }
            .toList().randomOrNull()
    }

    /**
     * Schedules up to two bot answers to a question, and the asker's thanks if the asker is a bot.
     */
    private fun answer(message: BotChatMessage, question: Question, items: List<Int>, now: Long) {
        val asker = message.speaker
        val answerers = message.hearers.filter {
            now >= it.nextPriceAnswer && canSpeak(it) &&
                it.preferences.getFeelingsToward(asker.username) >= 0.25 && rand(answerChance(it))
        }.shuffled()
        val first = answerers.firstOrNull() ?: return
        val reply = (if (items.size == 1) quote(first, items.first(), question.quantity)
        else quoteAll(first, items)) ?: return

        open.removeIf { now > it.expires }
        val asked = Asked(asker, asker.position, now + QUESTION_LIFETIME)
        open += asked
        var ticks = replyTicks(first, message.text, reply.text)
        say(first, reply.text, ticks, asked, 0)
        first.nextPriceAnswer = now + ANSWER_COOLDOWN

        val second = answerers.getOrNull(1)
        if (second != null && reply.estimate != null && rand(SECOND_ANSWER_CHANCE)) {
            followUp(second, items.first(), reply)?.let {
                ticks += 1 + replyTicks(second, reply.text, it)
                say(second, it, ticks, asked, 1)
                second.nextPriceAnswer = now + ANSWER_COOLDOWN
            }
        }
        if (asker is Bot && rand(asker.personality.social * 0.6)) {
            line(asker, PriceCheckSpeech.THANKS)?.let {
                say(asker, it, ticks + 1 + replyTicks(asker, reply.text, it), asked, 1, true)
            }
        }
    }

    /**
     * A bot's answer about one item.
     */
    private fun quote(bot: Bot, id: Int, quantity: Int): Reply? {
        val name = ItemNicknameDefinition.getNickname(id)
        if (!itemDef(id).isTradeable) {
            return line(bot, PriceCheckSpeech.UNTRADEABLE, name)?.let { Reply(it, null) }
        }
        if (id in rares && !sampled(id)) {
            return line(bot, PriceCheckSpeech.RARE, name)?.let { Reply(it, null) }
        }
        val estimate = estimate(bot, price(id))
            ?: return line(bot, PriceCheckSpeech.NO_IDEA, name)?.let { Reply(it, null) }
        val personality = bot.personality
        val kind = if (personality.isUncertain || personality.isDumb && randBoolean()) PriceCheckSpeech.UNSURE
        else PriceCheckSpeech.ANSWER
        if (quantity == 1) {
            return line(bot, kind, name, priceText(bot, estimate))?.let { Reply(it, estimate) }
        }
        val scale = if (randBoolean()) quantity else 1
        val suffix = if (scale == 1) " ea" else ""
        return line(bot, kind, name, slang(bot, estimate * scale) + suffix)?.let { Reply(it, estimate, scale, suffix) }
    }

    /**
     * A bot's answer about several items: "d long 60k, d med 100k".
     */
    private fun quoteAll(bot: Bot, items: List<Int>): Reply? {
        val parts = items.filter { itemDef(it).isTradeable && (it !in rares || sampled(it)) }.mapNotNull { id ->
            estimate(bot, price(id))?.let { "${ItemNicknameDefinition.getNickname(id)} ${slang(bot, it)}" }
        }
        val text = parts.joinToString(", ")
        return if (parts.isEmpty() || text.length > 80) null else Reply(text, null)
    }

    /**
     * A second bot's reaction to the first answer, in the same terms (a total or a price each), or `null` if it has
     * nothing to add.
     */
    private fun followUp(bot: Bot, id: Int, first: Reply): String? {
        val previous = first.estimate ?: return null
        val own = estimate(bot, price(id)) ?: return null
        fun said(price: Double) = slang(bot, price * first.scale) + first.suffix
        return when {
            abs(own / previous - 1) > DISAGREE_GAP -> line(bot, PriceCheckSpeech.DISAGREE, price = said(own))
            randBoolean() -> line(bot, PriceCheckSpeech.AGREE, price = said(previous))
            else -> null
        }
    }

    /**
     * A bot's guess at [price]: clever bots are close, dumb bots guess wildly or don't know, and greedy bots lowball.
     *
     * @return The guess, or `null` if the bot has no idea.
     */
    private fun estimate(bot: Bot, price: Double): Double? {
        val personality = bot.personality
        var guess = price * when {
            personality.isIntelligent -> rand(0.95, 1.05)
            personality.isDumb -> if (rand(0.3)) return null else rand(0.4, 2.0)
            else -> rand(0.85, 1.15)
        }
        if (bot.emotions.isFeeling(EmotionType.GREEDY)) {
            guess *= rand(0.7, 0.9)
        }
        return guess.coerceAtLeast(1.0)
    }

    /**
     * The price of one item as said in chat: "60k", "60k ea", "180gp".
     */
    private fun priceText(bot: Bot, estimate: Double): String {
        var text = slang(bot, estimate)
        if (estimate < 1_000 && rand(0.3)) {
            text += "gp"
        }
        return when (rand(0, 9)) {
            in 0..3 -> "$text ea"
            4 -> "$text each"
            else -> text
        }
    }

    /**
     * Rounds a price the way players typed it: "57k", "1.2m", "180". Dumb bots round further.
     */
    private fun slang(bot: Bot, price: Double): String {
        val figures = if (bot.personality.isDumb) 1 else 2
        val scale = 10.0.pow(floor(log10(price.coerceAtLeast(1.0))) + 1 - figures).coerceAtLeast(1.0)
        val rounded = (price / scale).roundToLong() * scale
        return when {
            rounded < 1_000 -> "${rounded.toLong()}"
            rounded < 1_000_000 -> "${decimal(rounded / 1_000)}k"
            else -> "${decimal(rounded / 1_000_000)}m"
        }
    }

    /**
     * Writes [value] with at most one decimal place.
     */
    private fun decimal(value: Double): String {
        val tenths = (value * 10).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
    }

    /**
     * Picks a price check line and fills in its item and price.
     */
    private fun line(bot: Bot, kind: PriceCheckSpeech, item: String = "", price: String = ""): String? =
        BotPriceCheckSpeechPool.take(bot, kind)?.replace("<item>", item)?.replace("<price>", price)

    /**
     * Queues a reply for [ticks] from now, checking again before it goes out.
     *
     * @param after How many bot replies must have gone out first.
     * @param thanks Whether this is the asker thanking. A player's answer doesn't cancel thanks, but bots thank at most
     * once every 20 seconds.
     */
    private fun say(bot: Bot, text: String, ticks: Int, asked: Asked, after: Int, thanks: Boolean = false) {
        world.scheduleOnce(ticks) {
            val now = System.currentTimeMillis()
            val cancelled = if (thanks) now < bot.nextPriceThanks else asked.answeredByPlayer
            val heard = asked.replies >= after || thanks && asked.answeredByPlayer
            if (!cancelled && heard && canSpeak(bot) && asked.asker.state == EntityState.ACTIVE &&
                bot.isWithinDistance(asked.asker, Position.VIEWING_DISTANCE)) {
                bot.speechStack.pushHead(BotSpeech(text, 0))
                if (thanks) bot.nextPriceThanks = now + THANKS_COOLDOWN else asked.replies++
            }
        }
    }

    /**
     * Marks open questions near a real player's message as answered when the message names a price.
     */
    private fun noteAnswer(message: BotChatMessage, now: Long) {
        open.removeIf { now > it.expires }
        val speaker = message.speaker
        if (speaker is Bot || open.isEmpty() || !PRICE.containsMatchIn(message.text)) {
            return
        }
        for (asked in open) {
            if (asked.asker !== speaker &&
                speaker.position.isWithinDistance(asked.position, Position.VIEWING_DISTANCE)) {
                asked.answeredByPlayer = true
            }
        }
    }

    /**
     * How likely a bot is to answer: social bots and bots hanging around answer more, busy bots less.
     */
    private fun answerChance(bot: Bot): Double {
        var chance = 0.2 + 0.6 * bot.personality.social
        if (bot.scriptStack.current() is SocialBotScript) {
            chance *= 1.25
        }
        if (!bot.walking.isEmpty || bot.actions.size(ActionType.WEAK) > 0) {
            chance *= 0.5
        }
        return chance.coerceAtMost(0.95)
    }

    /**
     * Whether a bot is free to talk: not fighting, trading, in a conversation or in the Wilderness.
     */
    private fun canSpeak(bot: Bot): Boolean = bot.state == EntityState.ACTIVE &&
        ActionSafety.NON_COMBAT.allows(bot) && bot.combat.target == null && bot.reflex.activeReflex == null &&
        bot.tradingWith == -1 && !bot.speechStack.isAllDisabled && !bot.inWilderness() &&
        !BotConversations.engaged(bot) && !BotSocialInteractions.engaged(bot)

    /**
     * Ticks before a reply: noticing the message, reading it and typing the reply at the bot's own speed.
     */
    private fun replyTicks(bot: Bot, heard: String, typed: String): Int {
        val notice = exp(ln(1.2) + 0.5 * rand().nextGaussian()).coerceAtMost(8.0)
        val read = (heard.count { it == ' ' } + 1) / 4.0
        val typing = typed.length / (2.2 + 4.3 * bot.personality.dexterity)
        return ceil((notice + read + typing) / TICK_SECONDS).toInt().coerceIn(2, 25)
    }

    /**
     * The item meant when a nickname names several: the most valuable.
     */
    private fun pick(ids: IntArray): Int = if (ids.size == 1) ids[0] else ids.maxBy { price(it) }

    /**
     * The economy's price for an item, read without creating a price entry for it.
     */
    private fun price(id: Int): Double =
        world.economy.prices[id]?.price ?: ItemPriceData.computeInitialPrice(id).toDouble()

    /**
     * Whether the economy has seen this item trade.
     */
    private fun sampled(id: Int): Boolean = (world.economy.prices[id]?.samples ?: 0) > 0
}
