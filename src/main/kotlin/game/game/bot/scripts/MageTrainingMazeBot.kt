package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.action.BotNpcDialogues
import game.content.mageTrainingArena.MageTrainingArena
import game.content.mageTrainingArena.MageTrainingArena.arenaRoom
import game.content.mageTrainingArena.TelekineticTheatre
import game.content.mageTrainingArena.TelekineticTheatre.Maze
import game.skill.magic.telekineticGrab.TelekineticGrabAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Solves the live Telekinetic Theatre layout using the same slide collision as the arena.
 *
 * @author lare96
 */
internal object MageTrainingMazeBot {
    private val directions = listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)

    internal fun solve(maze: Maze, start: Position): List<Direction>? {
        val paths = mutableMapOf(start to emptyList<Direction>())
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            val from = pending.removeFirst()
            val path = paths.getValue(from)
            if (from == maze.exit) return path
            for (direction in directions) {
                var stop = from
                while (true) {
                    stop = maze.slideStep(stop, direction) ?: break
                }
                if (stop !in paths) {
                    paths[stop] = path + direction
                    pending.addLast(stop)
                }
            }
        }
        return null
    }

    suspend fun play(bot: Bot, allowed: () -> Boolean) {
        repeat(5) { index ->
            val statue = TelekineticTheatre.stationaryStatue(bot) ?: return
            val maze = TelekineticTheatre.MAZES.firstOrNull { it.contains(statue.position) } ?: return
            val path = solve(maze, statue.position) ?: return
            for (direction in path) {
                if (bot.arenaRoom !== TelekineticTheatre || !allowed()) return
                if (!pull(bot, maze, direction)) {
                    bot.log("Mage Training Arena: could not pull the statue ${direction.name.lowercase()}.")
                    return
                }
            }
            if (index == 4 || !allowed()) return
            if (!nextMaze(bot)) {
                bot.log("Mage Training Arena: could not start the next maze.")
                return
            }
        }
    }

    private suspend fun pull(bot: Bot, maze: Maze, direction: Direction): Boolean {
        val statue = TelekineticTheatre.stationaryStatue(bot) ?: return false
        val from = statue.position
        val edge = when (direction) {
            Direction.NORTH -> Position(from.x, maze.maxY + 1, maze.z)
            Direction.SOUTH -> Position(from.x, maze.minY - 1, maze.z)
            Direction.EAST -> Position(maze.maxX + 1, from.y, maze.z)
            Direction.WEST -> Position(maze.minX - 1, from.y, maze.z)
            else -> return false
        }
        val candidates = buildList {
            add(bot.position)
            add(edge)
            for (along in -9..9) {
                val side = if (direction.translateX == 0) edge.translate(along, 0) else edge.translate(0, along)
                add(side)
                add(side.translate(direction.translateX, direction.translateY))
            }
        }.distinct().filter {
            maze.pull(it) == direction && it.isViewable(from) && TelekineticTheatre.inside(it)
        }.sortedBy { it.computeLongestDistance(bot.position) }
        if (candidates.none { walk(bot, it) }) return false
        if (!bot.output.useSpellOnGroundItem(TelekineticGrabAction.SPELL_ID, statue)) return false
        return waitFor(15.seconds) {
            val current = TelekineticTheatre.stationaryStatue(bot)
            current != null && current.position != from || guardian(bot) != null
        }
    }

    private fun guardian(bot: Bot) = bot.world.locator.findNearestNpc(bot, 18) {
        it.id == MageTrainingArena.MAZE_GUARDIAN && TelekineticTheatre.ownsGuardian(bot, it)
    }

    private suspend fun nextMaze(bot: Bot): Boolean {
        val guardian = guardian(bot) ?: return false
        val policy = InteractionPolicy.STANDARD_LINE_OF_SIGHT
        BotNpcDialogues.respond(bot, guardian, listOf("Yes please!"),
            completed = { TelekineticTheatre.stationaryStatue(bot) != null }) {
            bot.actionHandler.interactions.approachLocally(guardian, policy) && bot.output.sendNpcInteraction(1, guardian)
        }
        return waitFor(6.seconds) { TelekineticTheatre.stationaryStatue(bot) != null } && bot.closeArenaDialogue()
    }

    private suspend fun walk(bot: Bot, position: Position): Boolean = withTimeoutOrNull(20.seconds) {
        if (bot.position == position) return@withTimeoutOrNull true
        val navigation = bot.navigator.navigate(position, true)
        try {
            navigation.await() == NavigationResult.REACHED && bot.position == position
        } catch (cancelled: CancellationException) {
            if (bot.navigator.currentPending === navigation) {
                bot.navigator.cancel()
                bot.walking.clear()
            }
            throw cancelled
        }
    } == true
}
