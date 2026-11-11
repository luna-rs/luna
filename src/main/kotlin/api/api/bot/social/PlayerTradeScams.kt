package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.trading.BotTradeRequestStage
import api.bot.action.trading.PlayerTradeAdvertisementMessages
import api.bot.isRealBot
import api.bot.script.BotScript
import api.predef.ext.*
import api.predef.rand
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import engine.trade.tradingWith
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Rare, plainly misrepresented sales from scammer accounts to nearby human players. Every offered item comes from
 * carried stock, and both native trade screens show its real identity. Ordinary merchant valuation is unchanged.
 * A completed native settlement alone marks a successful scam. An explicit player decline receives an admission
 * only after the bot's substitute items are visible in the offer.
 *
 * @author lare96
 */
object PlayerTradeScams {
    internal const val ATTEMPT_CHANCE = 0.025
    internal const val COOLDOWN_MILLIS = 30 * 60_000L
    private const val MAXIMUM_COINS = 100_000L
    private var Player.nextScamOffer by Attr.long()

    /**
     * A finite sale of a real item advertised under another item's name.
     *
     * @author lare96
     */
    internal data class Offer(val actualId: Int, val claimedId: Int, val quantity: Int,
                              val coins: Int, val description: String)

    /**
     * Selects small carried batches and a discounted asking price from the claimed commodity's guide.
     * Unidentified low herbs use the clean torstol guide because their native name does not identify the species.
     */
    internal fun offers(stock: Map<Int, Int>, notedId: (Int) -> Int = { -1 },
                        guide: (Int) -> Long): List<Offer> {
        val result = ArrayList<Offer>()
        fun add(actual: Int, claimed: Int, description: String, maximum: Int, priceId: Int = claimed) {
            val available = stock[actual] ?: return
            if (available < 1) return
            val value = guide(priceId)
            if (value < 1L) return
            val unit = (value / 5 * 4 + value % 5 * 4 / 5).coerceAtLeast(1L)
            val quantity = minOf(available.toLong(), maximum.toLong(), MAXIMUM_COINS / unit).toInt()
            if (quantity > 0) result += Offer(actual, claimed, quantity, (unit * quantity).toInt(), description)
        }
        fun herb(actual: Int, claimed: Int, description: String) {
            add(actual, claimed, description, 14, 269)
            val actualNote = notedId(actual)
            val claimedNote = notedId(claimed)
            if (actualNote > 0 && claimedNote > 0 && actualNote != actual && actualNote != claimedNote)
                add(actualNote, claimedNote, "$description notes", 14, 269)
        }
        for (id in listOf(249, 251, 253, 255)) herb(id, 269, "torstol")
        for (id in listOf(199, 201, 203, 205)) herb(id, 219, "unidentified torstol")
        for (id in listOf(558, 559)) {
            add(id, 565, "blood runes", 500)
            add(id, 560, "death runes", 500)
        }
        return result
    }

    /**
     * Considers one nearby human without reserving their activity, taking their items, or changing their privacy.
     */
    fun approach(actor: Bot, victim: Player): Boolean =
        approach(actor, victim, System.currentTimeMillis(), rand(ATTEMPT_CHANCE))

    internal fun approach(actor: Bot, victim: Player, now: Long, attempt: Boolean): Boolean {
        if (!attempt || !actor.isRealBot() || victim.isRealBot() || !actor.preferences.isScammer ||
            actor === victim || now < actor.nextScamOffer || now < victim.nextScamOffer ||
            !BotConversations.available(actor) || !ready(actor, victim) ||
            actor.overlays.hasWindow() || victim.overlays.hasWindow() ||
            actor.tradingWith != -1 || victim.tradingWith != -1) return false
        val stock = amounts(actor.inventory.filterNotNull().filter { !it.isDynamic && it.itemDef.isTradeable })
        val offer = offers(stock, notedId = { id ->
            val note = ItemDefinition.ALL.get(id).orElse(null)?.notedId?.orElse(-1) ?: -1
            val definition = if (note >= 0) ItemDefinition.ALL.get(note).orElse(null) else null
            if (definition != null && definition.unnotedId.orElse(-1) == id &&
                definition.isTradeable && definition.isStackable) note else -1
        }) { actor.world.economy.getPrice(it) }.randomOrNull() ?: return false
        val session = Session(actor, victim, offer)
        if (!BotSocialInteractions.claim(listOf(actor), session)) return false
        if (!PlayerTradeAdvertisementMessages.counteroffer(actor, victim,
                "Selling ${offer.quantity} ${offer.description} for ${offer.coins} gp.")) {
            session.close()
            return false
        }
        actor.nextScamOffer = now + COOLDOWN_MILLIS
        victim.nextScamOffer = now + COOLDOWN_MILLIS
        return true
    }

    private fun currentPlayers(actor: Bot, victim: Player): Boolean =
        actor.state == EntityState.ACTIVE && victim.state == EntityState.ACTIVE && actor.world === victim.world &&
            actor.world.getPlayer(actor.usernameHash).orElse(null) === actor &&
            actor.world.getPlayer(victim.usernameHash).orElse(null) === victim

    private fun ready(actor: Bot, victim: Player): Boolean = currentPlayers(actor, victim) &&
        actor.isWithinDistance(victim, 8) && actor.isViewableFrom(victim) && victim.isViewableFrom(actor) &&
        BotScript.ActionSafety.NON_COMBAT.allows(actor) && actor.reflex.activeReflex == null &&
        actor.combat.target == null && victim.health > 0 && !victim.isLocked && !victim.combat.inCombat() &&
        victim.combat.target == null && victim.actions.size(ActionType.STRONG) == 0 &&
        PlayerTradeAdvertisementMessages.allowsTrade(actor, victim) &&
        actor.usernameHash !in victim.ignores && victim.usernameHash !in actor.ignores

    private fun amounts(items: List<Item>): Map<Int, Int> = items.groupBy { it.id }.mapValues { (_, copies) ->
        copies.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * A single bot's bounded native trade. Screen identities, exact quantities and revisions prevent a replacement
     * trade or later edit from inheriting an earlier acceptance. Cleanup closes only this session's own screens.
     *
     * @author lare96
     */
    private class Session(val actor: Bot, val victim: Player, val sale: Offer) : BotSocialInteractions.Session {
        private val script = actor.scriptStack.current()
        private val deadline = System.currentTimeMillis() + 120_000L
        private val ownBasket = mapOf(sale.actualId to sale.quantity)
        private val payment = mapOf(995 to sale.coins)
        private var ownScreen: OfferTradeInterface? = null
        private var otherScreen: OfferTradeInterface? = null
        private var started = false
        private var requestSubmitted = false
        private var ended = false
        private var declined = false
        private var completed = false

        override fun valid(): Boolean = !ended && !declined && System.currentTimeMillis() < deadline &&
            actor.isRealBot() && !victim.isRealBot() && actor.preferences.isScammer &&
            actor.scriptStack.current() === script && script?.isRunning() == true &&
            BotSocialInteractions.owns(actor, this) && ready(actor, victim)

        override suspend fun participate(bot: Bot) {
            if (bot !== actor || started) return
            started = true
            try {
                withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1L)) {
                    val stage = BotTradeRequestStage(actor, victim).sendAndAwait(beforeRequest = {
                        valid().also { if (it) requestSubmitted = true }
                    }, canContinue = { valid() }) ?: return@withTimeoutOrNull
                    if (!valid()) return@withTimeoutOrNull
                    val mine = actor.overlays[OfferTradeInterface::class] ?: return@withTimeoutOrNull
                    val theirs = victim.overlays[OfferTradeInterface::class] ?: return@withTimeoutOrNull
                    if (mine.other !== victim || theirs.other !== actor) return@withTimeoutOrNull
                    ownScreen = mine
                    otherScreen = theirs
                    mine.samplesMarketPrices = false
                    mine.onDecline { who ->
                        if (who === victim && !ended && !completed && !declined && currentPlayers(actor, victim)) {
                            declined = true
                            if (hasOfferedItems()) {
                                PlayerTradeAdvertisementMessages.counteroffer(actor, victim, "Fine, you caught me. Nice spot.")
                            }
                        }
                    }
                    if (!stage.offer(Item(sale.actualId, sale.quantity))) return@withTimeoutOrNull
                    if (!waitFor(60.seconds) { !valid() || !ownsScreens() || matches() } || !matches())
                        return@withTimeoutOrNull
                    val ownRevision = mine.revision
                    val otherRevision = theirs.revision
                    fun unchanged() = matches() && mine.revision == ownRevision && theirs.revision == otherRevision &&
                        actor.inventory.hasSpaceForAll(listOf(Item(995, sale.coins))) &&
                        victim.inventory.hasSpaceForAll(listOf(Item(sale.actualId, sale.quantity)))
                    mine.guardNativeTrade(validate = { unchanged() }, settle = {
                        if (!completed) {
                            completed = true
                        }
                    })
                    val confirmation = stage.offerAndAwait { unchanged() } ?: return@withTimeoutOrNull
                    val confirm = actor.overlays[ConfirmTradeInterface::class] ?: return@withTimeoutOrNull
                    val otherConfirm = victim.overlays[ConfirmTradeInterface::class] ?: return@withTimeoutOrNull
                    if (confirm.offer !== mine || otherConfirm.offer !== theirs || !unchanged()) return@withTimeoutOrNull
                    confirmation.confirmAndAwait()
                }
            } finally {
                close()
            }
        }

        private fun ownsScreens(): Boolean {
            val mine = ownScreen ?: return false
            val theirs = otherScreen ?: return false
            return (actor.overlays[OfferTradeInterface::class] === mine ||
                    actor.overlays[ConfirmTradeInterface::class]?.offer === mine) &&
                (victim.overlays[OfferTradeInterface::class] === theirs ||
                    victim.overlays[ConfirmTradeInterface::class]?.offer === theirs)
        }

        private fun matches(): Boolean = valid() && ownsScreens() &&
            amounts(ownScreen!!.items.filterNotNull()) == ownBasket &&
            amounts(otherScreen!!.items.filterNotNull()) == payment

        /**
         * Observes native placement before a decline closes the offer, even before the offering coroutine resumes.
         */
        private fun hasOfferedItems(): Boolean =
            ownsScreens() && ownScreen!!.items.computeNonDynamicAmountForId(sale.actualId) > 0

        override fun close() {
            if (ended) return
            ended = true
            if (BotSocialInteractions.owns(actor, this)) {
                val mine = ownScreen ?: if (requestSubmitted) {
                    val pending = actor.overlays[OfferTradeInterface::class]
                        ?: actor.overlays[ConfirmTradeInterface::class]?.offer
                    val opposite = victim.overlays[OfferTradeInterface::class]
                        ?: victim.overlays[ConfirmTradeInterface::class]?.offer
                    pending?.takeIf { it.other === victim && opposite?.other === actor }
                } else null
                if (mine != null && (actor.overlays[OfferTradeInterface::class] === mine ||
                        actor.overlays[ConfirmTradeInterface::class]?.offer === mine)) actor.overlays.closeWindows()
                if (!actor.overlays.hasWindow() && actor.tradingWith == victim.index) actor.tradingWith = -1
                if (!victim.overlays.hasWindow() && victim.tradingWith == actor.index) victim.tradingWith = -1
            }
            BotSocialInteractions.release(listOf(actor), this)
        }
    }
}
