package world.player.item.consumable.potion

import api.attr.Attr
import api.predef.*
import io.luna.game.action.ThrottledAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import world.player.item.consume.potion.Potion

/**
 * Throttles how often a player can drink potions.
 */
val Player.lastDrink by Attr.timeSource()

/**
 * Item instance for empty vials.
 */
val vialItem = Item(229)

/**
 * The consume potion animation.
 */
val drinkAnimation = Animation(829)

/**
 * The tick delay between consuming potions.
 */
val consumeDelay = 3

/**
 * Drinks a potion and applies the appropriate effects to the player.
 */
fun drink(plr: Player, drinkItem: Item, potion: Potion, index: Int) {
    plr.submitAction(object : ThrottledAction<Player>(plr, plr.lastDrink, consumeDelay) {
        override fun execute() {
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

                when (val dosesLeft = potion.getDosesLeft(id)) {
                    0 -> plr.sendMessage("You have finished your potion.")
                    1 -> plr.sendMessage("You have 1 dose left.")
                    else -> plr.sendMessage("You have $dosesLeft doses left.")
                }

                // Invoke effects.
                plr.animation(drinkAnimation)
                potion.effect(plr)
            }
        }
    })
}

// Initialize potion drinking event listeners here.
Potion.DOSE_TO_POTION.entries.forEach { (id, potion) ->
    item1(id) { drink(plr, Item(id), potion, index) }
}