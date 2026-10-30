package game.content.barrows

import com.google.common.collect.ImmutableSet
import api.attr.Attr
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
 * Moving between crypts and tunnels does not reset run progress or the prayer-drain countdown.
 *
 * @author lare96
 */
object BarrowsAreaListener : PlayerAreaListener() {
    private var Player.drainTicks by Attr.int { BarrowsDrain.INTERVAL }

    override fun enter(plr: Player) {
        plr.drainTicks = BarrowsDrain.INTERVAL
        plr.queue(MinimapStateMessageWriter(State.CONCEALED))
        plr.overlays.open(BarrowsOverlay())
        BarrowsCollapse.enter(plr)
    }

    override fun exit(plr: Player) {
        plr.drainTicks = 0
        BarrowsCollapse.exit(plr)
        BarrowsEncounters.clear(plr)
        plr.queue(MinimapStateMessageWriter(State.NORMAL))
        // Another area may already have opened its overlay during this position change.
        if (plr.overlays.has(BarrowsOverlay::class.java)) {
            plr.overlays.closeWalkable()
        }
    }

    override fun process(plr: Player) {
        BarrowsCollapse.process(plr)
        if (plr.isAlive && --plr.drainTicks <= 0) {
            plr.drainTicks = BarrowsDrain.INTERVAL
            BarrowsDrain.drain(plr)
        }
    }

    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(
        Area.of(3520, 9664, 3583, 9727, 0),
        Area.of(3520, 9664, 3583, 9727, 1),
        Area.of(3520, 9664, 3583, 9727, 2),
        Area.of(3520, 9664, 3583, 9727, 3)
    )
}
