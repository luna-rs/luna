import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skills.fletching.Arrow

/**
 * A [ProducingAction] that will attach arrowtips to headless arrows.
 */
class MakeArrowAction(plr: Player,
                      val arrow: Arrow,
                      var makeTimes: Int) : ProducingAction(plr, true, 3) {

    /**
     * The fletching skill.
     */
    val fletching = mob.skill(SKILL_FLETCHING)!!

    /**
     * The amount of arrows to make in this set.
     */
    var setAmount = 0

    override fun add() = arrayOf(Item(arrow.id, setAmount))
    override fun remove(): Array<Item> {
        val tipItem = Item(arrow.tip, setAmount)
        val withItem = Item(arrow.with, setAmount)
        return arrayOf(tipItem, withItem)
    }

    override fun canProduce(): Boolean {
        return when {

            // Check fletching level.
            fletching.level < arrow.level -> {
                mob.sendMessage("You need a Fletching level of ${arrow.level} to attach this.")
                false
            }

            // No more actions to execute.
            makeTimes == 0 -> false

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

    override fun onProduce() {
        val withName = itemDef(arrow.with).name
        val tipName = itemDef(arrow.tip).name
        mob.sendMessage("You attach the $tipName to the $withName.")

        fletching.addExperience(arrow.exp * setAmount)
        makeTimes--
    }

    override fun isEqual(other: Action<*>) =
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
        msg.terminate()
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
        .condition { matches(tip, with) }
        .then { openInterface(this, this@apply) }
}