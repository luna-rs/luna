import CookFood.CookFoodInterface
import api.predef.*
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import io.luna.game.model.mob.inter.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import world.player.skill.cooking.CookingAction
import world.player.skill.cooking.Food

// TODO needs testing!

/**
 * A dialogue that opens the cook food dialogue.
 */
class CookFoodInterface(val food: Food, val usingFire: Boolean) : DialogueInterface(1743) {

    companion object {

        /**
         * The new lines prepended to the name.
         */
        val NEWLINES = "\\n\\n"
    }

    override fun init(plr: Player): Boolean {
        val cooked = food.cooked
        plr.queue(WidgetItemModelMessageWriter(13716, 190, cooked))
        plr.sendText(NEWLINES + itemDef(cooked).name, 13717)
        return true
    }
}

/**
 * The fire objects.
 */
val fireObjects = setOf(2732)

/**
 * The range objects.
 */
val rangeObjects = setOf(114, 2728, 4172, 8750, 2732, 2728, 2729, 2730, 2731, 2859, 3039)

/**
 * Opens the [CookFoodInterface] if the food is non-null.
 */
fun open(msg: ItemOnObjectEvent, food: Food?, usingFire: Boolean) {
    if (food != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(CookFoodInterface(food, usingFire))
        msg.terminate()
    }
}

/**
 * Starts the [CookingAction] if [CookFoodInterface] is open.
 */
fun cook(plr: Player, amount: Int) {
    val inter = plr.interfaces.get(CookFoodInterface::class)
    if (inter != null) {
        val food = inter.food
        val usingFire = inter.usingFire
        val newAmt = if (amount == -1) plr.inventory.computeAmountForId(food.raw) else amount
        plr.submitAction(CookingAction(plr, food, usingFire, newAmt))
    }
}

/**
 * Use raw food on fire and stove.
 */
on(ItemOnObjectEvent::class)
    .condition { fireObjects.contains(objectId) }
    .then { open(this, Food.RAW_TO_FOOD[itemId], true) }

on(ItemOnObjectEvent::class)
    .condition { rangeObjects.contains(objectId) }
    .then { open(this, Food.RAW_TO_FOOD[itemId], false) }

/**
 * Button clicks from the [CookFoodInterface].
 */
button(13720) { cook(plr, 1) }
button(13719) { cook(plr, 5) }
button(13717) { cook(plr, -1) }
button(13718) {
    plr.interfaces.open(object : AmountInputInterface() {
        override fun onAmountInput(player: Player, value: Int) = cook(plr, value)
    })
}
