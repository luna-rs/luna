package game.npc.spawn.treeGnomeVillage

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Elkoy, waiting outside the Tree Gnome Village maze.
 */
val ELKOY_AT_MAZE = 473

/**
 * Elkoy, waiting inside the Tree Gnome Village.
 */
val ELKOY_IN_VILLAGE = 474

/**
 * Where Elkoy leaves players outside the maze.
 */
val MAZE_ENTRANCE = Position(2504, 3192)

/**
 * Where Elkoy leaves players inside the village.
 */
val VILLAGE = Position(2515, 3159)

/**
 * Talks to [elkoy], who offers to lead [plr] through the maze to [destination].
 */
fun talkToElkoy(plr: Player, elkoy: Int, offer: String, destination: Position, arrival: String) {
    // TODO Tree Gnome Village: before the quest he only introduces himself and the maze, and during it he talks
    //  about the stolen orbs of protection. Until the quest exists every player has finished it.
    plr.newDialogue()
        .player(Expression.HAPPY, "Hello Elkoy.")
        .npc(elkoy, Expression.HAPPY, "Hi there, I hope life is treating you well. Would you", offer)
        .options("Yes please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "Yes please.")
                .npc(elkoy, Expression.HAPPY, "Ok then, follow me.")
                .then { player ->
                    // A plain move would close the dialogue that is still closing here.
                    player.moveKeepingWindows(destination)
                    player.newDialogue().npc(ELKOY_IN_VILLAGE, Expression.HAPPY, arrival).open()
                }
                .open()
        }, "No thanks Elkoy.", {
            it.newDialogue()
                .player("No thanks Elkoy.")
                .npc(elkoy, "Ok then, take care.")
                .open()
        })
        .open()
}

npc1(ELKOY_AT_MAZE) {
    talkToElkoy(plr, ELKOY_AT_MAZE, "like me to show you the way to the village?", VILLAGE,
                "Here we are. Feel free to look around.")
}

npc1(ELKOY_IN_VILLAGE) {
    talkToElkoy(plr, ELKOY_IN_VILLAGE, "like me to show you the way out of the village?", MAZE_ENTRANCE,
                "Here we are. Have a safe journey.")
}
