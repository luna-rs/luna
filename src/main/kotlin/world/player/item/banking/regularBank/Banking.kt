package world.player.item.banking.regularBank

import api.predef.*
import io.luna.game.model.def.*

/**
 * Holds utility functions related to banking.
 */
object Banking {

    /**
     * ID of the default banker npc.
     */
    val DEFAULT_BANKER: Int = 494

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
        for (gameObj in world.objects) {
            val name = gameObj.definition.name
            val actions = gameObj.definition.actions
            if ((name.equals("Bank booth") || name.equals("Bank chest")) &&
                (actions.contains("Open") || actions.contains("Use") || actions.contains("Use-quickly"))) {
                loadObjects += gameObj.id
            }
        }
    }

    /**
     * Loads all banking npcs based on definitions from the cache.
     */
    internal fun loadBankingNpcs() {
        NpcDefinition.ALL
            .filter { npcDefinition -> npcDefinition?.name?.contains("Banker", ignoreCase = true) ?: false }
            .forEach({ npcDefinition -> loadBankingNpcs.add(npcDefinition.id)})
    }
}