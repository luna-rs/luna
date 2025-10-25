package api.combat.death.dsl

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Player.SkullIcon
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag

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
            victim.flags.flag(UpdateFlag.APPEARANCE)
        } else if (victim is Npc) {
            if (victim.isRespawn && victim.state == EntityState.INACTIVE) {
                val respawnTicks = if (victim.respawnTicks < 1)
                    victim.combatDef.map { it.respawnTime }.filter { it > 0 }.orElse(null) else victim.respawnTicks
                if (respawnTicks != null) {
                    world.scheduleOnce(respawnTicks) {
                        world.npcs.add(Npc(ctx, victim.baseId, victim.basePosition).setRespawning())
                    }
                }
            }
        }
    }
}