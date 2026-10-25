package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.trading.BotTradeRequestStage
import api.bot.action.trading.BotTradeStage
import api.bot.script.BotScript
import api.predef.ext.*
import api.predef.rand
import api.predef.world
import engine.bot.speech.BotReactions.lastScammedBy
import engine.bot.speech.BotScamSpeechPool
import engine.bot.speech.BotScamSpeechPool.ScamSpeech
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Opt-in, bot-only trust trades following a delivered scam pitch. Normal merchant valuation is never weakened.
 * Both participants retain their current scripts and drive their own native request, offer and confirmation stages.
 * Runtime claims expire on interruption; completed native exchanges alone produce loss memories and reactions.
 *
 * @author lare96
 */
object BotTradeScams {
    /**
     * Promised service; neither variant creates a return item or conjures coins.
     *
     * @author lare96
     */
    enum class Kind(val pitch: ScamSpeech, val aftermath: ScamSpeech, val advert: ScamSpeech) {
        DOUBLING(ScamSpeech.DOUBLING_PITCH, ScamSpeech.DOUBLING_SUCCESS, ScamSpeech.DOUBLING_ADVERT),
        TRIMMING(ScamSpeech.TRIMMING_PITCH, ScamSpeech.TRIMMING_SUCCESS, ScamSpeech.TRIMMING_ADVERT)
    }

    /**
     * Runtime recruitment cooldown; shared interaction ownership is held by BotSocialInteractions.
     */
    private var Bot.nextScam by Attr.long()

    /**
     * Starts an authored pitch only when both current scripts explicitly expose social availability.
     * Persistent negative feelings block repeat recruitment even after logout resets runtime cooldowns.
     */
    fun approach(actor: Bot, victim: Bot, kind: Kind): Boolean {
        val now = System.currentTimeMillis()
        if (!actor.preferences.isScammer || actor === victim ||
            now < actor.nextScam || now < victim.nextScam ||
            victim.preferences.getFeelingsToward(actor.username) < 0.4 ||
            !BotConversations.available(actor) || !BotConversations.available(victim) ||
            basket(victim, kind).isEmpty()) return false
        val lines = BotScamSpeechPool.exchange(kind.pitch) ?: return false
        val pitch = BotConversations.start(actor, victim, lines) ?: return false
        val session = Session(actor, victim, kind, pitch)
        if (!BotSocialInteractions.claim(listOf(actor, victim), session)) {
            pitch.close()
            return false
        }
        actor.nextScam = now + 60_000
        victim.nextScam = now + 10 * 60_000
        return true
    }

    /**
     * Intelligence controls carried risk, reaching the full eligible carried amount at the lowest intelligence.
     * A bank balance is never part of this calculation.
     */
    internal fun riskFraction(intelligence: Double): Double =
        (1.0 - 0.9 * intelligence.coerceIn(0.0, 1.0)).coerceIn(0.1, 1.0)

    /**
     * Intelligence and existing trust influence consent independently of the amount that would be offered.
     */
    internal fun consentChance(intelligence: Double, feeling: Double): Double =
        ((0.9 - 0.85 * intelligence.coerceIn(0.0, 1.0)) * feeling.coerceIn(0.0, 1.0) * 2).coerceIn(0.0, 0.95)

    /**
     * Selects native tradeable, non-dynamic inventory coins or ordinary untrimmed metal armour.
     * Equipped armour can be selected, but must subsequently be unequipped through the equipment action handler.
     */
    internal fun basket(victim: Bot, kind: Kind): Map<Int, Int> {
        val fraction = riskFraction(victim.personality.intelligence)
        if (kind == Kind.DOUBLING) {
            val amount = victim.inventory.filterNotNull().filter { it.id == 995 && !it.isDynamic }
                .sumOf { it.amount.toLong() }
            val risk = (amount * fraction).toLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            return if (risk > 0) mapOf(995 to risk) else emptyMap()
        }
        val armour = (victim.inventory.filterNotNull() + victim.equipment.filterNotNull()).filter { item ->
            val name = item.itemDef.name.lowercase()
            !item.isDynamic && item.itemDef.isTradeable && item.itemDef.unnotedId.isEmpty &&
                listOf("bronze", "iron", "steel", "black", "mithril", "adamant", "rune").any { metal ->
                    name in listOf("$metal full helm", "$metal platebody", "$metal platelegs", "$metal plateskirt", "$metal kiteshield")
                }
        }
        val values = armour.associate { it.id to world.economy.getTotalPrice(Item(it.id)).coerceAtLeast(0) }
        val budget = (armour.sumOf { values.getValue(it.id).toDouble() * it.amount } * fraction).toLong()
        var remaining = budget
        val result = linkedMapOf<Int, Int>()
        for (item in armour.sortedBy { values.getValue(it.id) }) {
            val price = values.getValue(item.id)
            if (price <= 0) continue
            val count = minOf(item.amount.toLong(), remaining / price).toInt()
            if (count > 0) {
                result[item.id] = (result[item.id] ?: 0) + count
                remaining -= price * count
            }
        }
        return result
    }

    /**
     * Game-thread-owned paired state. Captured scripts and screens prevent a resumed or replacement activity from
     * accepting an old trust trade. The native confirmation objects are the only authority for a completed loss.
     *
     * @author lare96
     */
    private class Session(val actor: Bot, val victim: Bot, val kind: Kind, val pitch: BotConversations.Conversation) : BotSocialInteractions.Session {
        private val bots = listOf(actor, victim)
        private val scripts = bots.map { it.scriptStack.current() }
        private val deadline = System.currentTimeMillis() + 8 * 60_000
        private val started = mutableSetOf<Bot>()
        private val finished = mutableSetOf<Bot>()
        private val confirms = mutableMapOf<Bot, ConfirmTradeInterface>()
        private var stock = emptyMap<Int, Int>()
        private var prepared = false
        private var recorded = false
        var trading = false
            private set
        var ended = false
            private set

        override fun valid(): Boolean = !ended && System.currentTimeMillis() < deadline &&
            actor.isWithinDistance(victim, 8) && bots.indices.all { index ->
                val bot = bots[index]
                bot.state == EntityState.ACTIVE && bot.scriptStack.current() === scripts[index] &&
                    scripts[index]?.isRunning() == true && BotScript.ActionSafety.NON_COMBAT.allows(bot) &&
                    bot.reflex.activeReflex == null && bot.combat.target == null &&
                    !bot.ignores.contains(bots[1 - index].usernameHash)
            }

        override suspend fun participate(bot: Bot) {
            if (!trading) {
                pitch.tick()
                if (pitch.ended) {
                    if (!pitch.completed) close() else decide()
                }
            }
            if (trading && !ended) trade(bot)
        }

        /**
         * Makes one independent victim decision only after every pitch line has been delivered.
         */
        fun decide() {
            if (trading || ended) return
            if (!rand(consentChance(victim.personality.intelligence,
                    victim.preferences.getFeelingsToward(actor.username)))) {
                close()
                dialogue(ScamSpeech.REFUSAL)
                return
            }
            stock = basket(victim, kind)
            if (stock.isEmpty()) close() else trading = true
        }

        /**
         * Performs one side of an exact one-way exchange. Partial offers and changed baskets never reach confirmation.
         */
        suspend fun trade(bot: Bot) {
            if (!started.add(bot)) return
            var success = false
            try {
                success = withTimeoutOrNull(180_000) {
                    if (bot === victim) {
                        for ((index, item) in victim.equipment.withIndex()) {
                            if (!valid()) return@withTimeoutOrNull false
                            if (item != null && (stock[item.id] ?: 0) > victim.inventory.computeNonDynamicAmountForId(item.id) &&
                                !victim.actionHandler.equipment.unequip(index))
                                return@withTimeoutOrNull false
                        }
                        if (!stock.all { (id, count) -> victim.inventory.computeNonDynamicAmountForId(id) >= count })
                            return@withTimeoutOrNull false
                        prepared = true
                    } else if (!waitFor(30.seconds) { prepared || !valid() } || !prepared) return@withTimeoutOrNull false
                    if (!valid()) return@withTimeoutOrNull false
                    val other = if (bot === actor) victim else actor
                    val stage = BotTradeRequestStage(bot, other).sendAndAwait() ?: return@withTimeoutOrNull false
                    if (!valid()) return@withTimeoutOrNull false
                    if (bot === victim && !stage.offerAll(stock.map { Item(it.key, it.value) })) return@withTimeoutOrNull false
                    val screen = bot.overlays[OfferTradeInterface::class] ?: return@withTimeoutOrNull false
                    val otherScreen = other.overlays[OfferTradeInterface::class] ?: return@withTimeoutOrNull false
                    if (screen.other !== other || otherScreen.other !== bot) return@withTimeoutOrNull false
                    val expectedOwn = if (bot === victim) stock else emptyMap()
                    val expectedOther = if (bot === actor) stock else emptyMap()
                    fun matches(): Boolean = valid() && amounts(screen.items.filterNotNull()) == expectedOwn &&
                        amounts(otherScreen.items.filterNotNull()) == expectedOther
                    if (!waitFor(45.seconds) { !valid() || matches() } || !matches()) return@withTimeoutOrNull false
                    val confirmStage = stage.offerAndAwait { matches() } ?: return@withTimeoutOrNull false
                    val confirm = bot.overlays[ConfirmTradeInterface::class] ?: return@withTimeoutOrNull false
                    val otherConfirm = other.overlays[ConfirmTradeInterface::class] ?: return@withTimeoutOrNull false
                    if (confirm.offer !== screen || otherConfirm.offer !== otherScreen || !matches())
                        return@withTimeoutOrNull false
                    confirms[bot] = confirm
                    confirmStage.confirmAndAwait()
                } == true
            } finally {
                recordCompletion()
                finished += bot
                if (!success || finished.size == 2) {
                    close()
                    if (recorded) dialogue(kind.aftermath)
                }
            }
        }

        /**
         * Observes both original native confirmations once, including completion immediately before cancellation.
         */
        private fun recordCompletion() {
            if (recorded || confirms.size != 2 || !confirms.values.all { it.completed }) return
            recorded = true
            victim.lastScammedBy = actor.username
            victim.preferences.adjustFeelingsToward(actor.username, -0.5)
            victim.preferences.rebalanceWantedItems()
            actor.preferences.rebalanceWantedItems()
            scripts.forEach { (it as? SocialBotScript)?.socialTradeCompleted() }
        }

        override fun close() {
            if (ended) return
            recordCompletion()
            ended = true
            pitch.close()
            bots.forEach { bot ->
                val other = if (bot === actor) victim else actor
                if (BotSocialInteractions.owns(bot, this)) {
                    BotTradeStage(bot, other).decline()
                    if (bot.tradingWith == other.index) bot.tradingWith = -1
                }
            }
            BotSocialInteractions.release(bots, this)
        }

        /**
         * Starts feedback only if the same two bots remain independently available after the claim is released.
         */
        private fun dialogue(context: ScamSpeech) {
            BotScamSpeechPool.exchange(context)?.let { BotConversations.start(actor, victim, it) }
        }

        /**
         * Compares exact carried ids and quantities; notes and unnoted items cannot silently substitute for each other.
         */
        private fun amounts(items: List<Item>): Map<Int, Int> = items.groupBy { it.id }.mapValues { (_, copies) ->
            copies.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}
