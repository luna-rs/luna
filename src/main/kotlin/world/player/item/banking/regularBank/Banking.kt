package world.player.item.banking.regularBank

import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.NpcDefinition

/**
 * Holds utility functions related to banking.
 */
object Banking {

    /**
     * ID of the default banker npc.
     */
    const val DEFAULT_BANKER = 494

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
    internal fun loadBankingObjects() {
        for (definition in GameObjectDefinition.ALL) {
            val name = definition.name
            val actions = definition.actions
            if ((name.equals("Bank booth") || name.equals("Bank chest")) &&
                (actions.contains("Open") || actions.contains("Use") || actions.contains("Use-quickly"))
            ) {
                loadObjects += definition.id
            }
        }
    }

    /**
     * Loads all banking npcs based on definitions from the cache.
     */
    internal fun loadBankingNpcs() {
        NpcDefinition.ALL
            .filter { npcDefinition -> npcDefinition?.name?.contains("Banker", ignoreCase = true) ?: false }
            .forEach { npcDefinition -> loadBankingNpcs.add(npcDefinition.id) }
    }
}