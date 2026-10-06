package engine.obj

import api.predef.*
import io.luna.game.action.impl.ClimbAction
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Supported ladder interaction action names.
 */
val LADDER_ACTIONS = listOf("Climb", "Climb-up", "Climb-down", "Climb-Down")

/**
 * The Mining Guild ladder, which needs 60 Mining.
 */
val MINING_GUILD_LADDER = 2113

/**
 * The ladder out of the Port Phasmatys brewery's cellar, whose name isn't capitalised.
 */
val PHASMATYS_BREWERY_LADDER = 7433

/**
 * The Barbarian Outpost agility course ladders, which barbarian_outpost.kts climbs instead.
 */
val AGILITY_COURSE_LADDERS = setOf(Position(2532, 3545), Position(2532, 3545, 1))

/**
 * Starts a ladder climb for [plr].
 *
 * @param plr The climbing player.
 * @param ladder The ladder being used.
 * @param up `true` to climb up, `false` to climb down.
 */
fun climb(plr: Player, ladder: GameObject, up: Boolean) {
    if (ladder.position in AGILITY_COURSE_LADDERS) {
        return
    }
    val refusal = LadderType.forObject(ladder).refusal
    if (refusal != null) {
        plr.sendMessage(refusal)
        return
    }
    if (ladder.id == MINING_GUILD_LADDER && plr.mining.level < 60) {
        plr.sendMessage("You need a Mining level of 60 to access the Mining Guild.")
        return
    }
    val destination = LadderDestination.destination(ladder, plr.position, up)
    if (destination == null) {
        plr.sendMessage("Nothing interesting happens.")
        return
    }
    val dir = Direction.between(plr.position, ladder.position)
    val message = if (up) "You climb up the ladder." else "You climb down the ladder."
    plr.submitAction(ClimbAction(plr, destination, dir, message))
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
        "Climb-up" -> fun(event: ObjectClickEvent) { climb(event.plr, event.gameObject, true) }
        "Climb-down", "Climb-Down" -> fun(event: ObjectClickEvent) { climb(event.plr, event.gameObject, false) }
        "Climb" ->
            fun(event: ObjectClickEvent) {
                val plr = event.plr
                plr.newDialogue().options("Climb up", { climb(plr, event.gameObject, true) },
                                          "Climb down", { climb(plr, event.gameObject, false) },
                                          "Nevermind", { plr.overlays.closeWindows() }).open()
            }

        else -> throw IllegalArgumentException(
            "Argument '$name' must be either 'Climb', 'Climb-up', 'Climb-down', or 'Climb-Down'.")
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
        if (def.name == "Ladder" || def.id() == PHASMATYS_BREWERY_LADDER) {
            def.actions.forEachIndexed { index, name -> handleIndex(def.id(), index, name) }
        }
    }
}
