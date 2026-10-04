package engine.obj

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Climbs [stairs] for [plr].
 *
 * @param plr The climbing player.
 * @param stairs The stairs being used.
 * @param up `true` to climb up, `false` to climb down.
 */
fun climb(plr: Player, stairs: GameObject, up: Boolean) {
    val destination = StairDestination.destination(stairs, plr.position, up)
    if (destination == null) {
        plr.sendMessage("Nothing interesting happens.")
        return
    }
    plr.move(destination)
    KnownStairs.forStairs(stairs)?.message?.let { plr.sendMessage(it) }
}

/**
 * Asks [plr] whether to climb up or down [stairs], unless they only lead one way.
 *
 * @param plr The climbing player.
 * @param stairs The stairs being used.
 */
fun climbEither(plr: Player, stairs: GameObject) {
    val up = StairDestination.destination(stairs, plr.position, true)
    val down = StairDestination.destination(stairs, plr.position, false)
    when {
        up != null && down != null && up != down -> {
            // TODO Title this dialogue "Climb up or down the stairs?" like the original game. Setting it as the option
            //  dialogue's title clips the text, so it needs another way.
            val (climbUp, climbDown) = KnownStairs.forStairs(stairs)?.choices
                ?: Pair("Climb up the stairs.", "Climb down the stairs.")
            plr.newDialogue().options(climbUp, { climb(plr, stairs, true) },
                                      climbDown, { climb(plr, stairs, false) }).open()
        }

        up != null -> climb(plr, stairs, true)
        down != null -> climb(plr, stairs, false)
        else -> plr.sendMessage("Nothing interesting happens.")
    }
}

/**
 * Returns the object click handler for a stairs action name, or `null` if it isn't a way of climbing.
 *
 * @param name The action name.
 */
fun handleAction(name: String): EventAction<ObjectClickEvent>? =
    when (name.lowercase()) {
        "climb-up", "walk-up", "ascend" -> fun(event: ObjectClickEvent) { climb(event.plr, event.gameObject, true) }
        "climb-down", "walk-down", "descend" ->
            fun(event: ObjectClickEvent) { climb(event.plr, event.gameObject, false) }

        "climb" -> fun(event: ObjectClickEvent) { climbEither(event.plr, event.gameObject) }
        else -> null
    }

on(ServerLaunchEvent::class) {
    for (def in GameObjectDefinition.ALL) {
        if (def.name !in StairDestination.NAMES) {
            continue
        }
        def.actions.forEachIndexed { index, name ->
            val action = name?.let { handleAction(it) } ?: return@forEachIndexed
            when (index) {
                0 -> object1(def.id(), action)
                1 -> object2(def.id(), action)
                2 -> object3(def.id(), action)
            }
        }
    }
}
