package api.combat.death.dsl

import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * A receiver class used during the **pre-death stage** of a [DeathHookReceiver].
 *
 * This receiver defines actions that must occur immediately before a mob’s death sequence begins, such as
 * interrupting queued actions and closing any active interfaces.
 *
 * @property receiver The [DeathHookReceiver] providing the current death context.
 * @see DeathHookReceiver
 * @author lare96
 */
class PreDeathReceiver(val receiver: DeathHookReceiver<*>) {

    /**
     * Resets the state of the dying [DeathHookReceiver.victim] prior to the death sequence. Ensures that all
     * transient activity is halted and the entity is ready for the next stage of processing.
     *
     * This method is typically called automatically from within the [DeathHookReceiver] during the `PRE_DEATH` stage.
     */
    fun reset() {
        val victim = receiver.victim
        if (victim is Player) {
            victim.hitpoints.level = 0
            victim.overlays.closeWindows()
        } else if (victim is Npc) {
            victim.actions.interruptWeak()
        }
    }
}
