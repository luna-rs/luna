import api.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.items.consume.food.Food

/**
 * The "last_eat_timer" attribute.
 */
private var Player.lastEat by TimerAttr("last_eat_timer")

/**
 * Mappings of [Food.id] to [Food].
 */
private val foodMap = Food.values().map { it.id to it }.toMap()

/**
 * The animation played when eating food.
 */
private val eatAnimation = Animation(829)

/**
 * Forwards to [eat] if the [Player] is alive and not eating too quickly.
 */
fun tryEat(plr: Player, food: Food, index: Int) {
    if (plr.lastEat < food.longDelay() || !plr.isAlive) {
        // TODO Confirm duel rule for no food.
        return
    }

    plr.interruptAction()
    plr.lastEat = RESET_TIMER
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
        if (nextId != -1) {
            inv.add(index, Item(nextId))
        }

        // Send consume messages.
        val consumeName = eatItem.itemDef.name
        plr.sendMessage(food.consumeMessage(consumeName))
        plr.animation(eatAnimation)
        food.effect(plr)

        // Increase HP.
        val skill = plr.skill(SKILL_HITPOINTS)
        if (skill.level < skill.staticLevel) {
            skill.addLevels(food.heal, false)
            plr.sendMessage(food.healMessage(consumeName))
        }
    }
}

/**
 * Forwards to [tryEat] if the item clicked was food.
 */
on(ItemFirstClickEvent::class)
    .condition { itemDef(it.id)?.inventoryActions?.get(0) == "Eat" }
    .run {
        val food = foodMap[it.id]
        if (food != null) {
            tryEat(it.plr, food, it.index)
        }
    }
