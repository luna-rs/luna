package world.player.skill.cooking.cookFood

import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.task.Task
import world.player.skill.cooking.prepareFood.IncompleteFood

/**
 * A [Task] implementation that will ferment all wines in the inventory and bank of a [Player].
 */
class FermentWineTask(val plr: Player) : Task(false, 1) {

    override fun execute() {
        if (plr.state == EntityState.INACTIVE) {
            cancel()
            return
        }

        if (executionCounter == 20) {
            val bankCount = plr.bank.replaceAll(1995, 1993)
            val invCount = plr.inventory.replaceAll(1995, 1993)
            if (bankCount > 0 || invCount > 0) {
                val xp = IncompleteFood.UNFERMENTED_WINE.exp * (bankCount + invCount)
                plr.cooking.addExperience(xp)
            }
            cancel()
        }
    }
}