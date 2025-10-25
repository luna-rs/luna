package api.controller

import api.controller.WildernessAreaController.wildernessLevel
import game.player.ControllerKeys
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.controller.ControllerKey
import io.luna.game.model.mob.controller.PlayerController
import kotlin.reflect.KClass

/**
 * A registry of global [ControllerKey] definitions and related controller utilities.
 *
 * This object centralizes the definition of core area controllers and provides helper functions for determining if
 * a [Mob] is currently within a specific controlled zone.
 *
 * Controllers represent contextual gameplay environments such as the Wilderness or multi-combat zones, and are
 * responsible for applying area or context specific behavior and rules.
 *
 * @author lare96
 */
object Controllers {

    /**
     * Shortcut to [ControllerKey.of] with Kotlin syntax.
     */
    fun <T : PlayerController> of(controllerType: KClass<T>, controllerSupplier: () -> T) =
        ControllerKey.of(controllerType.java, controllerSupplier)

    /**
     * Determines if a [Mob] has a [MultiCombatAreaController] registered.
     */
    fun Mob.inMultiArea(): Boolean {
        return if (this is Npc) MultiCombatAreaController.inside(position) else
            asPlr().controllers.contains(ControllerKeys.MULTICOMBAT)
    }

    /**
     * Determines if a [Mob] has a [WildernessAreaController] registered.
     */
    fun Mob.inWilderness(): Boolean {
        return if (this is Npc) WildernessAreaController.inside(position) else
            asPlr().wildernessLevel > 0

    }
}