package game.player

import api.controller.Controllers.of
import api.controller.MultiCombatAreaController
import api.controller.WildernessAreaController

/* Global controller keys. */
object ControllerKeys {
    val WILDERNESS = of(WildernessAreaController::class) { WildernessAreaController }
    val MULTICOMBAT = of(MultiCombatAreaController::class) { MultiCombatAreaController }
}