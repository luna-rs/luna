package world.player.item.consumable.food

import api.predef.*
import io.luna.game.action.ThrottledAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import world.player.item.consume.food.Food
import world.player.item.consume.food.Food.Companion.lastEat

/**
 * The food eating animation.
 */
val eatAnimation = Animation(829)

/**
 * Consumes food and restores health, plus any other effects.
 */
fun eat(plr: Player, eatItem: Item, food: Food, index: Int) {
    plr.submitAction(object : ThrottledAction<Player>(plr, plr.lastEat, food.delay) {
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
                food.effect(plr)

                // Increase HP.
                val hp = plr.hitpoints
                if (hp.level < hp.staticLevel) {
                    hp.addLevels(food.heal, false)
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
