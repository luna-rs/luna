/*
 Adds functionality for building shops within all plugins. The DSL syntax for a shop with the default
 settings and some random items is as follows:

(`let new shop`
  `with name` "Luna's Example Shop"
  `and buy policy` BUY_EXISTING
  `and restock policy` RESTOCK_DEFAULT
  `using currency` CURRENCY_COINS
  selling "Dragon scimitar" x 50
  selling "Dragon mace" x 50
  selling "Drgaon dagger" x 50
  selling "Dragon longsword" x 50
  `selling noted` "Lobster" x 1000
  `selling noted` "Swordfish" x 500) register
*/
import java.util.Objects

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

  def `selling noted`(value: String) = lookup(itemDef => itemDef.isTradeable &&
    itemDef.getName == value && itemDef.isNoted, value, this)

  def selling(value: String) = lookup(itemDef => itemDef.isTradeable &&
    itemDef.getName == value && !itemDef.isNoted, value, this)

  def selling(value: Int) = new ShopBuilder3(value, this)

  def register = {
    require(buyPolicy != null, "buyPolicy == null")
    require(restockPolicy != null, "restockPolicy == null")
    require(currency != null, "currency == null")
    require(items.nonEmpty && index > 0, "items.isEmpty || index <= 0")

    val shop = new Shop(world, name, restockPolicy, buyPolicy, currency)
    shop.init(items.values.toArray)

    ShopInterface.register(shop)
    0
  }
}

/* Contains an amount builder function that links back to the previous builder. */
final class ShopBuilder3(id: Int, builder: ShopBuilder2) {
  def x(amount: Int) = {
    val option = builder.items.put(id, new IndexedItem(builder.index, id, amount))
    if (option.isDefined) {
      throw new IllegalStateException(s"Shop already contains item <$id>")
    }
    builder.index += 1
    builder
  }
}