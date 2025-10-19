package api.controller

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.controller.ControllerKey
import io.luna.game.model.mob.controller.PlayerController
import world.player.combat.MultiCombatAreaController
import world.player.wilderness.WildernessAreaController
import world.player.wilderness.WildernessAreaController.wildernessLevel
import kotlin.reflect.KClass

/* All controller properties go here. */
object Controllers {
    val WILDERNESS = of(WildernessAreaController::class) { WildernessAreaController }
    val MULTICOMBAT = of(MultiCombatAreaController::class) { MultiCombatAreaController }
}

/**
 * Shortcut to [ControllerKey.of] with Kotlin syntax.
 */
fun <T : PlayerController> of(controllerType: KClass<T>, controllerSupplier: () -> T) =
    ControllerKey.of(controllerType.java, controllerSupplier)

/**
 * Determines if a [Mob] has a [MultiCombatAreaController] registered.
 */
fun Mob.inMultiArea(): Boolean {
    return if(this is Npc) MultiCombatAreaController.inside(position) else
        asPlr().controllers.contains(Controllers.MULTICOMBAT)
}

/**
 * Determines if a [Mob] has a [WildernessAreaController] registered.
 */
fun Mob.inWilderness(): Boolean {
    return if(this is Npc) WildernessAreaController.inside(position) else
        asPlr().wildernessLevel > 0

}