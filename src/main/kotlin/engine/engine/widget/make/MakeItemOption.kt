package engine.widget.make

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.overlay.NumberInput

/**
 * A model that runs the action based on the item's index and amount.
 *
 * @author lare96
 */
class MakeItemOption(val amount: Int, var index: Int) {
    fun run(plr: Player, inter: MakeItemDialogue) {
        if (amount == -1) {
            // Make <x> option.
            plr.overlays.open(object : NumberInput() {
                override fun input(player: Player, value: Int) {
                    plr.overlays.closeWindows()
                    inter.makeIndex(plr, index, value)
                }
            })
        } else {
            // Make specific amount option.
            plr.overlays.closeWindows()
            inter.makeIndex(plr, index, amount)
        }
    }
}
