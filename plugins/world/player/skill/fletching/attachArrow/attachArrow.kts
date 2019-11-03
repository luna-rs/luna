import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * An [InventoryAction] that will attach arrowtips to headless arrows.
 */
class MakeArrowAction(plr: Player,
                      val arrow: Arrow,
                      makeTimes: Int) : InventoryAction(plr, true, 3, makeTimes) {

    /**
     * The amount of arrows to make in this set.
     */
    var setAmount = 0

    override fun add() = listOf(Item(arrow.id, setAmount))
    override fun remove(): List<Item> {
        val tipItem = Item(arrow.tip, setAmount)
        val withItem = Item(arrow.with, setAmount)
        return listOf(tipItem, withItem)
    }

    override fun executeIf(start: Boolean): Boolean {
        return when {

            // Check fletching level.
            mob.fletching.level < arrow.level -> {
                mob.sendMessage("You need a Fletching level of ${arrow.level} to attach this.")
                false
            }

            // Check if there's enough materials.
            else -> {
                val withCount = mob.inventory.computeAmountForId(arrow.with)
                val tipCount = mob.inventory.computeAmountForId(arrow.tip)
                setAmount = Integer.min(withCount, tipCount)
                setAmount = Integer.min(setAmount, Arrow.SET_AMOUNT)
                setAmount != 0
            }
        }
    }

    override fun execute() {
        val withName = itemDef(arrow.with).name
        val tipName = itemDef(arrow.tip).name
        mob.sendMessage("You attach the $tipName to the $withName.")

        mob.fletching.addExperience(arrow.exp * setAmount)
    }

    override fun ignoreIf(other: Action<*>) =
        when (other) {
            is MakeArrowAction -> arrow == other.arrow
            else -> false
        }
}

/**
 * Opens a [MakeItemDialogueInterface] for making arrows.
 */
fun openInterface(msg: ItemOnItemEvent, arrow: Arrow?) {
    if (arrow != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(object : MakeItemDialogueInterface(arrow.id) {
            override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeArrowAction(plr, arrow, forAmount))
        })
    }
}

/**
 * Intercept item on item event to open interface.
 */
on(ItemOnItemEvent::class) {
    when (Arrow.HEADLESS) {
        targetId -> openInterface(this, Arrow.TIP_TO_ARROW[usedId])
        usedId -> openInterface(this, Arrow.TIP_TO_ARROW[targetId])
    }
}

/**
 * Intercept even specifically for [Arrow.HEADLESS_ARROW], because it differs from others.
 */
Arrow.HEADLESS_ARROW.apply {
    on(ItemOnItemEvent::class)
        .filter { matches(tip, with) }
        .then { openInterface(this, this@apply) }
}