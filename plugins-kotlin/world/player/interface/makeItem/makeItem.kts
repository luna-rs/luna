import MakeItem.MakeItemOption
import api.*
import com.google.common.collect.ImmutableList
import io.luna.game.event.impl.ButtonClickEvent
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


    /**
     * Computes a list of [MakeItemOption] instances from this item.
     */
    fun options(): ImmutableList<Pair<Int, MakeItemOption>> {
        val make1Action = Pair(make1, MakeItemOption(1, index))
        val make5Action = Pair(make5, MakeItemOption(5, index))
        val make10Action = Pair(make10, MakeItemOption(10, index))
        val makeXAction = Pair(makeX, MakeItemOption(-1, index))
        return immutableListOf(make1Action,
                               make5Action,
                               make10Action,
                               makeXAction)
    }
}

/**
 * A model that runs the action based on the item's index and amount.
 */
class MakeItemOption(private val amount: Int, var index: Int) {
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
private val buttonList = immutableListOf(
        ButtonIndex(8893, 8892, 8891, 8890, 0),
        ButtonIndex(8874, 8873, 8872, 8871, 0),
        ButtonIndex(8878, 8877, 8876, 8875, 1),
        ButtonIndex(8889, 8888, 8887, 8886, 0),
        ButtonIndex(8893, 8892, 8891, 8890, 1),
        ButtonIndex(8897, 8896, 8895, 8894, 2),
        ButtonIndex(8909, 8908, 8907, 8906, 0),
        ButtonIndex(8913, 8912, 8911, 8910, 1),
        ButtonIndex(8917, 8916, 8915, 8914, 2),
        ButtonIndex(8921, 8920, 8919, 8918, 3),
        ButtonIndex(8949, 8948, 8947, 8946, 0),
        ButtonIndex(8953, 8952, 8951, 8950, 1),
        ButtonIndex(8957, 8956, 8955, 8954, 2),
        ButtonIndex(8961, 8960, 8959, 8958, 3),
        ButtonIndex(8965, 8964, 8963, 8962, 4))

/**
 * A mapping of every option's button to its [MakeItemOption].
 */
private val buttonMap = buttonList.flatMap { it.options() }.toMap()

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
on(ButtonClickEvent::class).run {
    val interfaces = it.plr.interfaces
    val buttonAction = buttonMap[it.id]

    if (buttonAction != null) {
        val inter = interfaces.get(MakeItemDialogueInterface::class)
        when (inter) {
            null -> interfaces.close()
            else -> makeItem(it, inter, buttonAction)
        }
        it.terminate()
    }
}