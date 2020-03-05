package world.player.skill.crafting.hideTanning

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import world.player.skill.crafting.hideTanning.Hide.*

/**
 * The interface opened when tanning hides.
 */
class TanInterface : StandardInterface(14670) {

    companion object {

        /**
         * A list of hides displayed on the interface.
         */
        val HIDES = listOf(
                SOFT_LEATHER,
                HARD_LEATHER,
                SWAMP_SNAKESKIN,
                SNAKESKIN,
                GREEN_D_LEATHER,
                BLUE_D_LEATHER,
                RED_D_LEATHER,
                BLACK_D_LEATHER
        )
    }

    override fun onOpen(plr: Player) {
        var nameWidget = 14777
        var costWidget = 14785
        var modelWidget = 14769
        for (it in HIDES) {
            plr.sendText(it.displayName, nameWidget++)
            plr.sendText("${it.cost} coins", costWidget++)
            plr.queue(WidgetItemModelMessageWriter(modelWidget++, 250, it.hide))
        }
    }
}