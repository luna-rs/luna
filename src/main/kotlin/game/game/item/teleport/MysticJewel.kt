package game.item.teleport

import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * The Mystic jewel, which lets the player give up on the Rogues' Den maze. Activating it has Brian O'Richard, the head
 * of the maze, offer to call the player back out to the start of the maze.
 *
 * @author TheLining
 */
object MysticJewel {

    /**
     * The Mystic jewel item id.
     */
    const val ID = 5561

    /**
     * Brian O'Richard's npc id. He talks to the player from wherever he is.
     */
    const val BRIAN = 2266

    /**
     * The maze: map squares 46_78 to 47_79, north of the maze doorway.
     */
    private val MAZE = Area.of(2944, 4992, 3071, 5119)

    /**
     * The maze's plane.
     */
    private const val MAZE_PLANE = 1

    /**
     * Where the player is put after leaving: the corridor tile just south of the maze doorway.
     */
    val START = Position(3056, 4990, 1)

    /**
     * Determines if [position] is inside the maze.
     */
    fun inMaze(position: Position) = position.z == MAZE_PLANE && MAZE.contains(position)

    /**
     * Invoked when the player activates the jewel. Brian offers to let the player out if they are in the maze.
     */
    fun activate(plr: Player) {
        if (!inMaze(plr.position)) {
            plr.newDialogue()
                .npc(BRIAN, "You don't really need my help do you? You're not", "even in the maze yet!")
                .open()
            return
        }
        plr.newDialogue()
            .npc(BRIAN, "Want to come out then? Giving up are you? You know", "there's no time limit here.")
            .options(listOf(
                DialogueOption("Yes I'd like to leave!") {
                    plr.newDialogue()
                        .player("Yes I'd like to leave!")
                        .npc(BRIAN, "Right you are then, out you come.")
                        .then { leave(plr) }
                        .open()
                },
                DialogueOption("No I'm fine thanks.") {
                    plr.newDialogue().player("No I'm fine thanks.").open()
                }))
            .open()
    }

    /**
     * Takes every jewel the player holds and moves them out to [START]. The maze has no progress to reward, so Brian
     * gives the result for not getting past the first pendulum.
     */
    private fun leave(plr: Player) {
        // TODO Rogues' Den maze: once the maze exists, give the result, XP and stat restore for how far the player got.
        val jewels = plr.inventory.computeAmountForId(ID)
        if (jewels == 0 || !inMaze(plr.position)) {
            return
        }
        plr.inventory.remove(Item(ID, jewels))
        plr.move(START)
        plr.newDialogue().npc(BRIAN, "Oh dear, you really didn't get very far did you?").open()
    }
}
