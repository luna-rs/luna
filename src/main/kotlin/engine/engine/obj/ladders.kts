package engine.obj

import api.predef.*
import io.luna.game.action.impl.ClimbAction
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Direction
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Supported ladder interaction action names.
 */
val LADDER_ACTIONS = listOf("Climb", "Climb-up", "Climb-down")

/**
 * Starts a ladder climb for [plr].
 *
 * @param plr The climbing player.
 * @param ladder The ladder being used.
 * @param offset The height offset to apply. Positive climbs up, negative climbs down.
 */
fun climb(plr: Player, ladder: GameObject, offset: Int) {
    val plrPos = plr.position
    val dir = Direction.between(plrPos, ladder.position)
    when (Integer.signum(offset)) {
        1 -> plr.submitAction(ClimbAction(plr, plrPos.translate(0, 0, 1), dir, "You climb up the ladder."))
        -1 -> plr.submitAction(ClimbAction(plr, plrPos.translate(0, 0, -1), dir, "You climb down the ladder."))
    }
}

/**
 * Returns the object click handler for a ladder action name.
 *
 * @param name The ladder action name.
 * @return The matching object click handler.
 * @throws IllegalArgumentException If [name] is not a supported ladder action.
 */
fun handleAction(name: String): EventAction<ObjectClickEvent> =
    when (name) {
        "Climb-up" -> fun(event: ObjectClickEvent) { climb(event.plr, event.gameObject, 1) }
        "Climb-down" -> fun(event: ObjectClickEvent) { climb(event.plr, event.gameObject, -1) }
        "Climb" ->
            fun(event: ObjectClickEvent) {
                val plr = event.plr
                plr.newDialogue().options("Climb up", { climb(plr, event.gameObject, 1) },
                                          "Climb down", { climb(plr, event.gameObject, -1) },
                                          "Nevermind", { plr.overlays.closeWindows() }).open()
            }

        else -> throw IllegalArgumentException("Argument '$name' must be either 'Climb', 'Climb-up', or 'Climb-down'.")
    }

/**
 * Registers a ladder interaction for the given object action slot.
 *
 * @param id The object id.
 * @param index The action slot index.
 * @param name The action name in that slot.
 */
fun handleIndex(id: Int, index: Int, name: String) {
    if (LADDER_ACTIONS.contains(name)) {
        when (index) {
            0 -> object1(id, handleAction(name))
            1 -> object2(id, handleAction(name))
            2 -> object3(id, handleAction(name))
        }
    }
}

on(ServerLaunchEvent::class) {
    for (def in GameObjectDefinition.ALL) {
        if (def.name.equals("Ladder")) {
            def.actions.forEachIndexed { index, name -> handleIndex(def.id, index, name) }
        }
    }
}