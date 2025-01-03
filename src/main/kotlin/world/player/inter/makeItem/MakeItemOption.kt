package world.player.inter.makeItem

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.inter.AmountInputInterface

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
