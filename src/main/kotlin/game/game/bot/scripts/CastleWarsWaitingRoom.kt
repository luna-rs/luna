package game.bot.scripts

import game.content.castleWars.CastleWarsGame
import game.content.castleWars.CastleWarsMatch.Team
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.World
import java.util.ArrayDeque
import java.util.Deque

/**
 * Finds ordinary collision-checked walking paths to loosely spaced places in the bot's own waiting room.
 * Both the destination and every intermediate tile remain inside that room; no web travel or portals are used.
 *
 * @author lare96
 */
internal object CastleWarsWaitingRoom {
    fun contains(team: Team, position: Position): Boolean = CastleWarsGame.inWaitingRoom(position) &&
        position.computeLongestDistance(team.waiting) < position.computeLongestDistance(team.enemy.waiting)

    /**
     * Live positions and pending destinations keep arriving bots from selecting the same standing place.
     * A full or disconnected room has no fallback onto an occupied tile.
     */
    fun route(world: World, origin: Position, team: Team, occupied: Collection<Position>): Deque<Position>? {
        if (!contains(team, origin)) return null
        val collision = world.collisionManager
        val previous = hashMapOf(origin to origin)
        val queue = ArrayDeque<Position>().also { it.add(origin) }
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (direction in Direction.NESW) {
                val next = from.translate(1, direction)
                if (contains(team, next) && next !in previous &&
                    collision.traversable(from, EntityType.PLAYER, direction)) {
                    previous[next] = from
                    queue.add(next)
                }
            }
        }
        var tile = previous.keys.filter {
            it != origin && !it.isWithinDistance(team.waiting, 1) && !collision.isBlocked(it, false) &&
                occupied.none { other -> other.isWithinDistance(it, 1) }
        }.randomOrNull() ?: return null
        val path = ArrayDeque<Position>()
        while (tile != origin) {
            path.addFirst(tile)
            tile = previous.getValue(tile)
        }
        return path
    }
}
