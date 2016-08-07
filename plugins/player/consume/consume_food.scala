import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.`def`.ItemDefinition.getNameForId
import io.luna.game.model.item.{Inventory, Item}
import io.luna.game.model.mobile.Skill.HITPOINTS
import io.luna.game.model.mobile.{Animation, Player}


private case class Food(healAmount: Int, consumeDelay: Long, ids: Int*)


private val ANIMATION = new Animation(829)
// TODO confirm 1800 is proper delay
private val FOOD_TABLE = Map(
  'cooked_meat -> Food(2, 1800, 2142),
  'cooked_chicken -> Food(2, 1800, 2140),
  'herring -> Food(2, 1800, 347),
  'anchovies -> Food(2, 1800, 319),
  'redberry_pie -> Food(2, 600, 2325, 2333),
  'shrimp -> Food(3, 1800, 315),
  'cake -> Food(4, 1800, 1891, 1893, 1895),
  'cod -> Food(4, 1800, 339),
  'pike -> Food(4, 1800, 351),
  'chocolate_cake -> Food(5, 1800, 1897, 1899, 1901),
  'mackerel -> Food(6, 1800, 355),
  'meat_pie -> Food(6, 600, 2327, 2331),
  'plain_pizza -> Food(7, 1800, 2289, 2291),
  'apple_pie -> Food(7, 600, 2323, 2335),
  'trout -> Food(7, 1800, 333),
  'meat_pizza -> Food(8, 1800, 2293, 2295),
  'anchovy_pizza -> Food(9, 1800, 2297, 2299),
  'salmon -> Food(9, 1800, 329),
  'bass -> Food(9, 1800, 365),
  'tuna -> Food(10, 1800, 361),
  'pineapple_pizza -> Food(11, 1800, 2301, 2303),
  'lobster -> Food(12, 1800, 379),
  'swordfish -> Food(14, 1800, 373),
  'monkfish -> Food(16, 1800, 7946),
  'karambwan -> Food(18, 600, 3144),
  'shark -> Food(20, 1800, 385),
  'manta_ray -> Food(22, 1800, 391),
  'sea_turtle -> Food(22, 1800, 397),
  'tuna_potato -> Food(22, 1800, 7060)
)

private val ID_TO_FOOD = {
  def foodLookupFunction(id: Int) = FOOD_TABLE.values.find(food => food.ids.contains(id)).get

  FOOD_TABLE.values.
    flatMap(food => food.ids).
    map(id => id -> foodLookupFunction(id)).toMap
}


private def consume(plr: Player, food: Food, index: Int) = {

  // TODO don't eat food if player is dead
  // TODO dueling, no food rule
  if (!plr.elapsedTime("last_food_consume", food.consumeDelay)) {
    return
  }

  val inventory = plr.inventory
  val toConsume = inventory.get(index)
  if (inventory.remove(toConsume, index)) {

    val nextIndex = food.ids.indexOf(toConsume.getId) + 1
    val nextFood = if (food.ids.isDefinedAt(nextIndex)) None else Some(food.ids(nextIndex))
    nextFood.foreach(id => inventory.add(new Item(id), index))

    plr.sendMessage(s"You eat the ${getNameForId(toConsume.getId)}.")
    plr.animation(ANIMATION)

    val skill = plr.skill(HITPOINTS)
    if (skill.getLevel < skill.getStaticLevel) {
      skill.increaseLevel(food.healAmount)
      plr.sendMessage("It healed some health.")
    }
  }

  plr.resetTime("last_food_consume")
  plr.resetTime("last_potion_consume")
}


intercept[ItemFirstClickEvent] { (msg, plr) =>
  if (msg.getInterfaceId == Inventory.INVENTORY_DISPLAY_ID) {

    ID_TO_FOOD.get(msg.getId).foreach { food =>
      consume(plr, food, msg.getIndex)
      msg.terminate
    }
  }
}