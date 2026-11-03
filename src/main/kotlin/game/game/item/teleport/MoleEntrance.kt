package game.item.teleport

import engine.obj.LadderDestination
import io.luna.game.action.impl.LockedAction
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player

/**
 * The exact Falador mound descent. Darkness is a consequence below, never an admission requirement.
 * The owned delayed action cannot move a removed, dead or externally relocated passenger.
 *
 * @author lare96
 */
object MoleEntrance {
    const val SPADE = 952
    private val spot = SpadeDigSpot.FALADOR_MOLE_HILLS

    fun enter(player: Player): Boolean {
        val origin = player.position
        fun ready(): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
            player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }
        if (!ready() || player.isLocked || !player.inventory.contains(SPADE) ||
            SpadeDigSpot.forPosition(origin) != spot || !LadderDestination.canLand(spot.landing)) return false
        player.submitAction(object : LockedAction(player, false, 2), DeathInterruptible {
            override fun beforeLock(): Boolean = ready() && !player.isLocked && player.position == origin &&
                player.inventory.contains(SPADE)

            override fun onLock() {
                player.walking.clear()
                player.overlays.closeWindows()
            }

            override fun run(): Boolean {
                if (ready() && player.position == origin && LadderDestination.canLand(spot.landing)) {
                    player.move(spot.landing)
                    spot.arrival?.let { player.sendMessage(it) }
                }
                return true
            }
        })
        return true
    }
}
