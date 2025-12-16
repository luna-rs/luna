package api.combat.death.dsl

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.block.Animation

/**
 * A receiver class used during the **post-death stage** of a [DeathHookReceiver].
 *
 * This receiver defines actions that must occur after the mob dies, such as resetting skills and respawning.
 *
 * @property receiver The [DeathHookReceiver] providing the current death context.
 * @see DeathHookReceiver
 * @author lare96
 */
class PostDeathReceiver(val receiver: DeathHookReceiver<*>) {

    /**
     * Resets the victim’s state after death. This ensures that both players and NPCs transition smoothly from the
     * dead state back into an active, functional state within the world.
     */
    fun reset() {
        val victim = receiver.victim
        if (victim is Player) {
            victim.animation(Animation.CANCEL)
            victim.skills.resetAll()
            victim.skullIcon = SkullIcon.NONE
        } else if (victim is Npc) {
            if (victim.respawnTicks > 0 && victim.state == EntityState.INACTIVE) {
                world.scheduleOnce(victim.respawnTicks) {
                    val respawnNpc = Npc(ctx, victim.baseId, victim.basePosition)
                    respawnNpc.respawnTicks = victim.respawnTicks
                    world.npcs.add(respawnNpc)
                }
            }
        }
    }
}