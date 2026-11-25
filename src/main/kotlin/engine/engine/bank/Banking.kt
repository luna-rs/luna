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
     * Bankers whose options are handled by their own scripts, so they don't get the generic banker dialogue.
     */
    val scriptedBankers = setOf(2619) // TzHaar-Ket-Zuh.

    /**
     * Loads all banking objects based on definitions from the cache.
     */
    fun loadBankingObjects() {
        for (definition in GameObjectDefinition.ALL) {
            if (bankingDefinition(definition.id()) != null) {
                loadObjects += definition.id()
            }
        }
    }

    /**
     * The definition that makes object [id] a bank: its own, or for an object the client swaps by varbit (such as
     * a booth repaired in a quest), the bank booth it turns into. Clicks on those arrive under the original id.
     */
    fun bankingDefinition(id: Int): GameObjectDefinition? {
        val definition = GameObjectDefinition.ALL.get(id).orElse(null) ?: return null
        val children = definition.varpDef?.childIdList.orEmpty()
            .filter { it != -1 }
            .mapNotNull { GameObjectDefinition.ALL.get(it).orElse(null) }
        return (listOf(definition) + children).firstOrNull { isBankObject(it) }
    }

    /**
     * Whether [definition] is a bank booth or bank chest that can be used.
     */
    private fun isBankObject(definition: GameObjectDefinition): Boolean {
        val name = definition.name
        val actions = definition.actions
        return (name.equals("Bank booth", ignoreCase = true) || name.equals("Bank chest")) &&
            (actions.contains("Open") || actions.contains("Use") || actions.contains("Use-quickly"))
    }

    /**
     * Loads all banking npcs based on definitions from the cache.
     */
    fun loadBankingNpcs() {
        NpcDefinition.ALL
            .filter { npcDefinition -> npcDefinition?.name?.contains("Banker", ignoreCase = true) ?: false }
            .forEach { npcDefinition -> loadBankingNpcs.add(npcDefinition.id()) }
        loadBankingNpcs += 2271 // Emerald benedict.
        loadBankingNpcs += scriptedBankers
    }
}
