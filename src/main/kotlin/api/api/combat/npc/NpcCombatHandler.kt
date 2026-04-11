package api.combat.npc

import api.combat.npc.dsl.NpcAttackCombatHookReceiver
import api.combat.npc.dsl.NpcDefenceCombatHookReceiver
import api.combat.npc.dsl.NpcCombatReceiver
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import kotlin.reflect.KClass

/**
 * Receiver function used to configure an [NpcCombatReceiver] through the combat DSL.
 */
typealias NpcCombatReceiverFunction = NpcCombatReceiver.() -> Unit

/**
 * Central registry and dispatcher for NPC combat hooks.
 *
 * Hooks can be registered either by exact NPC id or by NPC runtime type. At combat time, this handler resolves the
 * matching receiver and delegates attack or defence behavior to the registered DSL callbacks.
 *
 * Type-based registrations are checked before id-based registrations.
 *
 * @author lare96
 */
object NpcCombatHandler {

    /**
     * Combat hook receivers keyed by NPC runtime type.
     */
    val typeMap = HashMap<KClass<*>, NpcCombatReceiver>()

    /**
     * Combat hook receivers keyed by NPC id.
     */
    val idMap = HashMap<Int, NpcCombatReceiver>()

    /**
     * Registers a combat receiver for one or more NPC ids.
     *
     * A single receiver instance is created, populated by the supplied DSL function, and then stored for each
     * provided id.
     *
     * @param ids The NPC ids that should use the registered combat receiver.
     * @param receiverFunction The DSL function used to configure the receiver.
     */
    fun combat(vararg ids: Int, receiverFunction: NpcCombatReceiverFunction) {
        val receiver = NpcCombatReceiver() // Generate new receiver holder.
        receiverFunction(receiver) // Populate it from script.
        for (id in ids) {
            idMap[id] = receiver // Add it to our map.
        }
    }

    /**
     * Registers a combat receiver for one or more NPC types.
     *
     * A single receiver instance is created, populated by the supplied DSL function, and then stored for each provided
     * type.
     *
     * @param types The NPC types that should use the registered combat receiver.
     * @param receiverFunction The DSL function used to configure the receiver.
     */
    fun combat(vararg types: KClass<in Npc>, receiverFunction: NpcCombatReceiverFunction) {
        val receiver = NpcCombatReceiver() // Generate new receiver holder.
        receiverFunction(receiver) // Populate it from script.
        for (type in types) {
            typeMap[type] = receiver // Add it to our map.
        }
    }

    /**
     * Supplies the combat attack that an NPC should use against a victim.
     *
     * If a registered receiver exists, its attack hook is invoked. Otherwise, the NPC's default combat attack is
     * returned.
     *
     * @param attacker The NPC performing the attack.
     * @param victim The current combat target.
     * @return The resolved combat attack.
     */
    fun supplyAttack(attacker: Npc, victim: Mob): CombatAttack<out Npc> {
        return resolve(attacker) {
            val receiver = NpcAttackCombatHookReceiver(attacker, victim)
            it.attack(receiver) // Invoke attack receiver.
        } ?: attacker.combat.getDefaultAttack(victim) // Otherwise, do default attack.
    }

    /**
     * Consumes an incoming hit against an NPC and applies any registered defence hook.
     *
     * If a receiver is registered, its defence hook is invoked. When the defence hook provides an animation
     * override, that animation is played. Otherwise, the NPC's default defence behavior is used.
     *
     * @param npc The NPC receiving the hit.
     * @param other The attacking mob.
     * @param action The action that launched the damage.
     */
    fun consumeDefence(npc: Npc, other: Mob, action: CombatDamageAction) {
        resolve(npc) {
            val receiver = NpcDefenceCombatHookReceiver(npc, other, action)
            it.defend(receiver) // Invoke defence receiver, handle defence animation if necessary.
            action.damage = receiver.damage
            if (receiver.animationId != null) {
                npc.animation(Animation(receiver.animationId!!))
            }
        } ?: npc.combat.handleDefaultDefence() // Otherwise do default defence.
    }

    /**
     * Resolves the registered combat receiver for an NPC and performs an action with it.
     *
     * Type-based registrations are checked first. If none exists, the handler falls back to an id-based lookup.
     *
     * @param attacker The NPC whose receiver should be resolved.
     * @param action The action to perform with the resolved receiver.
     * @return The result of [action], or `null` if no receiver is registered.
     */
    private fun <T> resolve(attacker: Npc, action: (NpcCombatReceiver) -> T): T? {
        val receiver = typeMap.getOrDefault(attacker::class, idMap[attacker.id])
        if (receiver != null) {
            return action(receiver)
        }
        return null
    }
}