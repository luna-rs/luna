package engine.controllers

import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc


object Controllers {

    /**
     * An immutable list of location listeners tracked by all players.
     */
    val GLOBAL_LOCATABLE_CONTROLLERS = listOf(
        MultiCombatAreaListener,
        WildernessLocatableController
    )

    /**
     * Determines if a [Mob] has a [MultiCombatAreaListener] registered.
     */
    fun Mob.inMultiArea(): Boolean {
        return if (this is Npc) MultiCombatAreaListener.inside(position) else
            asPlr().controllers.contains(MultiCombatAreaListener)
    }

    /**
     * Determines if a [Mob] has a [WildernessLocatableController] registered.
     */
    fun Mob.inWilderness(): Boolean {
        return if (this is Npc) WildernessLocatableController.inside(position) else asPlr().wildernessLevel > 0

    }
}