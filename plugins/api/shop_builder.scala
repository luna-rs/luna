/*
 Adds functionality for building shops within all plugins. The DSL syntax is as follows:

(shopBuilder
  `shop name` "My Store"
  `sell policy` myPolicy
  `restock policy` myPolicy
  `takes currency` myCurrency
  sell "Dragon scimitar" x 25
  sell "Dragon mace" x 25
  sell "Drgaon dagger" x 25
  sell "Dragon longsword" x 25) `add shop`
*/

import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.shop._

import scala.collection.mutable

/* Function that links to the ShopBuilder. */
def shopBuilder = new ShopBuilder

/* The ShopBuilder class. */
class ShopBuilder {
  private var name = "Shop"
  private var restock = RestockPolicy.DEFAULT
  private var sell = SellPolicy.EXISTING
  private var currency = Currency.COINS
  var items = new mutable.HashMap[Int, IndexedItem]()
  var index = 0

  /* Sets shop name. */
  def `shop name`(value: String) = {
    checkNotNull(value, "`shop name` <String>")
    name = value
    this
  }

  /* Sets restock policy. */
  def `restock policy`(value: RestockPolicy) = {
    checkNotNull(value, "`restock policy` <RestockPolicy>")
    restock = value
    this
  }

  /* Sets sell policy. */
  def `sell policy`(value: SellPolicy) = {
    checkNotNull(value, "`sell policy` <SellPolicy>")
    sell = value
    this
  }

  /* Sets currency used. */
  def `takes currency`(value: Currency) = {
    checkNotNull(value, "`takes currency` <Currency>")
    currency = value
    this
  }

  /* Sell an item. */
  def sell(value: Int) = {
    new ItemAmountBuilder(value, this)
  }

  /* Sell an item by name. */
  def sell(value: String) = {
    checkNotNull(value, "sell <String>")
    val item = ItemDefinition.ALL.lookup(itemDef => itemDef.isTradeable &&
      itemDef.getName == value)
    if (item.isPresent) {
      new ItemAmountBuilder(item.get.getId, this)
    } else {
      throw new NoSuchElementException(s"No valid definition found for <$value>.")
    }
  }

  /* Sell a noted item by name. */
  def `sell noted`(value: String) = {
    val item = ItemDefinition.ALL.lookup(itemDef => itemDef.getName == value &&
      itemDef.isNoted && itemDef.isTradeable)
    if (item.isPresent) {
      new ItemAmountBuilder(item.get.getId, this)
    } else {
      throw new NoSuchElementException(s"No valid definition found for <$value>.")
    }
  }

  /* Checks if arguments are non-null. */
  private def checkNotNull(value: AnyRef, name: String): Unit = {
    if (value == null) {
      throw new NullPointerException(s"$name value is null.")
    }
  }

  /* Add to the collection of globally registered shops. */
  def `add shop` = {
    val shop = new Shop(world, name, restock, sell, currency)
    shop.init(items.values.toArray)

    ShopInterface.register(shop)
    true
  }
}

/* An ItemAmountBuilder class, links back to ShopBuilder. */
class ItemAmountBuilder(id: Int, builder: ShopBuilder) {

  /* The amount of the item being sold. Allows us to decouple the item from the amount (with
     infix notation). */
  def x(value: Int) = {
    val option = builder.items.put(id, new IndexedItem(builder.index, id, value))
    if (option.isDefined) {
      throw new IllegalStateException(s"Shop already contains ID <$id>")
    }
    builder.index += 1
    builder
  }
}