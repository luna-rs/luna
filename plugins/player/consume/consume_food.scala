/*
 A plugin that adds functionality for eating food.

 SUPPORTS:
  -> Eating a variety of consumables.
  -> Multi-portion foods (pizzas, cakes, etc).
  -> Foods with different throttle rates (karambwans, pies, etc).

 TODO:
  -> Support for less commonly used foods.
  -> Stop eating process if player has just died.
  -> Confirm duel rule for no food.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing food in the 'FOOD_TABLE'. */
private case class Food(heal: Int, delay: Long, ids: Int*)


/* Consume food animation. */
private val ANIMATION = new Animation(829)

/*
 A table of all the foods that can be eaten.

 food_symbol -> Food
*/
private val FOOD_TABLE = Map(
  'cooked_meat -> Food(heal = 2,
    delay = 1800,
    ids = 2142),

  'cooked_chicken -> Food(heal = 2,
    delay = 1800,
    ids = 2140),

  'herring -> Food(heal = 2,
    delay = 1800,
    ids = 347),

  'anchovies -> Food(heal = 2,
    delay = 1800,
    ids = 319),

  'redberry_pie -> Food(heal = 2,
    delay = 600,
    ids = 2325, 2333),

  'shrimp -> Food(heal = 3,
    delay = 1800,
    ids = 315),

  'cake -> Food(heal = 4,
    delay = 1800,
    ids = 1891, 1893, 1895),

  'cod -> Food(heal = 4,
    delay = 1800,
    ids = 339),

  'pike -> Food(heal = 4,
    delay = 1800,
    ids = 351),

  'chocolate_cake -> Food(heal = 5,
    delay = 1800,
    ids = 1897, 1899, 1901),

  'mackerel -> Food(heal = 6,
    delay = 1800,
    ids = 355),

  'meat_pie -> Food(heal = 6,
    delay = 600,
    ids = 2327, 2331),

  'plain_pizza -> Food(heal = 7,
    delay = 1800,
    ids = 2289, 2291),

  'apple_pie -> Food(heal = 7,
    delay = 600,
    ids = 2323, 2335),

  'trout -> Food(heal = 7,
    delay = 1800,
    ids = 333),

  'meat_pizza -> Food(heal = 8,
    delay = 1800,
    ids = 2293, 2295),

  'anchovy_pizza -> Food(heal = 9,
    delay = 1800,
    ids = 2297, 2299),

  'salmon -> Food(heal = 9,
    delay = 1800,
    ids = 329),

  'bass -> Food(heal = 9,
    delay = 1800,
    ids = 365),

  'tuna -> Food(heal = 10,
    delay = 1800,
    ids = 361),

  'pineapple_pizza -> Food(heal = 11,
    delay = 1800,
    ids = 2301, 2303),

  'lobster -> Food(heal = 12,
    delay = 1800,
    ids = 379),

  'swordfish -> Food(heal = 14,
    delay = 1800,
    ids = 373),

  'monkfish -> Food(heal = 16,
    delay = 1800,
    ids = 7946),

  'karambwan -> Food(heal = 18,
    delay = 600,
    ids = 3144),

  'shark -> Food(heal = 20,
    delay = 1800,
    ids = 385),

  'manta_ray -> Food(heal = 22,
    delay = 1800,
    ids = 391),

  'sea_turtle -> Food(heal = 22,
    delay = 1800,
    ids = 397),

  'tuna_potato -> Food(heal = 22,
    delay = 1800,
    ids = 7060)
)

/*
 A different mapping of the 'FOOD_TABLE' that maps food ids to their data.

 food_id -> Food
*/
private val ID_TO_FOOD = {
  for {
    (symbol, food) <- FOOD_TABLE
    foodId <- food.ids
  } yield foodId -> food
}


/* Attempt to consume food, if we haven't just recently consumed any. */
private def consume(plr: Player, food: Food, index: Int): Unit = {
  val inventory = plr.inventory
  val skill = plr.skill(SKILL_HITPOINTS)
  val ids = food.ids

  if (!plr.elapsedTime("last_food_consume", food.delay)) {
    return
  }

  plr.interruptAction()

  val toConsume = inventory.get(index)
  if (inventory.remove(toConsume, index)) {

    val nextIndex = ids.indexOf(toConsume.getId) + 1
    if (ids.isDefinedAt(nextIndex)) { /* Add unfinished portion to inventory, if there is one. */
      inventory.add(new Item(ids(nextIndex)), index)
    }

    plr.sendMessage(s"You eat the ${ nameOfItem(toConsume.getId) }.")
    plr.animation(ANIMATION)

    if (skill.getLevel < skill.getStaticLevel) {
      skill.increaseLevel(food.heal, skill.getStaticLevel)
      plr.sendMessage("It heals some health.")
    }
  }

  plr.resetTime("last_food_consume")
}


/* Intercept first click item event, attempt to consume food if applicable. */
on[ItemFirstClickEvent] { msg =>
  ID_TO_FOOD.get(msg.id).foreach { food =>
    consume(msg.plr, food, msg.index)
    msg.terminate
  }
}
