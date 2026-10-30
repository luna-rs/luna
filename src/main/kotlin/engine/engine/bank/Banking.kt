package engine.bank

import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * Holds utility functions related to banking.
 */
object Banking {

    /**
     * ID of the default banker npc.
     */
    const val DEFAULT_BANKER = 494

    /**
     * The two-tile bank-teller approach range used by the 377 bank scripts. Registered bankers can serve customers
     * over a counter; walking to that range still uses ordinary collision, and other NPC interactions keep their
     * own reach policies.
     */
    val BANKER_POLICY = InteractionPolicy(InteractionType.DISTANCE, 2)

    /**
     * The mutable set of banking objects.
     */
    private val loadObjects = mutableSetOf<Int>()

    /**
     * The immutable set of banking objects.
     */
    val bankingObjects: Set<Int> = loadObjects

    /**
     * Mutable set of banking npc ids.
     */
    private val loadBankingNpcs = mutableSetOf<Int>()

    /**
     * Immutable set of banker npc ids.
     */
    val bankingNpcs: Set<Int> = loadBankingNpcs

    /**
     * Loads all banking objects based on definitions from the cache.
     */
    fun loadBankingObjects() {
        for (definition in GameObjectDefinition.ALL) {
            val name = definition.name
            val actions = definition.actions
            if ((name.equals("Bank booth") || name.equals("Bank chest")) &&
                (actions.contains("Open") || actions.contains("Use") || actions.contains("Use-quickly"))
            ) {
                loadObjects += definition.id()
            }
        }
    }

    /**
     * Loads all banking npcs based on definitions from the cache.
     */
    fun loadBankingNpcs() {
        NpcDefinition.ALL
            .filter { npcDefinition -> npcDefinition?.name?.contains("Banker", ignoreCase = true) ?: false }
            .forEach { npcDefinition -> loadBankingNpcs.add(npcDefinition.id()) }
        loadBankingNpcs += 2271 // Emerald benedict.
    }
}
