package game.content.barrows

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener
import io.luna.net.msg.out.MinimapStateMessageWriter
import io.luna.net.msg.out.MinimapStateMessageWriter.State

/**
 * Restores the Barrows display when entering or logging into the underground region, and clears it on exit.
 *
 * Bounds and display behavior follow barrows.rs2 in Lost City's Barrows contribution at
 * 0e7d7a8291d837f48646b2a962f3c5e208e744ad: map square 55,151 on planes 0 through 3.
 * This listener owns presentation only; moving between crypts and tunnels does not reset run progress.
 *
 * @author lare96
 */
object BarrowsAreaListener : PlayerAreaListener() {

    override fun enter(plr: Player) {
        plr.queue(MinimapStateMessageWriter(State.CONCEALED))
        plr.overlays.open(BarrowsOverlay())
    }

    override fun exit(plr: Player) {
        BarrowsEncounters.clear(plr)
        plr.queue(MinimapStateMessageWriter(State.NORMAL))
        // Another area may already have opened its overlay during this position change.
        if (plr.overlays.has(BarrowsOverlay::class.java)) {
            plr.overlays.closeWalkable()
        }
    }

    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(
        Area.of(3520, 9664, 3583, 9727)
    )
}
