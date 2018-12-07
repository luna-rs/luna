import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.items.fillWater.Fill

/**
 * Fills all items in an inventory with water.
 */
class FillAction(val evt: ItemOnObjectEvent,
                 val fill: Fill,
                 var amount: Int) : ProducingAction(evt.plr, true, 2) {

    companion object {

        /**
         * The water filling animation.
         */
        val ANIMATION = Animation(832)
    }


    override fun remove() = fill.emptyItem
    override fun add() = fill.filledItem
    override fun onProduce() {
        mob.animation(ANIMATION)
        amount--
        if (amount == 0) {
            interrupt()
        }
    }

    override fun isEqual(other: Action<*>): Boolean {
        return when (other) {
            is FillAction -> evt.objectId == other.evt.objectId &&
                    fill == other.fill
            else -> false
        }
    }
}

/**
 * Set of water source identifiers.
 */
val waterSources: Set<Int> = hashSetOf(153, 879, 880, 34579, 2864, 6232, 878, 884, 3359, 3485, 4004, 4005,
                                       5086, 6097, 8747, 8927, 9090, 6827, 3460)

/**
 * Opens the [MakeItemDialogueInterface] for selecting how many items to fill.
 */
fun openInterface(msg: ItemOnObjectEvent, plr: Player, fill: Fill) {
    plr.interfaces.open(object : MakeItemDialogueInterface(fill.filled) {
        override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(FillAction(msg, fill, forAmount))
    })
}

/**
 * Performs a lookup for the fillable item, and attempts to call [openInterface].
 */
fun tryFill(msg: ItemOnObjectEvent) {
    val fill = Fill.EMPTY_TO_FILL[msg.itemId]
    if (fill != null) {
        openInterface(msg, msg.plr, fill)
        msg.terminate()
    }
}

/**
 * Fills items if they are fillable and if the object used with is a water source.
 */
on(ItemOnObjectEvent::class)
    .condition { waterSources.contains(objectId) }
    .then { tryFill(this) }