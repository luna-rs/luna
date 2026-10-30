package game.content.darkness

import game.skill.firemaking.lightSource.LightSource
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.msg.out.MinimapStateMessageWriter
import io.luna.net.msg.out.MinimapStateMessageWriter.State

/**
 * Darkens [plr]'s screen while they're in a dark place, by how much light they carry. In pitch darkness without a
 * light their minimap goes black, they can't see to fight, and insects find them.
 *
 * @author TheLining
 */
class DarknessController(private val plr: Player) : PlayerController(plr) {

    companion object {

        /**
         * The ticks in pitch darkness without a light before the insects are heard, and before they start biting.
         */
        const val INSECTS_HEARD_TICKS = 15L
        const val INSECTS_BITE_TICKS = 30L
    }

    /**
     * The tick [plr] was last left in pitch darkness without a light, or `null` while they have one.
     */
    private var unlitSince: Long? = null

    /**
     * Whether [plr]'s minimap is blacked out.
     */
    private var minimapHidden = false

    override fun register() {
        update()
    }

    override fun unregister() {
        if (plr.overlays.getOverlay(DarknessOverlay::class.java) != null) {
            plr.overlays.closeWalkable()
        }
        hideMinimap(false)
    }

    override fun process() {
        update()
    }

    override fun combat(victim: Mob): Boolean {
        if (isPitchDark()) {
            plr.sendMessage("You can't see to attack anything!")
            return false
        }
        return super.combat(victim)
    }

    /**
     * Whether [plr] is in pitch darkness with no light.
     */
    private fun isPitchDark(): Boolean {
        val area = DarkArea.forPosition(plr.position) ?: return false
        return area.darkness >= DarkArea.PITCH_DARK && LightSource.brightness(plr) == 0
    }

    /**
     * Shows the right shade for [plr]'s light, and lets the insects find them in pitch darkness.
     */
    private fun update() {
        val area = DarkArea.forPosition(plr.position) ?: return
        val brightness = LightSource.brightness(plr)
        showOverlay(DarknessOverlay.forDarkness(area.darkness - brightness))
        val pitchDark = area.darkness >= DarkArea.PITCH_DARK && brightness == 0
        hideMinimap(pitchDark)
        if (pitchDark) {
            insects()
        } else {
            unlitSince = null
        }
    }

    /**
     * Blacks out [plr]'s minimap, or brings it back.
     */
    private fun hideMinimap(hide: Boolean) {
        if (hide != minimapHidden) {
            minimapHidden = hide
            plr.queue(MinimapStateMessageWriter(if (hide) State.CONCEALED else State.NORMAL))
        }
    }

    /**
     * Shows overlay [id], or takes the shade away when it's `null`. Another walkable interface is left alone until it
     * closes.
     */
    private fun showOverlay(id: Int?) {
        val shown = plr.overlays.getOverlay(DarknessOverlay::class.java)
        if (id == null) {
            if (shown != null) {
                plr.overlays.closeWalkable()
            }
        } else if (shown?.id != id && (shown != null || !plr.overlays.containsType(OverlayType.WIDGET_WALKABLE))) {
            plr.overlays.open(DarknessOverlay(id))
        }
    }

    /**
     * Times [plr]'s stay in pitch darkness: first the insects are heard, then they swarm and bite every tick.
     */
    private fun insects() {
        val now = plr.world.currentTick
        val ticks = now - (unlitSince ?: now.also { unlitSince = it })
        when {
            ticks == INSECTS_HEARD_TICKS -> plr.sendMessage("You hear tiny insects skittering over the ground...")
            ticks == INSECTS_BITE_TICKS -> plr.sendMessage("Tiny biting insects swarm all over you!")
            ticks > INSECTS_BITE_TICKS && plr.isAlive -> plr.damage(1)
        }
    }
}
