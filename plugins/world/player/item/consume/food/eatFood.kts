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
fun tryEat(plr: Player, food: Food, index: Int) {
    if (plr.lastEat < food.longDelay() || !plr.isAlive) {
        // TODO Confirm duel rule for no food.
        return
    }

    plr.interruptAction()
    plr.lastEat = -1
    plr.inventory.computeIdForIndex(index)
        .map { Item(it) }
        .ifPresent { eat(plr, it, food, index) }
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
        val consumeName = eatItem.itemDef.name
        plr.sendMessage(food.consumeMessage(consumeName))
        plr.animation(eatAnimation)
        food.effect(plr)

        // Increase HP.
        val hp = plr.hitpoints
        if (hp.level < hp.staticLevel) {
            hp.addLevels(food.heal, false)
            plr.sendMessage(food.healMessage(consumeName))
        }
    }
}

/**
 * Performs a lookup for the potion and forwards to [tryDrink].
 */
fun lookup(msg: ItemFirstClickEvent) {
    val food = Food.ID_TO_FOOD[msg.id]
    if (food != null) {
        tryEat(msg.plr, food, msg.index)
        msg.terminate()
    }
}

/**
 * Forwards to [tryEat] if the item clicked was food.
 */
on(ItemFirstClickEvent::class)
    .condition { itemDef(id).hasInventoryAction(0, "Eat") }
    .then { lookup(this) }
