package api.combat.death

import api.combat.death.dsl.DeathHookReceiver
import api.predef.*
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.MobDeathTask.DeathStage
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.ControllerKey
import kotlin.reflect.KClass

private typealias DeathHook<T> = DeathHookReceiver<T>.() -> Unit

/**
 * A global handler that manages and dispatches **death hooks**.
 *
 * This system defines how player and NPC deaths are processed at the content layer.
 * Each death hook represents a callback invoked when an entity reaches a particular
 * [DeathStage] within its death sequence.
 *
 * Hooks are separated by type and can be registered for:
 *
 *  - **Players**, using a [ControllerKey] that identifies a gameplay controller or zone
 *  - **NPCs**, using a numeric NPC identifier
 *  - **Defaults**, which act as fallbacks when no hook is found for the entity
 *
 * @author lare96
 */
object DeathHookHandler {

    /**
     * Registered player death hooks mapped to [ControllerKey] types.
     */
    private val playerHooks = HashMap<ControllerKey<*>, DeathHook<Player>>()

    /**
     * Registered NPC death hooks mapped to their respective IDs.
     */
    private val npcHooks = HashMap<Int, DeathHook<Npc>>()

    /**
     * The default player death hook.
     */
    var defaultPlayerHook: DeathHook<Player>? = null

    /**
     * The default NPC death hook.
     */
    var defaultNpcHook: DeathHook<Npc>? = null

    /**
     * Registers a player death hook.
     *
     * @param key The [ControllerKey] identifying the controlling system or zone that owns this hook.
     * @param hook The callback executed during the death sequence.
     */
    fun addPlayerHook(key: ControllerKey<*>, hook: DeathHook<Player>) {
        playerHooks[key] = hook
    }

    /**
     * Registers an NPC death hook.
     *
     * @param id The  NPC identifier that this hook applies to.
     * @param hook The callback executed during the death sequence.
     */
    fun addNpcHook(id: Int, hook: DeathHook<Npc>) {
        npcHooks[id] = hook
    }

    /**
     * Sets the default death hook.
     *
     * @param hook The default player death handler.
     */
    fun <T : Mob> setDefaultHook(type: KClass<T>, hook: DeathHook<T>) {
        when (type) {
            Player::class -> {
                if (defaultPlayerHook == null) {
                    defaultPlayerHook = hook as DeathHook<Player>
                } else {
                    logger.warn("Default player death hook has already been set.")
                }
            }

            Npc::class -> {
                if (defaultNpcHook == null) {
                    defaultNpcHook = hook as DeathHook<Npc>
                } else {
                    logger.warn("Default NPC death hook has already been set.")
                }
            }
        }

    }

    /**
     * Invoked by the combat engine whenever a [Mob] transitions into a new [DeathStage].
     *
     * This method determines which hook to execute for the given [victim], builds a [DeathHookReceiver] for the
     * current context, and invokes the matching callback.
     *
     * @param victim The [Mob] that has died.
     * @param source The [Mob] responsible for the kill.
     * @param stage The current [DeathStage] of the death sequence.
     */
    fun onDeath(victim: Mob, source: Mob, stage: DeathStage) {
        if (victim is Npc) {
            val receiver = DeathHookReceiver(victim, source, stage)
            val lookup = npcHooks[victim.id]
            if (lookup != null) {
                lookup.invoke(receiver)
            } else {
                defaultNpcHook?.invoke(receiver)
            }
        } else if (victim is Player) {
            val receiver = DeathHookReceiver(victim, source, stage)
            val controllerKeys = victim.controllers.keys()
            if (controllerKeys.isEmpty()) {
                // Use default death hook.
                defaultPlayerHook?.invoke(receiver)
                return
            }
            for (key in controllerKeys) {
                playerHooks[key]?.invoke(receiver)
            }
        }
    }
}
