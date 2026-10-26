package game.item.consumable.potion

import api.attr.Attr
import api.predef.*
import game.player.Sound
import game.player.item.consume.potion.Potion
import io.luna.game.action.impl.ConsumeAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

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
 * Submits immediate potion consumption through [ConsumeAction].
 *
 * A successful throttle check removes the dose, returns the next dose or empty vial, and applies the potion's
 * effects during submission. Potions submitted by client input take effect before that tick's combat turns.
 *
 * @param plr The player drinking the potion.
 * @param drinkItem The potion dose to remove from the inventory.
 * @param potion The potion definition supplying dose progression and effects.
 * @param index The inventory slot containing the dose.
 * @author lare96
 */
fun drink(plr: Player, drinkItem: Item, potion: Potion, index: Int) {
    plr.submitAction(object : ConsumeAction(plr, plr.lastDrink, consumeDelay) {
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
                plr.playSound(Sound.DRINK)
            }
        }
    })
}

// Initialize potion drinking event listeners here.
Potion.DOSE_TO_POTION.entries.forEach { (id, potion) ->
    item1(id) { drink(plr, Item(id), potion, index) }
}
