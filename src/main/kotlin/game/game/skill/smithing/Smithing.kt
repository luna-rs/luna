package game.skill.smithing

import io.luna.game.model.def.GameObjectDefinition

/**
 * Holds important constants and utility functions related to the Smithing skill.
 *
 * @author lare96
 */
object Smithing {

    /**
     * The hammer item ID.
     */
    const val HAMMER = 2347

    /**
     * All anvil object IDs.
     */
    val ANVIL_OBJECTS = setOf(2782, 2783, 4306, 6150)

    /**
     * Retrieves all object IDs with the interaction action "Smelt."
     */
    val FURNACE_OBJECTS = GameObjectDefinition.ALL.filter { smeltsAt(it) }.map { it.id() }.toSet()

    /**
     * Whether [definition] has the "Smelt" option, or the client swaps it by varbit for a furnace that does (such as
     * a furnace repaired in a quest). Clicks on those arrive under the original id.
     */
    fun smeltsAt(definition: GameObjectDefinition, name: String? = null): Boolean {
        val children = definition.varpDef?.childIdList.orEmpty()
            .filter { it != -1 }
            .mapNotNull { GameObjectDefinition.ALL.get(it).orElse(null) }
        return (listOf(definition) + children).any {
            it.actions.contains("Smelt") && (name == null || it.name == name)
        }
    }
}