package engine.engine.widget.make

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.inter.AmountInputInterface

/**
 * A model that runs the action based on the item's index and amount.
 *
 * @author lare96
 */
class MakeItemOption(val amount: Int, var index: Int) {
    fun run(plr: Player, inter: MakeItemDialogueInterface) {
        if (amount == -1) {
            // Make <x> option.
            plr.interfaces.open(object : AmountInputInterface() {
                override fun onAmountInput(player: Player, value: Int) {
                    plr.interfaces.close()
                    inter.makeItemIndex(plr, index, value)
                }
            })
        } else {
            // Make specific amount option.
            plr.interfaces.close()
            inter.makeItemIndex(plr, index, amount)
        }
    }
}
