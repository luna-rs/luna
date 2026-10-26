package game.skill.farming

import io.luna.game.model.mob.*
import io.luna.game.task.*

/**
 * Task responsible for updating the farms of online players. The task runs indefinitely every 60 seconds, bringing
 * each player's patches up to date through [GrowthEngine] and sending the varps that changed. Time spent offline is
 * caught up on login.
 *
 * @author hydrozoa
 */
class UpdateFarmsTask(private val players: MobList<Player>) : Task(false, 100) {

    override fun execute() {
        players.filterNotNull().forEach { plr -> Farming.update(plr) }
    }
}
