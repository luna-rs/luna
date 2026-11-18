package api.shop.dsl

import api.bot.zone.SubZone
import api.predef.*
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

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
     * Optional pricing for original stock; null preserves the usual specialty-shop prices.
     */
    var stockPricing: Shop.StockPricing? = null

    /**
     * Declares where bots can find a shopkeeper with a custom click or dialogue handler.
     *
     * @author lare96
     */
    class BotAccessReceiver internal constructor() {
        internal val sellers = mutableListOf<Shop.NpcSeller>()

        /**
         * Discovers the existing click handler for an NPC whose spawn is inside a bot subzone.
         */
        fun npcSeller(option: Int, id: Int, subZone: SubZone, dialogueOption: String? = null) {
            npcSeller(option, id, subZone.area, dialogueOption)
        }

        /**
         * Supplies the NPC's local spawn area when no bot subzone covers the shop.
         */
        fun npcSeller(option: Int, id: Int, area: SimpleBoxArea, dialogueOption: String? = null) {
            require(option in setOf(1, 2, 4, 5))
            sellers += Shop.NpcSeller(id, option, dialogueOption, area)
        }
    }

    /**
     * The next index to add an item to.
     */
    private var index = 0

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

    private val npcSellers = mutableListOf<Shop.NpcSeller>()

    /**
     * Adds locations for shopkeepers with custom click or dialogue handlers.
     * NPCs declared in [open] are discovered automatically.
     */
    fun botAccess(init: BotAccessReceiver.() -> Unit) {
        npcSellers += BotAccessReceiver().apply(init).sellers
    }

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
        val shop = Shop(world, name, restock, buy, currency, stockPricing)
        shop.init(items)

        // Normal players can still discover this shop through its configured NPC, object, or button listeners.
        openReceiver.addListeners(shop)
        npcSellers.forEach(shop::registerNpcSeller)

        world.shops.register(shop)
    }

    /**
     * Adds an item to this shop. Is invoked implicitly through [sell].
     */
    fun addItem(id: Int, amount: Int, maxAmount: Int): ShopHandler.PendingShopItem {
        val item = ShopHandler.PendingShopItem(index++, id, amount, maxAmount)
        items += item
        return item
    }
}
