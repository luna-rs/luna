/*
 Adds functionality for building shops within all plugins. The DSL syntax for a shop with the default
 settings and some random items is as follows:

(`let new shop`
  `with name` "Luna's Example Shop"
  `and buy policy` BUY_EXISTING
  `and restock policy` RESTOCK_DEFAULT
  `using currency` CURRENCY_COINS
  `opened by npc` 520
  sell "Dragon scimitar" x 50
  sell "Dragon mace" x 50
  sell "Drgaon dagger" x 50
  sell "Dragon longsword" x 50
  `sell noted` "Lobster" x 1000
  `sell noted` "Swordfish" x 500) register
*/

import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.EntityType
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.shop._

import scala.collection.mutable


/* Buy policy constants. */
val BUY_ALL = SellPolicy.ALL
val BUY_EXISTING = SellPolicy.EXISTING
val BUY_NONE = SellPolicy.NONE

/* Restock policy constants. */
val RESTOCK_FAST = RestockPolicy.FAST
val RESTOCK_DEFAULT = RestockPolicy.DEFAULT
val RESTOCK_SLOW = RestockPolicy.SLOW
val RESTOCK_NONE = RestockPolicy.DISABLED

/* Currency constants. */
val CURRENCY_COINS = Currency.COINS
val CURRENCY_TOKKUL = Currency.TOKKUL
val CURRENCY_AGILITY_ARENA_TICKETS = Currency.AGILITY_ARENA_TICKETS
val CURRENCY_CASTLE_WARS_TICKETS = Currency.CASTLE_WARS_TICKETS


/* Performs a lookup of the item definitions. Used for retrieving item identifiers. */
private def lookup(test: java.util.function.Predicate[ItemDefinition],
                   value: String,
                   builder: ShopBuilder2) = {
  val item = ItemDefinition.ALL.lookup(test)
  if (item.isPresent) {
    new ShopBuilder3(item.get.getId, builder)
  } else {
    throw new NoSuchElementException(s"No valid definition found for <$value>.")
  }
}

/* Links to the first shop builder. */
def `let new shop` = new ShopBuilder1


/* Contains a single function that links to the second shop builder. */
final class ShopBuilder1 {
  def `with name`(name: String) = new ShopBuilder2(name)
}

/* Contains builder-like functions that allow the shop to be prepared and registered. */
final class ShopBuilder2(name: String) {
  /* Cached builder fields. */
  private var buyPolicy = BUY_EXISTING
  private var restockPolicy = RESTOCK_DEFAULT
  private var currency = CURRENCY_COINS
  private var openBy: Option[(EntityType, Int)] = None
  var items = new mutable.HashMap[Int, IndexedItem]()
  var index = 0

  /* Builder functions. */
  def `and buy policy`(buyPolicy: SellPolicy) = {
    this.buyPolicy = buyPolicy
    this
  }

  def `and restock policy`(restockPolicy: RestockPolicy) = {
    this.restockPolicy = restockPolicy
    this
  }

  def `using currency`(currency: Currency) = {
    this.currency = currency
    this
  }

  def `opened by npc`(id: Int) = {
    openBy = Some((TYPE_NPC, id))
    this
  }

  def `opened by object`(id: Int) = {
    openBy = Some((TYPE_OBJECT, id))
    this
  }

  def `sell noted`(name: String) = lookup(itemDef => itemDef.isTradeable &&
    itemDef.getName == name && itemDef.isNoted, name, this)

  def sell(name: String) = lookup(itemDef => itemDef.isTradeable &&
    itemDef.getName == name && !itemDef.isNoted, name, this)

  def sell(id: Int) = new ShopBuilder3(id, this)

  /* Register shop in 'World'. */
  def register = {

    // Validate builder values.
    require(buyPolicy != null, "buyPolicy == null")
    require(restockPolicy != null, "restockPolicy == null")
    require(currency != null, "currency == null")
    require(openBy != null, "openBy == null")

    // Initialize shop for builder.
    val shop = new Shop(world, name, restockPolicy, buyPolicy, currency)
    shop.init(items.values.toArray)

    // Add NPC or Object first click listeners.
    openBy.foreach { case (etype, id) =>
      etype match {
        case TYPE_NPC =>
          on[NpcFirstClickEvent].
            args { id }.
            run { _.plr.interfaces.open(new ShopInterface(shop)) }
        case TYPE_OBJECT =>
          on[ObjectFirstClickEvent].
            args { id }.
            run { _.plr.interfaces.open(new ShopInterface(shop)) }
      }
    }

    // Register shop and add placeholder return type for DSL syntax.
    world.getShops.register(shop)
    None
  }
}

/* Contains an amount builder function that links back to the previous builder. */
final class ShopBuilder3(id: Int, builder: ShopBuilder2) {
  def x(amount: Int) = {
    val option = builder.items.put(id, new IndexedItem(builder.index, id, amount))
    if (option.isDefined) {
      throw new IllegalStateException(s"Shop already contains item '$id'")
    }
    builder.index += 1
    builder
  }
}