import api.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.items.consume.potion.Potion

/**
 * The "last_drink_timer" attribute.
 */
private var Player.lastDrink by TimerAttr("last_drink_timer")

/**
 * The "last_eat_timer" attribute.
 */
private var Player.lastEat by TimerAttr("last_eat_timer")

/**
 * Item instance for empty vials.
 */
private val vialItem = Item(229)

/**
 * The consume potion animation.
 */
private val drinkAnimation = Animation(829)

/**
 * The delay between consuming potions.
 */
private val consumeDelay = 1800L

/**
 * Forwards to [drink] if the [Player] is alive and not drinking too quickly.
 */
fun tryDrink(plr: Player, potion: Potion, index: Int) {
    if (plr.lastDrink < consumeDelay || !plr.isAlive) {
        // TODO Confirm duel rule for no drinks.
        return
    }

    plr.interruptAction()
    plr.lastDrink = RESET_TIMER
    plr.lastEat = RESET_TIMER

    plr.inventory.computeIdForIndex(index)
        .map { Item(it) }
        .ifPresent { drink(plr, it, potion, index) }
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
            -1 -> inv.add(index, Item(nextId))
            else -> inv.add(vialItem)
        }

        // Send consume messages.
        val name = itemDef(id)?.name
        plr.sendMessage("You drink some of your $name.")

        val dosesLeft = potion.getDosesLeft(id)
        when {
            dosesLeft > 0 -> plr.sendMessage("You have $dosesLeft doses of potion left.")
            else -> plr.sendMessage("You have finished your potion.")
        }

        // Invoke effects.
        plr.animation(drinkAnimation)
        potion.effect(plr)
    }
}

/**
 * Forwards to [tryDrink] if the item clicked was a potion.
 */
on(ItemFirstClickEvent::class)
    .condition { itemDef(it.id)?.inventoryActions?.get(0) == "Drink" }
    .run {
        val potion = Potion.DOSE_TO_POTION[it.id]
        if (potion != null) {
            tryDrink(it.plr, potion, it.index)
            it.terminate()
        }
    }