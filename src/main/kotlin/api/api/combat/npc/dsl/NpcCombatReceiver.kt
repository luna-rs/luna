package api.combat.npc.dsl

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * DSL receiver used to register attack and defence hooks for an NPC combat script.
 *
 * This class stores the hook callbacks produced by the DSL so they can later be invoked by the NPC combat system
 * for the configured attacker.
 *
 * @author lare96
 */
class NpcCombatReceiver {

    /**
     * Attack hook callback.
     *
     * Defaults to the NPC's standard combat attack when no custom attack hook is registered.
     */
    var attack: NpcAttackCombatHookReceiver.() -> CombatAttack<out Npc> = { default() }

    /**
     * Defence hook callback.
     *
     * Defaults to a no-op when no custom defence hook is registered.
     */
    var defend: NpcDefenceCombatHookReceiver.() -> Unit = { }

    /**
     * Registers the attack hook for this NPC combat receiver.
     *
     * The supplied callback is used to build the combat attack that should be executed when this NPC attacks.
     *
     * @param attackSupplier The callback that produces the attack to execute.
     */
    fun attack(attackSupplier: NpcAttackCombatHookReceiver.() -> CombatAttack<out Npc>) {
        attack = attackSupplier
    }

    /**
     * Registers the defence hook for this NPC combat receiver.
     *
     * The supplied callback is invoked when this NPC receives a hit, allowing defence-side effects such as animation
     * overrides or reactive behaviour.
     *
     * @param action The callback to execute when the NPC defends against an attack.
     */
    fun defend(action: NpcDefenceCombatHookReceiver.() -> Unit) {
        defend = action
    }
}