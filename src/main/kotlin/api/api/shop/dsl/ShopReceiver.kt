package api.shop.dsl

import api.predef.*
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * The receiver for the [shop] closure that encapsulates shop creation.
 *
 * @author lare96
 */
class ShopReceiver(val name: String) {
    //todo docs
    /**
     * The buy policy.
     */
    var buy = BuyPolicy.EXISTING

    /**
     * The restock policy.
     */
    var restock = RestockPolicy.DEFAULT!!

    /**
     * The currency.
     */
    var currency = Currency.COINS

    /**
     * If a bot can access this shop.
     */
    var botAccess: Bot.() -> Boolean = { true }

    /**
     * The next index to add an item to.
     */
    private var index = 0

    /**
     * Whether this shop should be considered a merchant-only shop by bot shopping systems.
     *
     * This does not grant access by itself. [botAccess] still decides whether an individual bot may open the shop.
     */
    private var merchantOnly = false

    /**
     * The added items.
     */
    private val items = ArrayList<ShopHandler.PendingShopItem>()

    /**
     * The [SellReceiver] instance.
     */
    private val sellReceiver = SellReceiver(this, false)

    /**
     * The [OpenReceiver] instance.
     */
    private val openReceiver = OpenReceiver()

    /**
     * Initializes a new [SellReceiver]. Allows for adding the initial items to the shop.
     */
    fun sell(init: SellReceiver.() -> Unit) {
        init(sellReceiver)
    }

    /**
     * Initializes a new [OpenReceiver]. Allows for opening this shop with npc or object clicks.
     */
    fun open(init: OpenReceiver.() -> Unit) {
        init(openReceiver)
    }

    /**
     * Registers this shop. Is invoked implicitly once the shop closure exits.
     */
    fun register() {
        // The shop registry remains the single source of truth. Bot shopping discovers shops from ShopManager later.
        val shop = Shop(world, name, restock, buy, currency, botAccess, merchantOnly)
        shop.init(items)

        // Normal players can still discover this shop through its configured NPC, object, or button listeners.
        openReceiver.addListeners(shop)

        world.shops.register(shop)
    }

    /**
     * Adds an item to this shop. Is invoked implicitly through [sell].
     */
    fun addItem(id: Int, amount: Int, maxAmount: Int) {
        items += ShopHandler.PendingShopItem(index++, id, amount, maxAmount)
    }

    /**
     * Restricts this shop to bots interested in merchanting.
     *
     * Non-strict shops require the bot to like merchanting. Strict shops require the bot to love merchanting.
     * [additionalFilter] may impose extra requirements such as combat level.
     *
     * The merchant-only flag is only used to categorize this shop during speculative bot shopping. [botAccess] remains
     * authoritative, meaning a bot still has to satisfy every condition configured here before opening the shop.
     */
    fun merchantsAccessOnly(
        strict: Boolean = false,
        additionalFilter: Bot.() -> Boolean = { true }
    ) {
        // Mark this shop as part of the merchant shopping pool.
        merchantOnly = true

        // Access itself is always decided by the predicate, including strictness and any extra caller-supplied checks.
        botAccess = {
            val merchantAccess = if (strict) {
                preferences.lovesActivity(BotActivity.MERCHANTING)
            } else {
                preferences.likesActivity(BotActivity.MERCHANTING)
            }

            merchantAccess && additionalFilter(this)
        }
    }
}