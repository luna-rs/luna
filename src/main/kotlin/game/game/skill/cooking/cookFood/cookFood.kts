package game.skill.cooking.cookFood

import api.predef.*
import api.predef.ext.*
import game.skill.cooking.cookFood.Cooking.FIRES
import game.skill.cooking.cookFood.Cooking.RANGES
import game.skill.cooking.cookFood.MakeWineActionItem.Companion.wineFermentTask
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.`object`.GameObject

/**
 * Opens the [CookingInterface] if the food is non-null.
 */
fun open(msg: ItemOnObjectEvent, obj: GameObject, food: Food?, usingFire: Boolean) {
    if (food != null) {
        val interfaces = msg.plr.overlays
        interfaces.open(CookingInterface(food, usingFire, obj))
    }
}

/**
 * Starts the [CookFoodActionItem] if [CookingInterface] is open.
 */
fun cook(plr: Player, amount: Int? = null) {
    val inter = plr.overlays[CookingInterface::class]
    if (inter != null) {
        val food = inter.food
        val usingFire = inter.usingFire
        val newAmt = amount ?: plr.inventory.computeAmountForId(food.raw)
        plr.submitAction(CookFoodActionItem(plr, inter.cookObj, food, usingFire, newAmt))
    }
}

/**
 * Check for unfermented wines on login.
 */
on(LoginEvent::class) {
    plr.wineFermentTask = FermentWineTask(plr)
    world.schedule(plr.wineFermentTask)
}

/**
 * Use raw food on fire and stove.
 */
on(ItemOnObjectEvent::class)
    .filter { FIRES.contains(objectId) }
    .then { open(this, gameObject, Food.RAW_TO_FOOD[usedItemId], true) }

on(ItemOnObjectEvent::class)
    .filter { RANGES.contains(objectId) }
    .then { open(this, gameObject, Food.RAW_TO_FOOD[usedItemId], false) }

/**
 * Button clicks from the [CookingInterface].
 */
button(13720) { cook(plr, 1) }
button(13719) { cook(plr, 5) }
button(13717) { cook(plr) }
button(13718) {
    plr.overlays.open(object : NumberInput() {
        override fun input(player: Player, value: Int) = cook(plr, value)
    })
}
