package world.player.command.setSkill

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface

/**
 * A [StandardInterface] used to set skill levels.
 */
class SetLevelInterface : StandardInterface(2808) {
    override fun onOpen(player: Player) {
        player.sendText("Choose the stat to set!", 2810)
    }

    override fun onClose(player: Player) {
        // Return interface to original state!
        player.sendText("Choose the stat you wish to be advanced!", 2810)
    }
}