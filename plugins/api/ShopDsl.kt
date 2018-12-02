package api

import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.PlayerEvent
import io.luna.game.model.EntityType
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.shop.Currency.COINS
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.SellPolicy
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface

/**
 * The entry point of the shop creation DSL.
 */
fun shop(init: ShopClosure.() -> Unit) {
    // Build the shop.
    val builder = ShopClosure()
    init(builder)

    // Validate and register the shop.
    require(builder.name != null) { "<name> must be defined for 'shop'." }
    builder.register()
}

/**
 * The main closure that encapsulates the shop creation DSL.
 */
class ShopClosure {

    /**
     * The name.
     */
    var name: String? = null

    /**
     * The buy policy.
     */
    var buy = SellPolicy.EXISTING

    /**
     * The restock policy.
     */
    var restock = RestockPolicy.DEFAULT!!

    /**
     * The currency.
     */
    var currency = COINS

    /**
     * The added items.
     */
    private var items = ArrayList<IndexedItem>()

    /**
     * The next index to add an item to.
     */
    private var index = 0

    /**
     * The [SellClosure] instance.
     */
    private val sellBuilder = SellClosure(this)

    /**
     * The [OpenClosure] instance.
     */
    private val openBuilder = OpenClosure()

    /**
     * Initializes a new [SellClosure].
     */
    fun sell(init: SellClosure.() -> Unit) {
        init(sellBuilder)
    }

    /**
     * Initializes a new [OpenClosure].
     */
    fun open(init: OpenClosure.() -> Unit) {
        init(openBuilder)
        require(openBuilder.type != null && openBuilder.id != null) {
            "Both <type> and <id> must be defined for 'open'."
        }
    }

    /**
     * Registers this shop. Is invoked implicitly by the [shop] entry point.
     */
    fun register() {
        // Create and initialize shop.
        val shop = Shop(world, name, restock, buy, currency)
        shop.init(items.toTypedArray())

        // Add event listeners from the OpenClosure.
        val eventType: ArgsTest<out PlayerEvent>? =
            when (openBuilder.type) {
                EntityType.NPC -> on(NpcFirstClickEvent::class).args(openBuilder.id!!)
                EntityType.OBJECT -> on(ObjectFirstClickEvent::class).args(openBuilder.id!!)
                else -> null
            }
        eventType?.run { it.plr.interfaces.open(ShopInterface(shop)) }

        // Register shop!
        world.shops.register(shop)
    }

    /**
     * The receiver for the [open] closure.
     */
    class OpenClosure {

        /**
         * The entity that will open the shop. Only works for [EntityType.NPC] and [EntityType.OBJECT].
         */
        var type: EntityType? = null

        /**
         * The identifier of the entity.
         */
        var id: Int? = null
    }

    /**
     * The receiver for the [sell] closure.
     */
    class SellClosure(private val shop: ShopClosure) {

        /**
         * Creates an [AmountBuilder] for id#[id].
         */
        private fun itemId(id: Int): AmountBuilder {
            val valid = itemDef(id)?.isTradeable == true
            return when (valid) {
                true -> AmountBuilder(id, shop)
                false -> throw NoSuchElementException("Item identifier <$id> not tradeable.")
            }
        }

        /**
         * Creates an [AmountBuilder] for an item whose name matches [name]. The syntax "id#" can be used
         * to append by identifier instead.
         */
        fun item(name: String, noted: Boolean = false): AmountBuilder {
            if (name.startsWith("id#")) {
                return itemId(name.drop(3).toInt())
            }
            return ItemDefinition.ALL
                .lookup { it.isTradeable && it.name == name && it.isNoted == noted }
                .map { AmountBuilder(it.id, shop) }
                .orElseThrow { NoSuchElementException("No valid definition found for <$name>.") }
        }
    }

    /**
     * Adds to the list of shop items, once the amount is determined.
     */
    class AmountBuilder(private val id: Int, private val shop: ShopClosure) {

        /**
         * Retrieves the amount and adds the item to the shop.
         */
        infix fun x(amount: Int) =
            shop.items.add(IndexedItem(shop.index++, id, amount))
    }
}


