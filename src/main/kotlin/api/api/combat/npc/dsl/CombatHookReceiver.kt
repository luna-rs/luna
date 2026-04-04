package api.combat.npc.dsl

import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.state.NpcCombatContext

/**
 * Base DSL receiver for NPC combat hooks.
 *
 * This receiver exposes the attacking NPC, the current victim, and commonly accessed combat state so hook
 * implementations can use concise property access instead of repeatedly resolving values from the NPC.
 *
 * @param attacker The NPC executing the combat hook.
 * @param victim The current combat target of the NPC.
 * @author lare96
 */
open class CombatHookReceiver(val attacker: Npc, val victim: Mob) {

    /**
     * Cached combat context for the attacking NPC.
     */
    val combat: NpcCombatContext = attacker.combat

    /**
     * Cached NPC definition for the attacker.
     */
    val def: NpcDefinition = attacker.definition

    /**
     * Cached combat definition for the attacker.
     */
    val combatDef: NpcCombatDefinition = attacker.combatDef
}