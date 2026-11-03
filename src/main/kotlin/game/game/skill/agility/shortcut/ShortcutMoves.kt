package game.skill.agility.shortcut

import api.predef.*
import api.predef.ext.*
import game.skill.Skills
import game.skill.agility.Agility
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityStep
import game.skill.agility.AgilityWalk
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * A crossing that ended in a fall, which pays its own XP.
 */
internal interface ShortcutFall

/**
 * The moves the Agility shortcuts are made of, from the 377 cache's animations for each kind of obstacle.
 *
 * @author TheLining
 */
internal object ShortcutMoves {

    /**
     * HUMAN_WALK_CRUMBLEDWALL, the climb over a crumbling wall.
     */
    const val CRUMBLED_WALL = 840

    /**
     * AGILTY_SHORTCUT_ENTER_HOLE, AGILTY_SHORTCUT_TUNNEL_WALK and AGILTY_SHORTCUT_EXIT_HOLE: into a tunnel under a
     * wall, along it and out of its far end.
     */
    const val ENTER_HOLE = 2589
    const val TUNNEL_WALK = 2590
    const val EXIT_HOLE = 2591

    /**
     * AGILITY_SHORTCUT_CRACK_ENTER and AGILITY_SHORTCUT_CRACK_LEAVE: into a crevice and out of its far end.
     */
    const val CRACK_ENTER = 2594
    const val CRACK_LEAVE = 2595

    /**
     * RAILING_SQUEEZE, through a gap in a railing.
     */
    const val RAILING_SQUEEZE = 3844

    /**
     * HUMAN_DOUBLEPIPESQUEEZE, through each half of a pipe.
     */
    const val PIPE_SQUEEZE = 749
    private const val PIPE_SQUEEZE_CYCLES = 80

    /**
     * HUMAN_ROPESWING_LONG, the swing on a rope.
     */
    const val ROPE_SWING = 751

    /**
     * HUMAN_MONKEYBARS_ON and HUMAN_MONKEYBARS_OFF: up onto the bars and the drop off them.
     */
    const val BARS_ON = 742
    const val BARS_OFF = 743

    /**
     * HUMAN_REACHFORLADDER, the climb up the Watchtower's wall.
     */
    const val REACH_FOR_LADDER = 828

    /**
     * HUMAN_WALK_LOGBALANCE_STUMBLE, the stumble before a fall into a river.
     */
    const val STUMBLE = 764

    /**
     * The splash where a player falls into a river.
     */
    const val SPLASH = 68

    /**
     * The lines a player washed up after a fall into a river sees.
     */
    val WASHED_UP = listOf("You get washed up on the side of the river...", "After being nearly half drowned.")

    /**
     * Whether [plr] can start a shortcut: alive, in the world and free to move.
     */
    fun ready(plr: Player) = active(plr) && !plr.isLocked && !plr.walking.isLocked && !plr.status.isImmobilized()

    /**
     * Whether [plr] is still in the world and alive, so a crossing may go on.
     */
    fun active(plr: Player) = plr.state == EntityState.ACTIVE && plr.isAlive

    /**
     * Whether a player at [level] Agility keeps their footing against a fail roll from [low] to [high].
     */
    fun keepsFooting(level: Int, low: Int, high: Int) = Skills.success(low, high, minOf(level, 99))

    /**
     * Drops [plr] into the river at [water] with a splash.
     */
    fun fallInto(plr: Player, water: Position) {
        plr.move(water)
        LocalGraphic(plr.context, SPLASH, 0, 0, water, ChunkUpdatableView.globalView()).display()
    }

    /**
     * Climbs over a crumbling wall onto [to], the far side: one climb with the move over its 68 cycles.
     */
    fun climbOverWall(plr: Player, to: Position) =
        AgilityAction(plr, listOf(AgilityStep(to, 3, animation = CRUMBLED_WALL, moveEnd = 68)), null,
                      valid = { active(plr) })

    /**
     * Climbs into a tunnel under a wall, along it to [to] a tile a tick and out. The tunnel pose is set as the stance
     * while the player climbs in, because the client keeps the stance a player has when a forced move starts.
     */
    fun underWall(plr: Player, from: Position, to: Position) = AgilityAction(plr, listOf(
        AgilityStep(from, 2, walkingAnimation = TUNNEL_WALK, animation = ENTER_HOLE, jump = true),
        AgilityStep(to, from.computeLongestDistance(to)),
        AgilityStep(to, 2, animation = EXIT_HOLE, jump = true)), null, valid = { active(plr) })

    /**
     * Squeezes through a gap in a fence onto [to], the tile across it.
     */
    fun squeezeThroughFence(plr: Player, to: Position) =
        AgilityAction(plr, listOf(AgilityStep(to, 3, animation = RAILING_SQUEEZE, moveStart = 30, moveEnd = 60)),
                      null, valid = { active(plr) })

    /**
     * Walks across a log balance onto [to], a tile a tick.
     */
    fun walkLog(plr: Player, to: Position) =
        AgilityAction(plr, listOf(AgilityStep(to, plr.position.computeLongestDistance(to),
                                              walkingAnimation = AgilityWalk.BALANCE.walk)),
                      "You walk carefully across the slippery log...",
                      endMessage = "...You make it safely to the other side.", valid = { active(plr) })

    /**
     * Walks to the middle of a log, stumbles and falls into the river at [water] beside it, swims and is washed up on
     * the far bank, [to], 10 ticks after the fall, with 2 to 6 damage and [xp].
     */
    fun fallOffLog(plr: Player, from: Position, to: Position, water: Position, xp: Double): AgilityAction {
        val middle = Position((from.x + to.x) / 2, (from.y + to.y) / 2)
        val swim = AgilityWalk.SWIM.walk
        return object : AgilityAction(plr, listOf(
            AgilityStep(middle, from.computeLongestDistance(middle), walkingAnimation = AgilityWalk.BALANCE.walk),
            AgilityStep(middle, 2, walkingAnimation = AgilityWalk.BALANCE.walk, animation = STUMBLE, jump = true),
            AgilityStep(water, 1, walkingAnimation = swim, jump = true, message = "You slip and fall...",
                        begin = { fallInto(plr, water) }),
            AgilityStep(to, 9, walkingAnimation = swim, jump = true)),
            "You walk carefully across the slippery log...", valid = { active(plr) }), DeathInterruptible, ShortcutFall {
            init {
                onSuccess = {
                    WASHED_UP.forEach { plr.sendMessage(it) }
                    plr.damage(rand(2, 6))
                    plr.agility.addExperience(xp)
                }
            }
        }
    }

    /**
     * Climbs across rocks onto [to], a tile a tick.
     */
    fun climbRocks(plr: Player, from: Position, to: Position) =
        AgilityAction(plr, listOf(AgilityStep(to, from.computeLongestDistance(to),
                                              walkingAnimation = AgilityWalk.CLIMB.walk)), null,
                      valid = { active(plr) })

    /**
     * Squeezes into a crevice, through the rock to [to] and out. The side-step stance is set while the player
     * squeezes in, because the client keeps the stance a player has when a forced move starts.
     */
    fun squeezeThroughCrevice(plr: Player, from: Position, to: Position) = AgilityAction(plr, listOf(
        AgilityStep(from, 2, walkingAnimation = AgilityWalk.SIDESTEP.walk, animation = CRACK_ENTER, jump = true),
        AgilityStep(to, 4),
        AgilityStep(to, 2, animation = CRACK_LEAVE, jump = true)), null, valid = { active(plr) })

    /**
     * Squeezes through each half of a pipe in turn, with a tick in the sealed gap between them; a tick at the mouth
     * first lets the client finish the step onto it. Each half's move ends with its squeeze, so the player never
     * slides on in a normal pose. A player who doesn't come through unhurt gets stuck in the gap and takes 1 to 6
     * damage, unless they have 6 Hitpoints or fewer.
     */
    fun squeezeThroughPipe(plr: Player, from: Position, to: Position): AgilityAction {
        val gap = Position((from.x + to.x) / 2, from.y)
        val stuck = plr.health > 6 && !keepsFooting(plr.agility.level, 0, 254)
        return AgilityAction(plr, listOf(
            AgilityStep(from, 1, jump = true),
            AgilityStep(gap, 3, animation = PIPE_SQUEEZE, moveEnd = PIPE_SQUEEZE_CYCLES),
            AgilityStep(gap, 1, jump = true, begin = {
                if (stuck) {
                    plr.sendMessage("You get stuck...and have to force yourself through the pipe.")
                    plr.damage(rand(1, 6))
                }
            }),
            AgilityStep(to, 4, animation = PIPE_SQUEEZE, moveEnd = PIPE_SQUEEZE_CYCLES)), null, valid = { active(plr) })
    }

    /**
     * Swings across on [rope] to [to]: two ticks on the start tile, then the swing.
     */
    fun swing(plr: Player, to: Position, rope: GameObject) = AgilityAction(plr, listOf(
        AgilityStep(plr.position, 2, jump = true),
        AgilityStep(to, 2, animation = ROPE_SWING, message = "You skilfully swing across.", moveStart = 20,
                    moveEnd = 50, begin = { rope.animate(Agility.ROPE_SWING_ANIMATION) })), null,
        valid = { active(plr) })

    /**
     * Swings across the monkey bars from the bar end [from] to the other, [to]: a tick facing along the bars, the
     * reach up, a tile hand over hand, a jump three tiles on to the far end of the spikes, the last tile and the drop.
     * The reach gets two ticks, because the client holds a player's steps until its 55 cycles are over, and so does the
     * last tile, so the client is there before the drop.
     */
    fun swingAcrossBars(plr: Player, from: Position, to: Position): AgilityAction {
        val step = if (to.y > from.y) 1 else -1
        val bars = AgilityWalk.MONKEY_BARS.walk
        val across = from.translate(0, 4 * step)
        return AgilityAction(plr, listOf(
            AgilityStep(from, 1, walkingAnimation = bars, jump = true, begin = { plr.face(to) }),
            AgilityStep(from, 2, walkingAnimation = bars, animation = BARS_ON, jump = true),
            AgilityStep(from.translate(0, step), 1, walkingAnimation = bars),
            AgilityStep(across, 1, walkingAnimation = bars, jump = true, begin = { plr.move(across) }),
            AgilityStep(to, 2, walkingAnimation = bars),
            AgilityStep(to, 1, animation = BARS_OFF, jump = true)), null, valid = { active(plr) })
    }

    /**
     * Climbs the wall below a window from [from], facing it, and comes in through the window at [to] two ticks after
     * the climb starts. A tick on [from] first lets the client finish the step onto it before the climb.
     */
    fun climbWall(plr: Player, from: Position, to: Position): AgilityAction {
        plr.face(Position(to.x, to.y, from.z))
        return AgilityAction(plr, listOf(
            AgilityStep(from, 1, jump = true),
            AgilityStep(to, 2, animation = REACH_FOR_LADDER, jump = true, message = "You climb up the wall...",
                        begin = { plr.sendMessage("...and squeeze in through the window.") })), null,
                             valid = { active(plr) })
    }
}
