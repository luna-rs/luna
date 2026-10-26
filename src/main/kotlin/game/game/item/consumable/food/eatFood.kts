package game.item.consumable.food

import api.predef.*
import game.player.Sound
import game.player.item.consume.food.Food
import game.player.item.consume.food.Food.Companion.lastEat
import io.luna.game.action.impl.ConsumeAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * The food eating animation.
 */
val eatAnimation = Animation(829)

/**
 * Submits immediate food consumption through [ConsumeAction].
 *
 * A successful throttle check removes the item before applying its effects, healing, and attack delay. Food
 * submitted by client input therefore takes effect before that tick's incoming hits and outgoing combat.
 * A strong action or an active eating throttle prevents the item effects.
 *
 * @param plr The player eating the food.
 * @param eatItem The food item to remove from the inventory.
 * @param food The food definition supplying healing, effects, and the eating throttle.
 * @param index The inventory slot containing the item.
 * @author lare96
 */
fun eat(plr: Player, eatItem: Item, food: Food, index: Int) {
    plr.submitAction(object : ConsumeAction(plr, plr.lastEat, food.delay) {
        override fun execute() {
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
                plr.playSound(Sound.EAT)
                food.effect(plr)
                plr.combat.addFoodDelay(
                    when (food) {
                        Food.KARAMBWAN -> 2
                        else -> 3
                    }
                )

                // Increase HP.
                val hp = plr.hitpoints
                if (hp.level < hp.staticLevel) {
                    hp.adjustLevel(food.heal)
                    plr.sendMessage(food.healMessage(name))
                }
            }
        }
    })
}

// Initialize food eating event listeners here.
Food.ID_TO_FOOD.entries.forEach { (id, food) ->
    item1(id) { eat(plr, Item(id), food, index) }
}
