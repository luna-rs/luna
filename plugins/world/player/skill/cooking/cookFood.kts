package world.player.skill.cooking

import api.predef.*
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface

/**
 * The fire objects.
 */
val fires = setOf(2732)

/**
 * The range objects.
 */
val ranges = setOf(114, 2728, 4172, 8750, 2732, 2728, 2729, 2730, 2731, 2859, 3039)

/**
 * Opens the [CookingInterface] if the food is non-null.
 */
fun open(msg: ItemOnObjectEvent, food: Food?, usingFire: Boolean) {
    if (food != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(CookingInterface(food, usingFire))
    }
}

/**
 * Starts the [CookingAction] if [CookingInterface] is open.
 */
fun cook(plr: Player, amount: Int? = null) {
    val inter = plr.interfaces.get(CookingInterface::class)
    if (inter != null) {
        val food = inter.food
        val usingFire = inter.usingFire
        val newAmt = amount ?: plr.inventory.computeAmountForId(food.raw)
        plr.submitAction(CookingAction(plr, food, usingFire, newAmt))
    }
}

/**
 * Use raw food on fire and stove.
 */
on(ItemOnObjectEvent::class)
    .filter { fires.contains(objectId) }
    .then { open(this, Food.RAW_TO_FOOD[itemId], true) }

on(ItemOnObjectEvent::class)
    .filter { ranges.contains(objectId) }
    .then { open(this, Food.RAW_TO_FOOD[itemId], false) }

/**
 * Button clicks from the [CookingInterface].
 */
button(13720) { cook(plr, 1) }
button(13719) { cook(plr, 5) }
button(13717) { cook(plr) }
button(13718) {
    plr.interfaces.open(object : AmountInputInterface() {
        override fun onAmountInput(player: Player, value: Int) = cook(plr, value)
    })
}
