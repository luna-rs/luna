package game.skill.cooking.churn

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Opens the churn menu for the first ingredient the player carries, milk before cream before butter.
 */
fun open(plr: Player, churn: GameObject, chosen: Dairy? = null) {
    val from = chosen ?: Dairy.INGREDIENTS.firstOrNull { plr.inventory.contains(it.id) }
    if (from == null) {
        plr.sendMessage("You need some milk, cream or butter to use the churn.")
        return
    }
    plr.overlays.open(ChurnInterface(from, churn))
}

Dairy.CHURNS.forEach { churn ->
    object1(churn) { open(plr, gameObject) }
    Dairy.INGREDIENTS.forEach { from -> useItem(from.id).onObject(churn) { open(plr, gameObject, from) } }
}

ChurnInterface.BUTTONS.forEach { (button, option) ->
    button(button) {
        val menu = plr.overlays[ChurnInterface::class]
        if (menu != null && menu.from == option.first) {
            plr.overlays.closeWindows(false)
            plr.submitAction(ChurnAction(plr, menu.churn, menu.from, menu.products()[option.second]))
        }
    }
}
