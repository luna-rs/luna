/*package api

import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.SellPolicy


/* Buy policy constants. */
val BUY_ALL = SellPolicy.ALL
val BUY_EXISTING = SellPolicy.EXISTING
val BUY_NONE = SellPolicy.NONE

/* Restock policy constants. */
val RESTOCK_FAST = RestockPolicy.FAST
val RESTOCK_DEFAULT = RestockPolicy.DEFAULT
val RESTOCK_SLOW = RestockPolicy.SLOW
val RESTOCK_NONE = RestockPolicy.DISABLED

/* Performs a lookup of the item definitions. Used for retrieving item identifiers. */
private fun lookup(test: (ItemDefinition) -> Boolean, value: String, builder: ShopBuilder2) {
    val item = ItemDefinition.ALL.lookup(test)
    if (item.isPresent) {
        ShopBuilder3(item.get().id, builder)
    } else {
        throw NoSuchElementException("No valid definition found for <$value>.")
    }
}

/* Links to the first shop builder. */
val `let new shop` = { ShopBuilder1() }


/* Contains a single function that links to the second shop builder. */
class ShopBuilder1 {
    infix fun `with name`(name: () -> String) = ShopBuilder2 (name)
}

/* Contains builder-like functions that allow the shop to be prepared and registered. */
class ShopBuilder2(name: () -> String) {
    /* Cached builder fields. */
    private var buyPolicy = BUY_EXISTING
    private var restockPolicy = RESTOCK_DEFAULT
    private var currency = CURRENCY_COINS
    private var openBy: Option[(EntityType, Int)] = None
    var items = new mutable . HashMap [Int, IndexedItem]()
    var index = 0

    /* Builder functions. */
    infix fun `and buy policy`(buyPolicy: SellPolicy) = {
        this.buyPolicy = buyPolicy
        this
    }

    fun `and restock policy`(restockPolicy: RestockPolicy) = {
        this.restockPolicy = restockPolicy
        this
    }

    fun `using currency`(currency: Currency) = {
        this.currency = currency
        this
    }

    fun `opened by npc`(id: Int) = {
        openBy = Some((TYPE_NPC, id))
        this
    }

    fun `opened by object`(id: Int) = {
        openBy = Some((TYPE_OBJECT, id))
        this
    }

    fun `sell noted`(name: String) = lookup({ it.isTradeable && it.name == name && it.isNoted }, name, this)

    fun sell(name: String) = lookup({ it.isTradeable && it.name == name && !it.isNoted }, name, this)

    fun sell(id: Int) = ShopBuilder3(id, this)


    /* Register shop in 'World'. */
    fun register() = {

        // Validate builder values.
        require(buyPolicy != null, "buyPolicy == null")
        require(restockPolicy != null, "restockPolicy == null")
        require(currency != null, "currency == null")
        require(openBy != null, "openBy == null")

        // Initialize shop for builder.
        val shop = new Shop (world, name, restockPolicy, buyPolicy, currency)
        shop.init(items.values.toArray)

        // Add NPC or Object first click listeners.
        openBy.foreach {
            case(etype, id) =>
            etype match {
                case TYPE_NPC =>
                on[NpcFirstClickEvent].args { id }.run { _.plr.interfaces.open(new ShopInterface (shop)) }
                case TYPE_OBJECT =>
                on[ObjectFirstClickEvent].args { id }.run { _.plr.interfaces.open(new ShopInterface (shop)) }
            }
        }

        // Register shop and add placeholder return type for DSL syntax.
        world.getShops.register(shop)
        None
    }
}

/* Contains an amount builder function that links back to the previous builder. */
final class ShopBuilder3(id: Int, builder: ShopBuilder2) {
    def x(amount: Int) =
    {
        val option = builder.items.put(id, new IndexedItem (builder.index, id, amount))
        if (option.isDefined) {
            throw new IllegalStateException (s"Shop already contains item '$id'")
        }
        builder.index += 1
        builder
    }
}*/