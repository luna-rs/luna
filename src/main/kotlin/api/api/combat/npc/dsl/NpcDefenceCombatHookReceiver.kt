package api.combat.npc.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction

/**
 * DSL receiver used when an NPC defence combat hook is executed.
 *
 * This receiver exposes the defending NPC, the attacking mob, and any shared combat-hook helpers inherited from
 * [NpcCombatHookReceiver]. It can also be used to override the defence animation that should play for the current hit.
 *
 * @param npc The NPC being attacked and processing the defence hook.
 * @param other The mob that initiated the attack.
 * @param action The action that will apply the combat damage.
 * @author lare96
 */
class NpcDefenceCombatHookReceiver(npc: Npc, other: Mob, action: CombatDamageAction) :
    NpcCombatHookReceiver(npc, other) {

    /**
     * Optional defence animation override for this hook execution.
     *
     * When set, this animation should be used instead of the NPC's default block or defence animation.
     */
    var animationId: Int? = npc.combatDef().defenceAnimation


    /**
     * Optional damage override for this hook execution.
     *
     * When set, this damage should be used instead of the damage from [CombatDamageAction].
     */
    var damage: CombatDamage? = action.damage


    val source: CombatAttack<*> = action.source
}