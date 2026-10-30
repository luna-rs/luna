package game.content.darkness

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener

/**
 * Hands players in a [DarkArea] to a [DarknessController] while they're there.
 *
 * @author TheLining
 */
object DarknessAreaListener : PlayerAreaListener() {

    override fun computeLocatables(): ImmutableSet<Locatable> =
        ImmutableSet.copyOf(DarkArea.entries.flatMap { it.areas.asList() })

    override fun enter(plr: Player) {
        plr.controllers.register(DarknessController(plr))
    }

    override fun exit(plr: Player) {
        if (plr.controllers.primary is DarknessController) {
            plr.controllers.unregister()
        }
    }
}
