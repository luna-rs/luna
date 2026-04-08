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
 * @param npc The NPC executing the combat hook.
 * @param other The current combat enemy of the NPC.
 */
open class CombatHookReceiver(val npc: Npc, val other: Mob) {

    /**
     * Cached combat context for the attacking NPC.
     */
    val combat: NpcCombatContext = npc.combat

    /**
     * Cached NPC definition for the attacker.
     */
    val def: NpcDefinition = npc.def()

    /**
     * Cached combat definition for the attacker.
     */
    val combatDef: NpcCombatDefinition = npc.combatDef()
}