package game.content.mageTrainingArena

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.player.Sound
import game.skill.magic.Magic
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import io.luna.game.action.impl.LockedAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.GroundItemClickEvent
import io.luna.game.event.impl.GroundItemOptionEvent
import io.luna.game.event.impl.UseSpellEvent.MagicOnGroundItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.net.msg.out.CameraTargetMessageWriter
import io.luna.net.msg.out.ResetCameraMessageWriter

/**
 * The Telekinetic Theatre: ten mazes, each holding a guardian turned to stone. Telekinetic Grab cast from one side of
 * a maze pulls the statue that way until a wall stops it; bringing it to the exit square frees the guardian.
 *
 * @author TheLining
 */
object TelekineticTheatre : ArenaRoom("Telekinetic Theatre", 10778, 33, 15962, Position(3363, 3316)) {

    /**
     * The statue, a ground item with the options Observe and Reset.
     */
    const val STATUE = 6888

    /**
     * The Telekinetic Pizazz Points line and the solved-in-a-row count on the overlay.
     */
    private const val POINTS_LINE = 15966
    private const val SOLVED_LINE = 15968

    /**
     * One maze: the ten by ten square the statue moves in, the exit square on its edge, where the statue starts, and
     * where a player arrives beside it.
     */
    class Maze(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val z: Int,
               val exit: Position, val start: Position, val arrival: Position) {

        /**
         * The middle of the maze, for the Observe view.
         */
        val centre = Position((minX + maxX + 1) / 2, (minY + maxY + 1) / 2, z)

        fun contains(pos: Position) = pos.z == z && pos.x in minX..maxX && pos.y in minY..maxY

        /**
         * The way a statue moves when the spell is cast from [pos]: towards the side of the maze [pos] is on. A
         * corner, or anywhere not beside the maze, moves it nowhere.
         */
        fun pull(pos: Position): Direction? {
            if (pos.z != z) return null
            val alongX = pos.x in minX..maxX
            val alongY = pos.y in minY..maxY
            return when {
                alongX && pos.y > maxY -> Direction.NORTH
                alongX && pos.y < minY -> Direction.SOUTH
                alongY && pos.x > maxX -> Direction.EAST
                alongY && pos.x < minX -> Direction.WEST
                else -> null
            }
        }

        /**
         * Whether [pos] is close enough to the maze to keep the Observe view.
         */
        fun nearby(pos: Position) = pos.z == z && pos.x in minX - 10..maxX + 10 && pos.y in minY - 10..maxY + 10
    }

    /**
     * The ten mazes. Walls and exit squares come from the cache; statue starts match the wiki's solution lengths.
     */
    val MAZES = listOf(
        Maze(3339, 9674, 3348, 9683, 2, Position(3339, 9683, 2), Position(3348, 9674, 2), Position(3368, 9680, 2)),
        Maze(3341, 9708, 3350, 9717, 1, Position(3341, 9708, 1), Position(3350, 9717, 1), Position(3362, 9713, 1)),
        Maze(3338, 9675, 3347, 9684, 0, Position(3342, 9684, 0), Position(3343, 9680, 0), Position(3354, 9690, 0)),
        Maze(3343, 9709, 3352, 9718, 2, Position(3345, 9718, 2), Position(3346, 9718, 2), Position(3359, 9701, 2)),
        Maze(3338, 9705, 3347, 9714, 0, Position(3347, 9714, 0), Position(3343, 9705, 0), Position(3336, 9718, 0)),
        Maze(3346, 9680, 3355, 9689, 1, Position(3353, 9680, 1), Position(3351, 9684, 1), Position(3355, 9693, 1)),
        Maze(3366, 9711, 3375, 9720, 0, Position(3367, 9720, 0), Position(3368, 9712, 0), Position(3379, 9716, 0)),
        Maze(3369, 9673, 3378, 9682, 0, Position(3375, 9682, 0), Position(3373, 9678, 0), Position(3374, 9696, 0)),
        Maze(3374, 9713, 3383, 9722, 1, Position(3383, 9713, 1), Position(3374, 9713, 1), Position(3378, 9706, 1)),
        Maze(3376, 9677, 3385, 9686, 1, Position(3385, 9686, 1), Position(3376, 9686, 1), Position(3382, 9698, 1))
    )

    /**
     * Mazes solved in a row. Kept between visits; every fifth earns the bonus.
     */
    var Player.mazeStreak by Attr.int().persist("mta_maze_streak")

    /**
     * A player's maze in progress.
     */
    class Attempt(val maze: Maze) {
        var statue: GroundItem? = null
        var guardian: Npc? = null
        var moving = false
        var observing = false
        val solved get() = guardian != null
    }

    private val attempts = HashMap<Player, Attempt>()

    override val roomItems = setOf(STATUE)

    override fun inside(position: Position) = position.x in 3328..3391 && position.y in 9664..9727 && position.z in 0..2

    override fun enter(plr: Player) {
        startMaze(plr, null)
    }

    /**
     * Sends [plr] to a maze other than [last] and sets its statue down.
     */
    private fun startMaze(plr: Player, last: Maze?) {
        clear(plr)
        val maze = MAZES.filter { it !== last }.random()
        val attempt = Attempt(maze)
        attempts[plr] = attempt
        plr.move(maze.arrival)
        placeStatue(plr, attempt, maze.start)
    }

    private fun placeStatue(plr: Player, attempt: Attempt, at: Position) {
        attempt.statue?.let { world.items.unregister(it) }
        val statue = GroundItem(ctx, STATUE, 1, at, ChunkUpdatableView.localView(plr))
        statue.setExpire(false)
        attempt.statue = if (world.items.register(statue)) statue else null
    }

    /**
     * Removes [plr]'s statue, freed guardian and Observe view.
     */
    private fun clear(plr: Player) {
        val attempt = attempts.remove(plr) ?: return
        attempt.statue?.let { world.items.unregister(it) }
        attempt.guardian?.let { if (it.state == EntityState.ACTIVE) world.npcs.remove(it) }
        if (attempt.observing) plr.queue(ResetCameraMessageWriter())
    }

    override fun leave(plr: Player, throughPortal: Boolean) {
        clear(plr)
    }

    override fun refreshOverlay(plr: Player) {
        plr.sendText(MageTrainingArena.points(plr, Points.TELEKINETIC), POINTS_LINE)
        plr.sendText(plr.mazeStreak, SOLVED_LINE)
    }

    override fun process(plr: Player) {
        val attempt = attempts[plr] ?: return
        if (attempt.observing && !attempt.maze.nearby(plr.position)) {
            attempt.observing = false
            plr.queue(ResetCameraMessageWriter())
        }
    }

    override fun event(plr: Player, event: ControllableEvent): Boolean {
        val attempt = attempts[plr]
        when (event) {
            is MagicOnGroundItemEvent -> {
                if (event.spellId != TelekineticGrabAction.SPELL_ID || event.targetItem.id != STATUE) return true
                if (attempt != null && event.targetItem === attempt.statue && !attempt.moving) {
                    plr.walking.clear()
                    plr.submitAction(PullStatue(plr, attempt))
                }
                return false
            }

            is GroundItemOptionEvent -> {
                if (event.groundItem.id != STATUE) return true
                if (attempt == null || event.groundItem !== attempt.statue) return false
                if (event.option == 0) observe(plr, attempt) else reset(plr, attempt)
                return false
            }

            is GroundItemClickEvent -> return event.groundItem.id != STATUE
        }
        return true
    }

    /**
     * Turns the Observe view over [attempt]'s maze on or off.
     */
    private fun observe(plr: Player, attempt: Attempt) {
        attempt.observing = !attempt.observing
        if (attempt.observing) {
            val centre = attempt.maze.centre
            // Eleven tiles south and 1800 up: the steepest the client tilts still frames the whole maze.
            plr.queue(CameraTargetMessageWriter(centre.translate(0, -11), 1800, 0, 100, false))
            plr.queue(CameraTargetMessageWriter(centre, 0, 0, 100, true))
        } else {
            plr.queue(ResetCameraMessageWriter())
        }
    }

    /**
     * Puts the statue back where it started.
     */
    private fun reset(plr: Player, attempt: Attempt) {
        if (attempt.moving || attempt.solved) return
        placeStatue(plr, attempt, attempt.maze.start)
    }

    /**
     * Frees the guardian once the statue reaches the exit square, and pays [plr] for the maze.
     */
    private fun solved(plr: Player, attempt: Attempt) {
        attempt.statue?.let { world.items.unregister(it) }
        attempt.statue = null
        val guardian = Npc(ctx, MageTrainingArena.MAZE_GUARDIAN, attempt.maze.exit)
        if (world.npcs.add(guardian)) attempt.guardian = guardian
        MageTrainingArena.addPoints(plr, Points.TELEKINETIC, 2)
        plr.mazeStreak++
        if (plr.mazeStreak >= 5) {
            plr.mazeStreak = 0
            MageTrainingArena.addPoints(plr, Points.TELEKINETIC, 8)
            plr.magic.addExperience(1000.0)
            val laws = Item(563, 10)
            if (!plr.inventory.add(laws)) world.items.register(GroundItem(ctx, laws, plr.position,
                                                                           ChunkUpdatableView.localView(plr)))
            plr.sendMessage("You have solved five mazes in a row! You receive 8 bonus Telekinetic Pizazz Points,")
            plr.sendMessage("1000 Magic experience and ten law runes.")
        } else {
            plr.sendMessage("You have solved the maze! You receive 2 Telekinetic Pizazz Points.")
        }
        refreshOverlay(plr)
    }

    /**
     * Whether [npc] is the guardian [plr] freed.
     */
    fun ownsGuardian(plr: Player, npc: Npc) = attempts[plr]?.guardian === npc

    /**
     * Talking to the freed guardian.
     */
    fun talkToGuardian(plr: Player, npc: Npc) {
        if (!ownsGuardian(plr, npc)) return
        plr.newDialogue()
            .npc(npc.id, "Well done on releasing me. Would you like to try another maze?")
            .options("Yes please!", {
                it.newDialogue().player("Yes please!").then { p ->
                    if (p.arenaRoom === this) startMaze(p, attempts[p]?.maze)
                }.open()
            }, "No thanks.", {
                it.newDialogue().player("No thanks.")
                    .npc(npc.id, "Very well. Talk to me if you want to move onto the next maze, or you can return to " +
                        "the entrance hall through the portal.").open()
            }).open()
    }

    /**
     * Telekinetic Grab cast on the statue: the usual cast, lock and projectile, after which the statue slides a tile
     * a tick towards the caster's side of the maze until something stops it.
     */
    private class PullStatue(plr: Player, private val attempt: Attempt) : LockedAction(plr) {

        private var direction: Direction? = null

        override fun beforeLock(): Boolean {
            val statue = attempt.statue ?: return false
            mob.face(statue.position)
            val costs = Magic.checkRequirements(mob, TelekineticGrabAction.LEVEL, TelekineticGrabAction.REQUIREMENTS)
                ?: return false
            mob.inventory.removeAll(costs)
            mob.magic.addExperience(43.0)
            direction = attempt.maze.pull(mob.position)
            attempt.moving = true
            return true
        }

        override fun run(): Boolean {
            val statue = attempt.statue
            if (statue == null || attempts[mob] !== attempt) {
                attempt.moving = false
                return true
            }
            when (executions) {
                0 -> {
                    mob.graphic(Graphic(142, 100))
                    mob.animation(Animation(791))
                    mob.playSound(Sound.TELEGRAB)
                }

                2 -> LocalProjectile.followPath(ctx)
                    .setSourcePosition(mob.position)
                    .setTargetPosition(statue.position)
                    .setId(143)
                    .setStartHeight(35)
                    .setEndHeight(10)
                    .setTicksToEnd(45)
                    .setTicksToStart(0)
                    .setInitialSlope(10)
                    .build()
                    .display()

                4 -> {
                    LocalGraphic(ctx, 144, 0, 0, statue.position, ChunkUpdatableView.localView(mob)).display()
                    slide(mob, attempt, direction)
                    return true
                }
            }
            return false
        }
    }

    /**
     * Slides the statue in [direction] a tile a tick until a wall or the maze edge stops it.
     */
    private fun slide(plr: Player, attempt: Attempt, direction: Direction?) {
        if (direction == null) {
            attempt.moving = false
            return
        }
        world.schedule(1, true) { task ->
            val statue = attempt.statue
            if (statue == null || attempts[plr] !== attempt || plr.state != EntityState.ACTIVE) {
                attempt.moving = false
                task.cancel()
                return@schedule
            }
            val from = statue.position
            val to = from.translate(direction.translateX, direction.translateY)
            if (!attempt.maze.contains(to) ||
                !world.collisionManager.traversable(from, EntityType.ITEM, direction)) {
                attempt.moving = false
                task.cancel()
                if (from == attempt.maze.exit) solved(plr, attempt)
                return@schedule
            }
            placeStatue(plr, attempt, to)
        }
    }
}
