import api.attr.Stopwatch
import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.item.consume.potion.Potion

/**
 * The "last_drink_timer" stopwatch.
 */
var Player.lastDrink by Stopwatch("last_drink_timer")

/**
 * The "last_eat_timer" stopwatch.
 */
var Player.lastEat by Stopwatch("last_eat_timer")

/**
 * Item instance for empty vials.
 */
val vialItem = Item(229)

/**
 * The consume potion animation.
 */
val drinkAnimation = Animation(829)

/**
 * The delay between consuming potions.
 */
val consumeDelay = 1800L

/**
 * Forwards to [drink] if the [Player] is alive and not drinking too quickly.
 */
fun tryDrink(plr: Player, potion: Potion, id: Int, index: Int) {
    if (plr.lastDrink < consumeDelay || !plr.isAlive) {
        // TODO Confirm duel rule for no drinks.
        return
    }

    plr.interruptAction()
    plr.lastDrink = -1
    plr.lastEat = -1
    drink(plr, Item(id), potion, index)
}

/**
 * Drinks a potion and applies the appropriate effects to the player.
 */
fun drink(plr: Player, drinkItem: Item, potion: Potion, index: Int) {
    val id = drinkItem.id
    val inv = plr.inventory
    if (inv.remove(index, drinkItem)) {

        // Add next dose, or empty vial.
        val nextId = potion.getNextDose(id)
        when (nextId) {
            null -> inv.add(vialItem)
            else -> inv.add(index, Item(nextId))
        }

        // Send consume messages.
        plr.sendMessage("You drink some of your ${potion.formattedName}.")

        val dosesLeft = potion.getDosesLeft(id)
        when (dosesLeft) {
            0 -> plr.sendMessage("You have finished your potion.")
            1 -> plr.sendMessage("You have 1 dose left.")
            else -> plr.sendMessage("You have $dosesLeft doses left.")
        }

        // Invoke effects.
        plr.animation(drinkAnimation)
        potion.effect(plr)
    }
}

/**
 * Performs a lookup for the potion and forwards to [tryDrink].
 */
fun lookup(msg: ItemFirstClickEvent) {
    val potion = Potion.DOSE_TO_POTION[msg.id]
    if (potion != null) {
        tryDrink(msg.plr, potion, msg.id, msg.index)
    }
}

/**
 * Forwards to [lookup] if the item clicked was a consumable.
 */
on(ItemFirstClickEvent::class)
    .filter { itemDef(id).hasInventoryAction(0, "Drink") }
    .then { lookup(this) }