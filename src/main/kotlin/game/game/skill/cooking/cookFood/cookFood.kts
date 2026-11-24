package game.skill.cooking.cookFood

import api.predef.*
import api.predef.ext.*
import game.skill.cooking.cookFood.Cooking.COOKING_OBJECTS
import game.skill.cooking.cookFood.Cooking.FIRES
import game.skill.cooking.cookFood.MakeWineActionItem.Companion.wineFermentTask
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.`object`.GameObject

/**
 * Opens the [CookingInterface] if the recipe accepts the cooking source.
 */
fun open(plr: Player, obj: GameObject, food: Food) {
    if (!food.cooksOn(obj.id)) {
        plr.sendMessage(if (food == Food.BAKED_POTATO) "This has to be cooked on a range." else food.source.refusal)
        return
    }
    plr.overlays.open(CookingInterface(food, obj.id in FIRES, obj))
}

/**
 * Starts the [CookFoodActionItem] if the selected cooking source is still within reach.
 */
fun cook(plr: Player, amount: Int? = null) {
    val inter = plr.overlays[CookingInterface::class] ?: return
    // Close the menu now, so another click in the same tick finds it shut and starts nothing.
    plr.overlays.closeWindows(false)

    val cookObj = inter.cookObj
    if (cookObj.state != EntityState.ACTIVE ||
        !world.collisionManager.reached(plr, cookObj, InteractionPolicy.STANDARD_SIZE)) {
        plr.sendMessage("I can't reach that!")
        return
    }

    val food = inter.food
    val newAmt = amount ?: plr.inventory.computeAmountForId(food.raw)
    plr.submitAction(CookFoodActionItem(plr, cookObj, food, inter.usingFire, newAmt))
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
Food.RAW_TO_FOOD.forEach { (rawId, food) ->
    COOKING_OBJECTS.forEach { sourceId ->
        useItem(rawId).onObject(sourceId) { open(plr, gameObject, food) }
    }
}

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
