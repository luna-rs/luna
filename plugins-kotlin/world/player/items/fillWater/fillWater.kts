import FillWater.Fill
import api.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * An enum representing items that can be filled with water.
 */
enum class Fill(val empty: Int, val filled: Int) {
    BOWL(empty = 1923,
         filled = 1921),
    VIAL(empty = 229,
         filled = 227),
    BUCKET(empty = 1925,
           filled = 1929),
    CUP(empty = 1980,
        filled = 4458),
    JUG(empty = 1935,
        filled = 1937);

    val emptyItem = arrayOf(Item(empty))
    val filledItem = arrayOf(Item(filled))
}

/**
 * Fills all items in an inventory with water.
 */
class FillAction(private val evt: ItemOnObjectEvent,
                 private val fill: Fill,
                 private var amount: Int) : ProducingAction(evt.plr, true, 2) {

    /**
     * The water filling animation.
     */
    private val fillAnimation = Animation(832)

    override fun remove() = fill.emptyItem
    override fun add() = fill.filledItem
    override fun onProduce() {
        mob.animation(fillAnimation)
        amount -= 1
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

/* Identifiers of various water sources. */
/**
 * Set of water source identifiers.
 */
private val waterSources: Set<Int> = hashSetOf(153, 879, 880, 34579, 2864, 6232, 878, 884, 3359, 3485, 4004, 4005,
                                               5086, 6097, 8747, 8927, 9090, 6827, 3460)

/**
 * Mappings of [Fill.emptyItem] to [Fill].
 */
private val fillMap = Fill.values().map { it.empty to it }.toMap()

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
        val fill = fillMap[it.itemId]
        if (fill != null) {
            openInterface(it, fill)
            it.terminate()
        }
    }