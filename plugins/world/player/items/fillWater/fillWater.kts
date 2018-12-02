import api.*
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
class FillAction(private val evt: ItemOnObjectEvent,
                 private val fill: Fill,
                 private var amount: Int) : ProducingAction(evt.plr, true, 2) {

    companion object {

        /**
         * The water filling animation.
         */
        private val ANIMATION = Animation(832)
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
private val waterSources: Set<Int> = hashSetOf(153, 879, 880, 34579, 2864, 6232, 878, 884, 3359, 3485, 4004, 4005,
                                               5086, 6097, 8747, 8927, 9090, 6827, 3460)

/**
 * Opens the [MakeItemDialogueInterface] for selecting how many items to fill.
 */
fun openInterface(msg: ItemOnObjectEvent, fill: Fill) {
    val inter = object : MakeItemDialogueInterface(fill.filled) {
        override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(FillAction(msg, fill, forAmount))
    }
    msg.plr.interfaces.open(inter)
}

/**
 * Fills items if they are fillable and if the object used with is a water source.
 */
on(ItemOnObjectEvent::class)
    .condition { waterSources.contains(it.objectId) }
    .run {
        val fill = Fill.EMPTY_TO_FILL[it.itemId]
        if (fill != null) {
            openInterface(it, fill)
        }
    }