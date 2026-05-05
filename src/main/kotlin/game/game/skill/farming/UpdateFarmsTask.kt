package game.skill.farming

import game.skill.farming.Farming.herbPatches
import io.luna.game.model.mob.*
import io.luna.game.task.*

/**
 * Task responsible for updating players farms. The task runs indefinitely every 60 seconds.
 * It will handle weed growing, plant growing, death and disease.
 * // todo finish
 * 
 * @author hydrozoa
 */
class UpdateFarmsTask(private val players: MobList<Player>) : Task(false, 100) {
    
    override fun execute() {
        players.filterNotNull().forEach({ plr ->
            // update herb patches for weed growing
            plr.herbPatches.forEach({ (location, patch) ->
                patch.update()
            })

            // send new herb patch state
            Farming.sendHerbState(plr)
        })
    }
}