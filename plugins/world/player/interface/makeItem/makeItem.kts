import MakeItem.MakeItemOption
import api.predef.*
import io.luna.game.event.button.ButtonClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.inter.AmountInputInterface

/**
 * A model representing an item on a [MakeItemDialogueInterface] interface.
 */
class ButtonIndex(val make1: Int,
                  val make5: Int,
                  val make10: Int,
                  val makeX: Int,
                  val index: Int) {

    constructor(make1: Int, index: Int) :
            this(make1, make1 - 1, make1 - 2, make1 - 3, index)

    /**
     * Computes a list of [MakeItemOption] instances from this item.
     */
    fun options(): List<Pair<Int, MakeItemOption>> {
        val make1Action = Pair(make1, MakeItemOption(1, index))
        val make5Action = Pair(make5, MakeItemOption(5, index))
        val make10Action = Pair(make10, MakeItemOption(10, index))
        val makeXAction = Pair(makeX, MakeItemOption(-1, index))
        return listOf(make1Action,
                      make5Action,
                      make10Action,
                      makeXAction)
    }
}

/**
 * A model that runs the action based on the item's index and amount.
 */
class MakeItemOption(val amount: Int, var index: Int) {
    fun run(plr: Player, inter: MakeItemDialogueInterface) {
        if (amount == -1) {
            // Make <x> option.
            plr.interfaces.open(object : AmountInputInterface() {
                override fun onAmountInput(player: Player, value: Int) {
                    inter.makeItemIndex(plr, index, value)
                    plr.interfaces.close()
                }
            })
        } else {
            // Make specific amount option.
            inter.makeItemIndex(plr, index, amount)
            plr.interfaces.close()
        }
    }
}

/**
 * A list of button ids and the indexes that they correspond to.
 */
val buttonList = listOf(
        ButtonIndex(make1 = 8893, index = 0),
        ButtonIndex(make1 = 8874, index = 0),
        ButtonIndex(make1 = 8878, index = 1),
        ButtonIndex(make1 = 8889, index = 0),
        ButtonIndex(make1 = 8893, index = 1),
        ButtonIndex(make1 = 8897, index = 2),
        ButtonIndex(make1 = 8909, index = 0),
        ButtonIndex(make1 = 8913, index = 1),
        ButtonIndex(make1 = 8917, index = 2),
        ButtonIndex(make1 = 8921, index = 3),
        ButtonIndex(make1 = 8949, index = 0),
        ButtonIndex(make1 = 8953, index = 1),
        ButtonIndex(make1 = 8957, index = 2),
        ButtonIndex(make1 = 8961, index = 3),
        ButtonIndex(make1 = 8965, index = 4))

/**
 * A mapping of every option's button to its [MakeItemOption].
 */
val buttonMap = buttonList.flatMap { it.options() }.toMap()

/**
 * Runs the [MakeItemOption] after checking the item length.
 */
fun makeItem(msg: ButtonClickEvent, inter: MakeItemDialogueInterface, action: MakeItemOption) {

    // Because 1 and 3 use the same interface, but different indexes.
    fun checkLength() {
        when {
            inter.length == 1 -> action.index = 0
            inter.length == 3 -> action.index = 1
        }
    }

    when (msg.id) {
        8893, 8892, 8891, 8890 -> checkLength()
    }
    action.run(msg.plr, inter)
}

/**
 * Listens for button clicks on the [MakeItemDialogueInterface].
 */
on(ButtonClickEvent::class)
    .condition { plr.interfaces.isOpen(MakeItemDialogueInterface::class) }
    .then {
        val action = buttonMap[id]
        if (action != null) {
            val inter = plr.interfaces.get(MakeItemDialogueInterface::class)!!
            makeItem(this, inter, action)
            terminate()
        }
    }