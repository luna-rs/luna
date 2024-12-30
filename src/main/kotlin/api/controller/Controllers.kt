package api.controller

import io.luna.game.model.mob.controller.ControllerKey
import io.luna.game.model.mob.controller.PlayerController
import world.controller.MultiCombatAreaController
import world.controller.wilderness.WildernessAreaController
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
