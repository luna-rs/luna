package api.combat.death.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.MobDeathTask.DeathStage

/**
 * Represents a contextual receiver for handling various stages of an entity’s death event.
 *
 * The [DeathHookReceiver] class is the entry point for death-handling DSLs, providing
 * stage-specific receivers such as:
 * - [PreDeathReceiver] for pre-death logic
 * - [DeathReceiver] for main death logic
 * - [PostDeathReceiver] for cleanup and respawn logic
 *
 * Each stage is determined by the [DeathStage] enumeration within [MobDeathTask]. Only the corresponding stage
 * block is executed when the current stage matches.
 *
 * Example usage:
 * ```
 * DeathHookHandler.addPlayerHook(ControllerKeys.SOME_MINIGAME) {
 *     preDeath {
 *         victim.sendMessage("You lose everything!")
 *     }
 *
 *     death {
 *         val removed = removeAll(false)
 *         if(source is Player) {
 *             source.sendMessage("Enjoy the items, champ!")
 *             source.bank.addAll(removed)
 *         }
 *     }
 *
 *     postDeath {
 *         victim.move(RESPAWN_LOCATION)
 *     }
 * }
 * ```
 *
 * @param T The mob type affected by the death event.
 * @property victim The entity that has died.
 * @property source The entity responsible for causing the death.
 * @property stage The current death stage ([DeathStage.PRE_DEATH], [DeathStage.DEATH], [DeathStage.POST_DEATH]).
 */
class DeathHookReceiver<T : Mob>(val victim: T, val source: Mob, private val stage: DeathStage) {

    /**
     * Executes logic during the pre-death stage. This block runs before the entity officially dies.
     *
     * If [reset] is true, the [PreDeathReceiver.reset] function will be invoked to restore certain baseline values
     * or clear temporary states.
     *
     * @param reset Whether to automatically reset the pre-death state before running [action].
     * @param action The pre-death DSL logic to execute if [stage] equals [DeathStage.PRE_DEATH].
     */
    fun preDeath(reset: Boolean = true, action: PreDeathReceiver.() -> Unit) {
        if (stage == DeathStage.PRE_DEATH) {
            val preDeathReceiver = PreDeathReceiver(this)
            if (reset) {
                preDeathReceiver.reset()
            }
            action(preDeathReceiver)
        }
    }

    /**
     * Executes logic during the main death stage. This is where the actual death event occurs. The provided
     * [action] is only executed if the current stage is [DeathStage.DEATH].
     *
     * @param action The main death logic to execute.
     */
    fun death(action: DeathReceiver<T>.() -> Unit) {
        if (stage == DeathStage.DEATH) {
            val deathReceiver = DeathReceiver(this)
            action(deathReceiver)
        }
    }

    /**
     * Executes logic during the post-death stage. This block runs after the death event concludes, usually handling
     * cleanup operations.
     *
     * If [reset] is true, the [PostDeathReceiver.reset] method will be invoked before executing [action].
     *
     * @param reset Whether to automatically perform a post-death reset prior to [action].
     * @param action The post-death logic to execute if [stage] equals [DeathStage.POST_DEATH].
     */
    fun postDeath(reset: Boolean = true, action: PostDeathReceiver.() -> Unit) {
        if (stage == DeathStage.POST_DEATH) {
            val postDeathReceiver = PostDeathReceiver(this)
            if (reset) {
                postDeathReceiver.reset()
            }
            action(postDeathReceiver)
        }
    }
}
