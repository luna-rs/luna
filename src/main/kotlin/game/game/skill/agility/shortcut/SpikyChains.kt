package game.skill.agility.shortcut

import api.predef.*
import api.predef.ext.*
import game.skill.agility.Agility
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityApproach
import game.skill.agility.AgilityStep
import game.skill.agility.shortcut.ShortcutMoves.CLIMB_DOWN
import game.skill.agility.shortcut.ShortcutMoves.REACH_FOR_LADDER
import game.skill.agility.shortcut.ShortcutMoves.active
import game.skill.agility.shortcut.ShortcutMoves.keepsFooting
import game.skill.agility.shortcut.ShortcutMoves.ready
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The Slayer Tower's spiked chains, each climbed up from the floor below or down from the floor above, from any of
 * its four sides; the player comes out on the same side on the other floor. A climb can rip the player's hands, which
 * hurts but still gets them there.
 *
 * @author TheLining
 */
object SpikyChains {

    /**
     * A chain through a floor: its level and the tile it stands on, on the lower floor.
     */
    class Chain(val level: Int, val bottom: Position) {
        val top: Position = bottom.translate(0, 0, 1)
    }

    /**
     * The ground-floor chain (61) and the first-floor one (71).
     */
    val CHAINS = listOf(Chain(61, Position(3422, 3550)), Chain(71, Position(3447, 3576, 1)))

    /**
     * The chain's two object ids: climbed up, and climbed down.
     */
    const val UP = 9319
    const val DOWN = 9320
    val IDS = setOf(UP, DOWN)

    /**
     * XP for a clean climb and for one that rips the player's hands.
     */
    private const val XP = 3.0
    private const val HURT_XP = 6.0

    /**
     * Climbs [obj] from the side of it [plr] is nearest, after walking them there.
     */
    fun climb(plr: Player, obj: GameObject) {
        val up = obj.id == UP
        val chain = CHAINS.firstOrNull { (if (up) it.bottom else it.top) == obj.position } ?: return
        if (!ready(plr)) return
        val side = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
            .map { obj.position.translate(1, it) }
            .minByOrNull { plr.position.computeLongestDistance(it) } ?: return
        if (plr.position.computeLongestDistance(side) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(plr) || plr.position != side || obj.state != EntityState.ACTIVE) return@begin
            if (!Agility.checkLevel(plr, chain.level)) return@begin
            plr.face(obj.position)
            plr.submitAction(climb(plr, side.translate(0, 0, if (up) 1 else -1), up))
        }
        if (plr.position == side) begin() else plr.submitAction(AgilityApproach(plr, side) { begin() })
    }

    /**
     * The climb onto [to], two ticks. One roll, on the Cosmic walls' chart, decides whether the chain rips the
     * player's hands: 2 to 5 damage, and more XP for the pain.
     */
    private fun climb(plr: Player, to: Position, up: Boolean): AgilityAction {
        val hurt = !keepsFooting(plr.agility.level, 50, 254)
        val animation = if (up) REACH_FOR_LADDER else CLIMB_DOWN
        return object : AgilityAction(plr, listOf(AgilityStep(to, 2, animation = animation, jump = true)), null,
                                      valid = { active(plr) }), DeathInterruptible {
            init {
                onSuccess = {
                    if (hurt) {
                        plr.sendMessage("You rip your hands to pieces on the chain as you climb.")
                        plr.damage(rand(2, 5))
                        plr.agility.addExperience(HURT_XP)
                    } else {
                        plr.agility.addExperience(XP)
                    }
                }
            }
        }
    }
}
