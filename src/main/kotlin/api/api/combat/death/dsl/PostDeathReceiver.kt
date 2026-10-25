package api.combat.death.dsl

import api.predef.*
import api.predef.ext.*
import engine.combat.status.StatusEffectType
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
     * Player reset clears active prayers and their drain state, the manually selected spell, combat and retaliation
     * targets, the last opponent, and the combat timer before restoring skills, fully replenishing run energy,
     * and removing the skull.
     */
    fun reset() {
        val victim = receiver.victim
        if (victim is Player) {
            victim.combat.prayers.deactivateAll()
            victim.combat.magic.selectedSpell = io.luna.game.model.def.CombatSpellDefinition.NONE
            victim.combat.target = null
            victim.combat.autoRetaliateTarget = null
            victim.combat.lastCombatWith = null
            victim.combat.timer.reset()
            victim.animation(Animation.CANCEL)
            victim.skills.resetAll()
            victim.runEnergy = 100.0
            victim.status.remove(StatusEffectType.SKULLED)
            victim.skullIcon = SkullIcon.NONE
        } else if (victim is Npc) {
            if (victim.respawnTicks > 0) {
                // Out of the world but keeping its index, and back as the same NPC.
                victim.despawn()
                world.scheduleOnce(victim.respawnTicks) { victim.respawn() }
            } else {
                world.npcs.remove(victim)
            }
        }
    }
}
