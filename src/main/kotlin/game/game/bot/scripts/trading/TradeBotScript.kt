package game.bot.scripts.trading

import api.bot.Suspendable
import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeItem
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradeManager.tradeRequests
import api.bot.action.trading.BotTradeMessage
import api.bot.action.trading.BotTradeOfferStage
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.BotTradeRequestStage
import api.bot.action.trading.BotTradeScrutiny
import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import engine.trade.OfferTradeInterface
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.util.RandomUtils
import kotlinx.coroutines.future.await
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class TradeBotScript(bot: Bot,
                     private val purpose: BotTradePurpose,
                     private val marketplace: BotMarketplace = BotMarketplace.VARROCK,
                     duration: Duration = getPreferenceBasedDuration(bot),
                     zone: MutableList<SubZone> = mutableListOf(SubZone.HOME)) :
    ZonedBotScript(bot, duration, zone) {

    /*
     *
     * <h2>Scamming Behaviour</h2>
     *
     * Both buying and selling bots can scam, including during bot-to-bot and bot-to-player trades. Two bots can
     * potentially attempt to scam each other in the same trade.
     *
     * <h3>Buyer Scamming</h3>
     * <ul>
     *     <li>Offer the agreed amount of coins.</li>
     *     <li>Wait until the other participant clicks Accept.</li>
     *     <li>Remove 5-25% of the offered coins.</li>
     *     <li>Attempt to complete the trade at the reduced price.</li>
     * </ul>
     *
     * TODO Test whether removing offered items or coins after acceptance resets the acceptance state.
     * TODO Determine whether both participants must click Accept again after the offer changes.
     * TODO Ensure scamming behaviour accounts for the actual trade-interface mechanics.
     *
     * <h3>Seller Scamming</h3>
     * <ul>
     *     <li>Greedy and scammer-type bots have a high chance of offering an incorrect item quantity.</li>
     *     <li>This behaviour should be especially common during high-volume trades.</li>
     *     <li>Scam quantities should generally deviate by 5-25% from the agreed amount.</li>
     *     <li>Scams can target both bots and human players.</li>
     * </ul>
     *
     * <h3>Scam Detection</h3>
     * <ul>
     *     <li>Only sufficiently intelligent bots should notice incorrect trade quantities or values.</li>
     *     <li>Detection probability should depend on intelligence.</li>
     *     <li>Bots that detect a scam should react immediately afterward using the appropriate speech pool.</li>
     *     <li>Less intelligent bots may fail to notice that they were scammed.</li>
     * </ul>
     *
     * TODO Define intelligence-based scam detection probabilities.
     * TODO Add post-trade scam reaction speech pools.
     *
     * <h2>Equipment Validation</h2>
     *
     * TODO Verify that the H.A.M. hood equips correctly.
     * TODO Verify that the H.A.M. skirt equips correctly.
     * TODO Test the entire H.A.M. equipment set for correct slot assignments.
     *
     * <h2>Outstanding Design Decisions</h2>
     * <ul>
     *     <li>Determine how trade acceptance resets when an offer changes.</li>
     *     <li>Define scamming probability based on intelligence, greed, and scammer preferences.</li>
     *     <li>Define intelligence-based scam detection probabilities.</li>
     *     <li>Determine valuation bonuses for wanted items in equivalent-value trades.</li>
     *     <li>Define acceptable profit and loss thresholds for merchant bots.</li>
     *     <li>Determine item-specific wanted-quantity scaling rules.</li>
     *     <li>Decide how wanted-quantity recalculation interacts with bot tracking.</li>
     *     <li>Determine how selling candidates are selected from ownership and wanted-item data.</li>
     *     <li>Define general-store fallback conditions and inventory decay mechanics.</li>
     * </ul>
     *
     * <h2>Testing Requirements</h2>
     *
     * TODO Test both participants attempting to scam each other simultaneously.
     * TODO Test scam detection and post-trade reactions.
     * TODO Test high-volume trades and incorrect quantities.
     * TODO Test offer changes after acceptance.
     * TODO Test insufficient and excessive buyer payments.
     * TODO Test wanted-item updates after item acquisition and loss.
     * TODO Verify the entire H.A.M. equipment set.
     * TODO Don't withdraw dynamic items.
     */

    companion object {
        private fun getPreferenceBasedDuration(bot: Bot): Duration {
            return when {
                bot.preferences.hatesActivity(BotActivity.MERCHANTING) -> rand(30, 60).minutes
                bot.preferences.likesActivity(BotActivity.MERCHANTING) -> rand(1, 3).hours
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> rand(2, 4).hours
                else -> rand(45, 120).minutes
            }
        }
    }

    private val blackList = HashMap<String, Instant>()
    private val searchTimer = Stopwatch.createUnstarted()
    private val messageTimer = Stopwatch.createUnstarted()
    private var offering: List<BotTradeItem> = listOf()
    private var expected: List<BotTradeItem> = listOf()
    private var traded = false
    private var message: BotTradeMessage? = null
    private var messageCooldown = 0

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (!handler.travelTo(SubZone.HOME)) {
            // Could not travel to ::home.
            return false
        }

        // We don't want to trade with hated players.
        for (playerFeelings in bot.preferences.playerFeelings.entries) {
            if (playerFeelings.value < 0.25) {
                addBlacklisted(playerFeelings.key, 365.days)
            }
        }

        // Resolve the items we're offering and the value we're expecting.
        if (!computeOfferAndExpected(false)) {
            return false
        }

        // Prepare bot for trading.
        bot.speechStack.setDisableAll(true)
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        handler.banking.clickBankingMode(true)

        // Withdraw all our items we're offering.
        var success = false
        for (item in offering) {
            if (bot.inventory.contains(item.item) || handler.banking.withdraw(item.item)) {
                success = true
            }
        }
        if (!success) {
            stop()
            bot.log("No more items to withdraw, ending script.")
            return
        }

        // Return back to our trading location.
        bot.navigator.navigate(BotMarketplace.VARROCK.randomPosition(), true).await()
    }

    override suspend fun finish() {
        bot.speechStack.setDisableAll(false)
        handler.banking.clickBankingMode(false)
    }

    override suspend fun completed() {
        if (!traded) {
            // We didn't trade anything, queue shop buying/selling.
            when (purpose) {
                BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY -> bot.scriptStack.push(BuyFromStoreBotScript(bot))
                BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL -> bot.scriptStack.push(SellToStoreBotScript(bot))
            }
        }
    }

    override suspend fun executeInZone(): Boolean {
        val tradeItem =
            if (purpose == BotTradePurpose.BUY && expected.isNotEmpty()) expected.first()
            else if (purpose == BotTradePurpose.SELL && offering.isNotEmpty()) offering.first() else null
        if (!listenForRequests(tradeItem)) {
            sendNearbyRequests(tradeItem)
        }
        sendMessage(tradeItem)
        return true
    }

    override fun snapshot(): BotScriptData? {
        return null
    }

    private suspend fun listenForRequests(tradeItem: BotTradeItem?): Boolean {
        // First check if any incoming trade requests are acceptable.
        val acceptedRequested = bot.tradeRequests.pollRequests { canTradeWith(it) }
        if (tradeItem == null && purpose != BotTradePurpose.MERCHANT_SELL && purpose != BotTradePurpose.MERCHANT_BUY) {
            bot.log("Trade item is <null>, purpose must be <MERCHANT_BUY> or <MERCHANT_SELL>.")
            computeOfferAndExpected(true)
            return false
        } else if (tradeItem != null && tradeItem.item !in bot.inventory) {
            if (tradeItem.item in bot.bank) {
                bot.log("We don't have the trade item, but it's in our bank. Forcing banking trip...")
                forceBanking = true
            } else {
                bot.log("We don't have the trade item at all. Rebalancing trade intentions...")
                computeOfferAndExpected(true)
            }
            return false
        }

        // Valid request found, attempt a trade.
        if (acceptedRequested != null && startTradeWith(acceptedRequested, tradeItem)) {
            forceBanking = true
            return true
        }
        return false
    }

    private suspend fun sendNearbyRequests(tradeItem: BotTradeItem?) {
        // Then check if we can send any trade requests to bots in the area. Throttled to every 5-10 seconds.
        if (!searchTimer.isRunning || searchTimer.elapsed().toSeconds() > rand(5, 10)) {
            val localValid =
                world.locator.findPlayers(bot, Position.VIEWING_DISTANCE * 2) { it is Bot && canTradeWith(it) }
                    .map { it as Bot }
            for (local in localValid) {
                if (bot.isViewableFrom(local) && startTradeWith(local, tradeItem)) {
                    forceBanking = true
                    return
                }
                // If trade attempt fails, add bot to blacklist temporarily so we don't repeatedly attempt trades
                // on the same bot.
                val end = Instant.now().plus(5, ChronoUnit.MINUTES)
                blackList[local.username] = end
                bot.log("Blacklisted ${local.username} until $end.")
            }
            searchTimer.reset().start()
        }
    }

    private fun sendMessage(item: BotTradeItem?) {
        val message = message
        if (message != null && messageCooldown > 0) {
            if (!messageTimer.isRunning || messageTimer.elapsed(TimeUnit.SECONDS) >= messageCooldown) {
                bot.output.chat(message.text, message.color, message.effect)
                messageTimer.reset().start()
            }
        } else {
            val cooldownSeconds = when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> BotTradeManager.STRONG_MERCHANT_COOLDOWN
                bot.preferences.likesActivity(BotActivity.MERCHANTING) -> BotTradeManager.MERCHANT_COOLDOWN
                bot.preferences.hatesActivity(BotActivity.MERCHANTING) -> BotTradeManager.HATE_COOLDOWN
                else -> BotTradeManager.REGULAR_COOLDOWN
            }.random()
            messageCooldown = cooldownSeconds
            this@TradeBotScript.message = BotTradeManager.advertiseTrade(bot, item, purpose)
        }
    }

    private fun computeOfferAndExpected(needsBank: Boolean): Boolean {
        bot.preferences.rebalanceWantedItems()
        offering = emptyList()
        expected = emptyList()

        val priceStrategy = BotTradePrice.Companion.computePriceStrategy(bot)
        val offering = BotTradeManager.computeOfferItems(bot, priceStrategy, purpose)
        val expected = BotTradeManager.computeExpected(bot, priceStrategy, purpose, offering)
        if (offering.isEmpty() || expected.isEmpty()) {
            // No items to offer/no expected value was set.
            stop()
            bot.log("Nothing to offer or expect, end script.")
            return false
        } else if (offering.sumOf { it.totalPrice() } < BotTradeManager.MINIMUM_OFFER_VALUE) {
            bot.log("Not enough value to offer,  ending script.")
            return false
        }

        this.offering = offering
        this.expected = expected
        if (needsBank) {
            forceBanking = true
        }
        return true
    }

    private suspend fun startTradeWith(other: Player, tradeItem: BotTradeItem?): Boolean {
        val request = BotTradeRequestStage(bot, other)
        val scrutiny = BotTradeScrutiny.Companion.resolveScrutinyFor(bot, other)
        val offer = request.sendAndAwait() ?: return false
        bot.naturalMicroDelay()
        bot.log("Offering intended set of items; first pass.")
        val offerValue: Long
        when (purpose) {
            BotTradePurpose.MERCHANT_BUY, BotTradePurpose.MERCHANT_SELL, BotTradePurpose.BUY -> {
                offerValue = offering.sumOf { it.totalPrice().toLong() }
                if (bot.inventory.size() == 0 || !offer.offerAll(offering.map { it.item })) {
                    bot.log("Could not offer $purpose items.")
                    return false
                }
            }

            BotTradePurpose.SELL -> {
                offerValue = tradeItem?.price?.times(tradeItem.item.amount) ?: 0L
                if (tradeItem == null || !offer.offer(tradeItem.item)) {
                    bot.log("Could not offer sell trade item.")
                    return false
                }
            }
        }
        bot.naturalDecisionDelay()
        if (offer.getTradeItems().isEmpty()) {
            bot.log("No trade items added on interface.")
            return false
        }
        val timer = Stopwatch.createStarted()
        var receivingItems = emptyList<Item>()
        var lastReceivingItems = emptyList<Item>()
        repeat(10) {
            val offerInterface = bot.overlays[OfferTradeInterface::class]
            if (offerInterface == null) {
                bot.log("Trade was ended by the participating bot.")
                bot.naturalDelay()
                return false
            }

            if (Suspendable.waitFor(30.seconds) {
                    receivingItems = offer.getOtherTradeItems()
                    (receivingItems - lastReceivingItems).isNotEmpty()
                }) {
                bot.log("Trade screen has changed since last viewing. Validating contents...")
                lastReceivingItems = receivingItems
                timer.reset().start()
                val expectedItems = expected.map { it.item }
                val expectedValue = expected.sumOf { it.totalPrice().toLong() }
                val receivedValue = receivingItems.sumOf { world.economy.getPrice(it.id) * it.amount }
                val satisfied = scrutiny.applyScrutiny(purpose, expectedValue, expectedItems, bot, receivingItems)
                        || receivedValue > expectedValue * 2
                if (satisfied) {
                    if (finishTradeWith(offer)) {
                        // Trade completed successfully.
                        computeOfferAndExpected(true)
                        traded = true
                        return true
                    }
                    // otherwise try again.
                    bot.naturalDecisionDelay()
                    return@repeat
                } else {
                    // TODO Make bot communicate through chat?
                    //  Ie. "I'm expecting xyz items before we continue the trade."
                    //  I need a little more value for what I'm offering you.

                    /*todo val delta = targetAmount - currentAmount
                        when {
                            delta > 0 -> offer.offer(Item(id, delta))
                            delta < 0 -> offer.remove(Item(id, -delta))
                        }
                        */
                    bot.log("Bot is unhappy with trade contents, rebalancing...")
                    if (receivedValue > expectedValue) {
                        // Value isn't an issue. Bot is expecting specific items that they aren't getting. Calculate the
                        // percentage of correct items.
                        if (purpose != BotTradePurpose.MERCHANT_SELL && purpose != BotTradePurpose.SELL) {
                            val expectedItemIds = expectedItems.associateBy { it.id }
                            var total = expectedItems.size
                            var current = 0
                            for (item in receivingItems) {
                                val amount = expectedItemIds[item.id]?.amount
                                if (amount != null && item.amount > amount) {
                                    current++
                                }
                            }

                            // Match our value to the percentage of items we're getting.
                            val offerItems = offer.getTradeItems()
                            val oldCount = offerItems.sumOf { it.amount.toLong() }
                            var newCount = (oldCount.toDouble() * (current / total.toDouble())).toInt()
                            for (item in offerItems) {
                                if (newCount < 1) {
                                    // Remove the entire item.
                                    offer.remove(item)
                                    continue
                                }
                                if (newCount - item.amount < 1) {
                                    // Removing some of the item.
                                    val removeCount = item.amount - newCount
                                    offer.remove(Item(item.id, removeCount))
                                    newCount = 0
                                } else {
                                    // Keep item on trade screen.
                                    newCount -= item.amount
                                }
                            }
                        } else {
                            // Value is an issue. Increase stock to match value.
                            val offerItems = offer.getTradeItems()
                            val offerValue = offerItems.sumOf { world.economy.getPrice(it.id) * it.amount }
                            val otherOfferValue =
                                offer.getOtherTradeItems().sumOf { world.economy.getPrice(it.id) * it.amount }
                            if (otherOfferValue > offerValue) {
                                var add = otherOfferValue - offerValue
                                when (purpose) {
                                    BotTradePurpose.SELL -> {
                                        if (tradeItem == null) {
                                            offer.decline()
                                            bot.log("Invalid trade item.")
                                            return false
                                        }
                                        val needed = floor(add.toDouble() / tradeItem.price).toInt()
                                        val noted = itemDef(tradeItem.item.id).notedId.orElse(-1)
                                        if (noted != -1 && noted in bot.inventory) {
                                            offer.offer(Item(noted, needed))
                                        } else {
                                            offer.offer(Item(tradeItem.item.id, needed))
                                        }
                                    }

                                    BotTradePurpose.MERCHANT_SELL -> {
                                        for (item in bot.inventory) {
                                            if (item == null) {
                                                continue
                                            }
                                            val value = world.economy.getPrice(item.id) * item.amount
                                            add -= value
                                            // TODO Reduce value by amount as well, not just whole quantities
                                            offer.offer(item)
                                            if (value <= 0) {
                                                break
                                            }
                                        }
                                    }

                                    else -> {}
                                }
                            }
                        }
                    } else {
                        // Value is an issue. Reduce stock to match value.
                        val offerItems = offer.getTradeItems()
                        val oldOfferValue = offerItems.sumOf { world.economy.getPrice(it.id) * it.amount }
                        var newOfferValue = (oldOfferValue * (receivedValue / expectedValue.toDouble())).toLong()
                        for (item in offerItems) {
                            if (newOfferValue < 1) {
                                // Remove the entire item.
                                offer.remove(item)
                                continue
                            }
                            val singlePrice = world.economy.getPrice(item.id)
                            val totalPrice = singlePrice * item.amount
                            if (newOfferValue - totalPrice < 1) {
                                // Removing some of the item.
                                val removeValue = totalPrice - newOfferValue
                                val removeAmount = floor(removeValue.toDouble() / singlePrice).toInt()
                                offer.remove(Item(item.id, removeAmount))
                                newOfferValue = 0
                            } else {
                                // Keep item on trade screen.
                                newOfferValue -= totalPrice
                            }
                        }
                    }
                }
            } else {
                // No change on trade screen.
                bot.naturalDecisionDelay()
                return@repeat
            }
        }
        offer.decline()
        bot.log("Ended trade because it went back and forth for more than 10 turns.")
        bot.naturalDelay()
        return false
    }

    private suspend fun finishTradeWith(offer: BotTradeOfferStage): Boolean {
        // Bot is happy with the contents of the trade, try and finish.
        val confirm = offer.offerAndAwait() ?: return false
        if (confirm.confirmAndAwait()) {
            forceBanking = true
            return true
        } else {
            offer.decline()
            bot.log("Ended trade because confirm stage failed.")
            bot.naturalDelay()
            return false
        }
    }

    private fun canTradeWith(other: Player): Boolean {
        return if (blackList[other.username]?.isAfter(Instant.now()) == true || other.overlays.hasWindow()) {
            false
        } else if (other !is Bot) {
            true
        } else if (other is Bot) {
            // check if BUYING contains any from other offer
            val tradeScript = other.scriptStack.current() as? TradeBotScript
            if (tradeScript == null) {
                return false
            }
            return if (purpose == BotTradePurpose.BUY && expected.any { tradeScript.offering.contains(it) }) {
                true
            } else if (purpose == BotTradePurpose.SELL && offering.any { tradeScript.expected.contains(it) }) {
                true
            } else {
                false
            }

        } else {
            false
        }
    }

    private suspend fun resetInventoryItems() {
        handler.banking.travelToBankDepositAll()
        //todo   handler.banking.travelToBankWithdraw(offering!!)
        bot.naturalDelay()
        if (bot.inventory.size() == 0) {
            // No more items left to trade in this context, stop script.
            stop()
        }
    }

    open fun resolveScrutiny(other: Player): BotTradeScrutiny {
        // First determine desired scrutiny based on personality.
        val scrutinyActivity = when {
            bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> BotTradeScrutiny.PARANOID
            bot.personality.isIntelligent && bot.personality.isSocial -> BotTradeScrutiny.PARANOID
            bot.preferences.likesActivity(BotActivity.MERCHANTING) || bot.personality.isIntelligent -> BotTradeScrutiny.SMART
            bot.personality.isDumb && bot.personality.isSocial -> BotTradeScrutiny.TRUSTWORTHY
            bot.personality.isDumb && !bot.personality.isSocial -> BotTradeScrutiny.DUMB
            else -> BotTradeScrutiny.NORMAL
        }
        // Then determine desired scrutiny based on feelings about the participant.
        val scrutinyPlayer = when {
            bot.preferences.hatesPlayer(other.username) -> BotTradeScrutiny.SMART
            bot.preferences.dislikesPlayer(other.username) -> BotTradeScrutiny.NORMAL
            bot.preferences.likesPlayer(other.username) -> BotTradeScrutiny.TRUSTWORTHY
            bot.preferences.lovesPlayer(other.username) -> BotTradeScrutiny.DUMB
            else -> scrutinyActivity
        }
        // Whichever is higher is used.
        return if (scrutinyPlayer.ordinal >= scrutinyActivity.ordinal) scrutinyPlayer else scrutinyActivity
    }

    open fun resolveTradePrice(): BotTradePrice {
        return when {
            bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> BotTradePrice.LOW
            bot.preferences.likesActivity(BotActivity.MERCHANTING) -> BotTradePrice.AVERAGE
            bot.personality.isDumb || (bot.personality.isDumb && duration < 1.hours)
                    || (!bot.personality.isIntelligent && duration < 30.minutes) -> BotTradePrice.DESPERATE

            bot.personality.isIntelligent -> if (randBoolean()) BotTradePrice.AVERAGE else BotTradePrice.EXACT
            else -> if (RandomUtils.roll(bot.personality.intelligence)) BotTradePrice.EXACT else BotTradePrice.HIGH
        }
    }

    private fun addBlacklisted(name: String, duration: Duration) {
        blackList[name] = Instant.now().plus(duration.toJavaDuration())
    }

    private fun checkBlacklisted(name: String): Boolean {
        return blackList[name]?.isAfter(Instant.now()) == true
    }
}