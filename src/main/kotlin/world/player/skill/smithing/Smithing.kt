package world.player.skill.smithing

import io.luna.game.model.def.GameObjectDefinition

/**
 * Holds important constants and utility functions related to the Smithing skill.
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
    val FURNACE_OBJECTS = GameObjectDefinition.ALL.filter { it.actions.contains("Smelt") }.map { it.id }.toSet()
}