package game.content.barrows

import api.attr.Attr
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.net.msg.out.CameraShakeMessageWriter
import io.luna.net.msg.out.ResetCameraMessageWriter
import kotlin.random.Random

/**
 * Restores the saved collapse on entry and releases the camera when leaving the underground area.
 * Contemporary guides confirm periodic minor rock damage, but not its exact cadence/distribution.
 * Ten ticks and 1..2 damage are explicit reconstruction choices, as is the cache's rockfall graphic.
 *
 * @author lare96
 */
object BarrowsCollapse {
    const val INTERVAL = 10
    private var Player.collapseTicks by Attr.int { INTERVAL }
    private var Player.collapseCamera by Attr.boolean()

    fun enter(player: Player) {
        player.collapseTicks = INTERVAL
        if (BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK != 0) {
            player.queue(CameraShakeMessageWriter(0, 5, 0, 0))
            player.collapseCamera = true
        }
    }

    fun exit(player: Player) {
        if (player.collapseCamera) player.queue(ResetCameraMessageWriter())
        player.collapseCamera = false
        player.collapseTicks = INTERVAL
    }

    fun process(player: Player, random: Random = Random.Default) {
        if (!BarrowsAreaListener.inside(player.position) || !player.isAlive ||
            BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK == 0) {
            exit(player)
            return
        }
        if (!player.collapseCamera) enter(player)
        if (player.position.z == 0 && --player.collapseTicks <= 0) {
            player.collapseTicks = INTERVAL
            player.graphic(Graphic(60))
            player.damage(random.nextInt(1, 3))
        }
    }
}
