package api.combat.npc.dsl

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc

/**
 * DSL receiver used when an NPC defence combat hook is executed.
 *
 * This receiver exposes the defending NPC, the attacking mob, and any shared combat-hook helpers inherited from
 * [CombatHookReceiver]. It can also be used to override the defence animation that should play for the current hit.
 *
 * @param npc The NPC being attacked and processing the defence hook.
 * @param other The mob that initiated the attack.
 * @author lare96
 */
class DefenceCombatHookReceiver(npc: Npc, other: Mob) : CombatHookReceiver(npc, other) {

    /**
     * Optional defence animation override for this hook execution.
     *
     * When set, this animation should be used instead of the NPC's default block or defence animation.
     */
    var animationId: Int? = npc.combatDef.defenceAnimation
}