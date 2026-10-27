package game.combat.spells

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Crumble Undead target eligibility from Lost City's revision-377 NPC parameters.
 * The list includes every explicit undead flag in all.npc and antimacro.npc, resolved through npc.pack.
 * Unlisted NPCs are rejected; names and later-version classifications do not add eligible targets.
 * Player targets retain the reference's ordinary PvP spell path.
 *
 * @author lare96
 */
object CrumbleUndead {

    /**
     * Explicit undead NPC identities, including quest forms without Luna combat definitions.
     * Eligibility alone does not bypass ordinary attackability, controller or quest restrictions.
     */
    private val undead = setOf(
        73, 74, 75, 76, 77, // Zombies.
        90, 91, 92, 93, 94, // Skeletons.
        103, 104, 238, // Ghosts and Thrantax.
        419, 420, 421, 422, 423, 424, // Random-event zombies.
        425, 426, 427, 428, 429, 430, // Random-event shades.
        459, 491, 492, 501, // Quest skeleton and spirits.
        502, 503, 504, 505, 506, 507, 508, 509, // Shilo Village undead.
        749, 750, 751, // Melzar's Maze.
        940, 1218, // Echned Zekin and ghoul.
        2724, 2725, 3291 // Additional skeletons.
    )

    /**
     * Checks the target's current form before rune consumption and cast experience, for manual and autocasts.
     * A failed target does not alter the player's configured autocast spell.
     */
    fun canCast(player: Player, target: Mob): Boolean {
        if (target is Npc && target.id !in undead) {
            player.sendMessage("This spell only affects skeletons, zombies, ghosts and shades.")
            return false
        }
        return true
    }
}
