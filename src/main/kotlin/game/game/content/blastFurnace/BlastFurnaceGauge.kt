package game.content.blastFurnace

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetAnimationMessageWriter

/**
 * The temperature gauge. Interface 14594 has no client scripts: the 377 cache turns its needle with one sequence
 * per degree (2452 to 2552), so the server animates the needle model.
 *
 * @author TheLining
 */
class BlastFurnaceGauge : StandardInterface(ID) {
    companion object {
        const val ID = 14594
        const val NEEDLE = 14599
        const val NEEDLE_ZERO = 2452

        /**
         * Refreshes the needle only while [player] has the gauge open. The client ignores a repeated sequence,
         * so an unchanged reading costs nothing on screen.
         */
        fun update(player: Player) {
            if (player.overlays.getOverlay(BlastFurnaceGauge::class.java) == null) return
            val temperature = BlastFurnace.temperature(player).coerceIn(0, 100)
            player.queue(WidgetAnimationMessageWriter(NEEDLE, NEEDLE_ZERO + temperature))
        }
    }

    override fun onOpen(player: Player) {
        update(player)
    }
}
