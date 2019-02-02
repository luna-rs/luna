import api.attr.Stopwatch
import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.item.consume.food.Food

/**
 * The "last_eat_timer" stopwatch.
 */
var Player.lastEat by Stopwatch("last_eat_timer")

/**
 * The food eating animation.
 */
val eatAnimation = Animation(829)

/**
 * Forwards to [eat] if the [Player] is alive and not eating too quickly.
 */
fun tryEat(plr: Player, food: Food, eatItem: Item, index: Int) {
    if (plr.lastEat < food.longDelay() || !plr.isAlive) {
        return
    }

    plr.interruptAction()
    plr.lastEat = -1
    eat(plr, eatItem, food, index)
}

/**
 * Consumes food and restores health, plus any other effects.
 */
fun eat(plr: Player, eatItem: Item, food: Food, index: Int) {
    val inv = plr.inventory
    if (inv.remove(index, eatItem)) {
        // Add next portion of item, if applicable.
        val nextId = food.getNextId(eatItem.id)
        if (nextId != null) {
            inv.add(index, Item(nextId))
        }

        // Send consume messages.
        val name = food.formattedName
        plr.sendMessage(food.consumeMessage(name))
        plr.animation(eatAnimation)
        food.effect(plr)

        // Increase HP.
        val hp = plr.hitpoints
        if (hp.level < hp.staticLevel) {
            hp.addLevels(food.heal, false)
            plr.sendMessage(food.healMessage(name))
        }
    }
}

/**
 * Performs a lookup for the potion and forwards to [tryDrink].
 */
fun lookup(msg: ItemFirstClickEvent) {
    val item = msg.plr.inventory.get(msg.index)
    if (item != null) {
        val food = Food.ID_TO_FOOD[item.id]
        if (food != null) {
            tryEat(msg.plr, food, item, msg.index)
            msg.terminate()
        }
    }
}

/**
 * Forwards to [tryEat] if the item clicked was food.
 */
on(ItemFirstClickEvent::class)
    .condition { itemDef(id).hasInventoryAction(0, "Eat") }
    .then { lookup(this) }
