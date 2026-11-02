package game.skill.agility.skullball

import api.predef.*
import api.predef.ext.*
import game.player.Jingles
import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.ClearHintMessageWriter
import io.luna.net.msg.out.MobHintMessageWriter
import io.luna.net.msg.out.PositionHintMessageWriter
import io.luna.net.msg.out.SoundMessageWriter
import kotlin.math.floor

/**
 * Werewolf Skullball, the level 25 game on the east side of the werewolf agility area. A player kicks their own
 * skullball through ten goals in order round the cave, then into a hole in the wall, and is paid by their time.
 *
 * @author TheLining
 */
object Skullball {

    /**
     * The ball, the boss who hands it out, and the trainers along the course.
     */
    const val BALL = 1659
    const val BOSS = 1660
    const val TRAINER = 1662

    /**
     * The skeleton hanging between each goal's posts.
     */
    const val GOAL = 5146

    /**
     * The Agility level needed for a ball.
     */
    const val LEVEL = 25

    /**
     * The kick, the goal skeleton swinging with or against the course, and the celebration at the finish.
     */
    const val KICK = 1606
    const val GOAL_FORWARD = 1598
    const val GOAL_BACKWARD = 1599
    const val CELEBRATE = 1605

    /**
     * Client cycles into the kick before the tap sounds, as the foot meets the ball.
     */
    const val TAP_DELAY = 15

    /**
     * The result screen and its time and score texts.
     */
    const val RESULT = 12130
    const val RESULT_TIME = 12136
    const val RESULT_XP = 12137

    /**
     * Full experience for a lap of up to four minutes. Each tick over takes [XP_PER_TICK] off.
     */
    const val FULL_XP = 750
    const val FULL_XP_TICKS = 400
    const val XP_PER_TICK = 1.6

    /**
     * How far a new ball rolls out of its hole, and how many ticks after it appears.
     */
    const val ROLL_OUT = 4
    const val ROLL_OUT_DELAY = 2

    /**
     * Ticks from the ball going into the hole to the finish, and from the finish to the result screen.
     */
    const val FINISH_DELAY = 2
    const val RESULT_DELAY = 5

    /**
     * Ticks the arrow points at the next goal after Show-Goal.
     */
    const val SHOW_GOAL_TICKS = 6

    /**
     * How high the arrow hangs over a goal.
     */
    const val GOAL_ARROW_HEIGHT = 60

    /**
     * The ways to kick the ball, and how many squares each sends it.
     */
    enum class Shot(val squares: Int) {
        TAP(1),
        KICK(4),
        SHOOT(9)
    }

    /**
     * A goal: the skeleton's tile, and the way the ball has to travel through it to score.
     */
    class Goal(val middle: Position, val direction: Direction)

    /**
     * The goals in the order they're scored, clockwise round the cave.
     */
    val GOALS = listOf(
        Goal(Position(3555, 9870), Direction.NORTH),
        Goal(Position(3556, 9883), Direction.NORTH),
        Goal(Position(3558, 9891), Direction.NORTH),
        Goal(Position(3557, 9900), Direction.NORTH),
        Goal(Position(3558, 9906), Direction.NORTH),
        Goal(Position(3563, 9911), Direction.EAST),
        Goal(Position(3575, 9905), Direction.SOUTH),
        Goal(Position(3574, 9888), Direction.SOUTH),
        Goal(Position(3575, 9878), Direction.SOUTH),
        Goal(Position(3568, 9864), Direction.WEST))

    /**
     * The tiles in front of the four holes a new ball comes out of, west to east.
     */
    val HOLES = listOf(Position(3552, 9860), Position(3554, 9861), Position(3555, 9861), Position(3557, 9860))

    /**
     * The hole in the bottom corridor's north wall that ends a lap, and the tile below it.
     */
    val FINISH = Position(3563, 9866)
    val FINISH_FROM = Position(3563, 9865)

    /**
     * What the trainers shout as a werewolf scores a goal.
     */
    val GOAL_LINES = listOf(
        "You have truly gifted paws!",
        "I've never seen anything like it!",
        "You need a few more skullball lessons.",
        "Keep it up!",
        "Don't give up the day job!",
        "Pathetic!",
        "What - a - goal !!!",
        "That was just plain lucky.",
        "Come on, get going!")

    val GRRR = "Grrr - you don't belong in here, human!"

    /**
     * One player's lap: their ball, how far round they are, and what the ball is doing.
     */
    class Game(val player: Player, val ball: Npc) {
        var goals = 0
        var started = -1L
        var direction: Direction = Direction.NONE
        var moves = 0
        var delay = 0
        var showGoal = 0
        var finishIn = 0
        var resultIn = 0
        var lapTicks = 0
    }

    /**
     * The lap each player is on.
     */
    private val games = HashMap<Player, Game>()

    /**
     * The hole the next ball comes out of.
     */
    private var nextHole = 0

    /**
     * Whether the werewolves take [plr] for one of them. In 2006 only a worn Ring of Charos (4202, or the unlocked
     * 6465) did that.
     */
    fun passesAsWerewolf(plr: Player): Boolean {
        // TODO Once Creature of Fenkenstrain is added, only players wearing a Ring of Charos pass.
        return true
    }

    /**
     * Whether [plr] has a ball out.
     */
    fun playing(plr: Player) = games[plr]?.let { it.finishIn == 0 && it.resultIn == 0 } ?: false

    /**
     * Whether [position] is in the werewolf agility area.
     */
    fun inside(position: Position) = position.x in 3520..3583 && position.y in 9856..9925 && position.z == 0

    /**
     * Gives [plr] a new ball from the next hole, dropping any ball and lap they had. The ball rolls out of the wall a
     * moment later.
     */
    fun start(plr: Player) {
        games.remove(plr)?.let { remove(it) }
        val ball = Npc(ctx, BALL, HOLES[nextHole])
        nextHole = (nextHole + 1) % HOLES.size
        ball.respawnTicks = -1
        if (!world.npcs.add(ball)) {
            return
        }
        val game = Game(plr, ball)
        game.direction = Direction.NORTH
        game.moves = ROLL_OUT
        game.delay = ROLL_OUT_DELAY
        games[plr] = game
        plr.queue(MobHintMessageWriter(ball))
    }

    /**
     * Takes [plr]'s ball away and ends their lap.
     */
    fun giveUp(plr: Player) {
        games.remove(plr)?.let { remove(it) }
    }

    /**
     * Kicks [ball] with [shot] if it's [plr]'s: straight away from them, starting on the next tick.
     */
    fun kick(plr: Player, ball: Npc, shot: Shot) {
        val game = games[plr] ?: return
        if (game.ball != ball || !playing(plr)) {
            return
        }
        val direction = Direction.between(plr.position, ball.position)
        if (direction == Direction.NONE || direction.isDiagonal) {
            return
        }
        plr.face(direction)
        plr.animation(Animation(KICK))
        plr.queue(SoundMessageWriter(Sound.SKULLBALL_TAP.id, 1, TAP_DELAY))
        game.direction = direction
        game.moves = shot.squares
        game.delay = 1
    }

    /**
     * Points [plr]'s arrow at their next goal, or the hole once all ten are scored, for a few ticks.
     */
    fun showGoal(plr: Player, ball: Npc) {
        val game = games[plr] ?: return
        if (game.ball != ball || !playing(plr)) {
            return
        }
        val target = if (game.goals < GOALS.size) GOALS[game.goals].middle else FINISH
        plr.queue(PositionHintMessageWriter(target, GOAL_ARROW_HEIGHT))
        game.showGoal = SHOW_GOAL_TICKS
    }

    /**
     * Moves every ball a square and runs each lap's timers. Runs once a tick, before input is handled, so a step
     * queued here is taken this tick.
     */
    fun process() {
        for (game in games.values.toList()) {
            val plr = game.player
            val finishing = game.finishIn > 0 || game.resultIn > 0
            if (plr.state != EntityState.ACTIVE || (!finishing && (!inside(plr.position) ||
                        game.ball.state != EntityState.ACTIVE))) {
                games.remove(plr)
                remove(game)
                continue
            }
            if (game.showGoal > 0 && --game.showGoal == 0) {
                plr.queue(MobHintMessageWriter(game.ball))
            }
            when {
                game.resultIn > 0 -> if (--game.resultIn == 0) result(game)
                game.finishIn > 0 -> if (--game.finishIn == 0) finish(game)
                game.delay > 0 -> if (--game.delay == 0 && game.moves > 0) roll(game)
                game.moves > 0 -> roll(game)
            }
        }
    }

    /**
     * Ends [plr]'s lap when they log out.
     */
    fun logout(plr: Player) {
        games.remove(plr)?.let { remove(it) }
    }

    /**
     * The experience for a lap of [ticks].
     */
    fun xpFor(ticks: Int): Int =
        if (ticks <= FULL_XP_TICKS) FULL_XP else maxOf(0, floor(FULL_XP - XP_PER_TICK * (ticks - FULL_XP_TICKS)).toInt())

    /**
     * [ticks] as minutes and seconds, the way the result screen shows them.
     */
    fun time(ticks: Int): String {
        val seconds = ticks * 6 / 10
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /**
     * Rolls the ball one square on. A ball that meets a wall turns back with half its remaining squares, and stops if
     * it can't move that way either. The player hears one sound a tick, since their client plays one at a time: a
     * goal over the wall, and the wall over the roll.
     */
    private fun roll(game: Game) {
        val ball = game.ball
        val from = ball.position
        if (game.goals == GOALS.size && from == FINISH_FROM && game.direction == Direction.NORTH) {
            ball.walking.addStep(Direction.NORTH)
            game.moves = 0
            game.finishIn = FINISH_DELAY
            game.player.playSound(Sound.SKULLBALL_ROLL)
            return
        }
        var sound = Sound.SKULLBALL_ROLL
        if (!rollable(from, game.direction)) {
            sound = Sound.SKULLBALL_COLLIDE
            game.moves /= 2
            game.direction = game.direction.opposite()
            if (game.moves == 0 || !rollable(from, game.direction)) {
                game.moves = 0
                game.player.playSound(sound)
                return
            }
        }
        ball.walking.addStep(game.direction)
        game.moves--
        if (pass(game, from.translate(1, game.direction))) {
            sound = Sound.SKELETONGOAL
        }
        game.player.playSound(sound)
    }

    /**
     * Whether the ball can roll from [from] towards [direction]. Goals' middles are solid, but the ball rolls through
     * them between the posts. The static map is used, so the ball rolls through people.
     */
    private fun rollable(from: Position, direction: Direction): Boolean {
        val to = from.translate(1, direction)
        return GOALS.any { it.middle == to } || world.collisionManager.traversable(from, EntityType.PLAYER, direction)
    }

    /**
     * Swings the goal the ball rolls into at [tile], if there is one, and scores it if it's the next goal and the ball
     * goes the course's way. Returns whether it scored.
     */
    private fun pass(game: Game, tile: Position): Boolean {
        val index = GOALS.indexOfFirst { it.middle == tile }
        if (index == -1) {
            return false
        }
        val forward = game.direction == GOALS[index].direction
        world.objects.findAll(tile).filter { it.id == GOAL }.findFirst()
            .ifPresent { it.animate(if (forward) GOAL_FORWARD else GOAL_BACKWARD) }
        if (!forward || index != game.goals) {
            return false
        }
        game.goals++
        if (game.goals == 1) {
            game.started = world.currentTick
        }
        val plr = game.player
        val look = if (plr.appearance.isMale) "Look at him go!" else "Look at her go!"
        shout(game, if (passesAsWerewolf(plr)) (GOAL_LINES + look).random() else
            "Humans play skullball worse than puppies!")
        plr.playJingle(Jingles.SKULLBALL_GOAL)
        return true
    }

    /**
     * The ball is in the hole: the lap is over, and the result screen follows.
     */
    private fun finish(game: Game) {
        val plr = game.player
        game.lapTicks = (world.currentTick - game.started).toInt()
        plr.sendMessage("Well done - you've finished the skullball course!!!")
        val pronoun = if (plr.appearance.isMale) "he" else "she"
        shout(game, if (passesAsWerewolf(plr))
            "${pronoun.replaceFirstChar { it.uppercase() }} shoots - $pronoun scores!!!!!" else "The human's scored!")
        plr.animation(Animation(CELEBRATE))
        remove(game)
        game.resultIn = RESULT_DELAY
    }

    /**
     * Shows [game]'s time and score, and pays the experience.
     */
    private fun result(game: Game) {
        val plr = game.player
        games.remove(plr)
        val xp = xpFor(game.lapTicks)
        plr.sendText(time(game.lapTicks), RESULT_TIME)
        plr.sendText(xp, RESULT_XP)
        plr.overlays.open(StandardInterface(RESULT))
        if (xp > 0) {
            plr.agility.addExperience(xp.toDouble())
        }
    }

    /**
     * Has the trainer nearest the ball shout [line].
     */
    private fun shout(game: Game, line: String) {
        world.locator.findNpcs(game.ball, 16, true) { it.id == TRAINER }.firstOrNull()?.speak(line)
    }

    /**
     * Takes [game]'s ball out of the world and the arrow off its player's screen.
     */
    private fun remove(game: Game) {
        if (game.ball.state == EntityState.ACTIVE) {
            world.npcs.remove(game.ball)
        }
        if (game.player.state == EntityState.ACTIVE) {
            game.player.queue(ClearHintMessageWriter())
        }
    }
}
