package game.player.command

import io.luna.game.model.Position
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Frames overhead bot footage using the native client's camera pitch and roof-cutaway rules.
 * Roofed views hold their position because terrain-relative height can change while the client eases an orbit.
 *
 * @author lare96
 */
internal object BotTourCamera {
    const val TARGET_HEIGHT = 90
    private const val HEIGHT = 680
    private val orbit = listOf(-3 to -1, -1 to -3, 1 to -3, 3 to -1,
        3 to 1, 1 to 3, -1 to 3, -3 to 1)

    /**
     * Describes either an open-air orbit or a stationary position from which the aim can pan safely.
     *
     * @author lare96
     */
    data class Shot(val positions: List<Position>, val height: Int, val roofsHidden: Boolean) {

        /**
         * Keeps moving subjects within a downward view without relying on the client's pitch clamp.
         * These framing angles assume level ground; roof cutaway itself is independent of terrain heights.
         */
        fun frames(focus: Position): Boolean = positions.all { position ->
            if (position.z != focus.z) return@all false
            val distance = hypot((position.x - focus.x).toDouble(), (position.y - focus.y).toDouble()) * 128.0
            val pitch = atan2((height - TARGET_HEIGHT).toDouble(), distance) * 1024.0 / PI
            pitch in 256.0..380.0
        }
    }

    /**
     * Uses raw cache roof flags rather than collision flags, whose planes may have been adjusted for bridges.
     * Missing terrain and views outside the loaded scene are never treated as clear sky.
     */
    fun plan(anchor: Position, roofAt: (Position) -> Boolean?, inScene: (Position) -> Boolean): Shot? {
        if (!inScene(anchor)) return null
        val roofed = roofAt(anchor) ?: return null
        val positions = orbit.map { (dx, dy) -> anchor.translate(dx, dy) }

        fun clear(from: Position, to: Position): Boolean {
            for (x in min(from.x, to.x)..max(from.x, to.x)) {
                for (y in min(from.y, to.y)..max(from.y, to.y)) {
                    val position = Position(x, y, anchor.z)
                    if (!inScene(position) || roofAt(position) != false) return false
                }
            }
            return true
        }

        // A new target can arrive before an earlier pan completes; every point inside the orbit must stay clear.
        if (!roofed && clear(anchor.translate(-3, -3), anchor.translate(3, 3))) {
            return Shot(positions, HEIGHT, false)
        }

        val candidates = positions.map { it to HEIGHT } +
            listOf(-2 to -1, -1 to -2, 1 to -2, 2 to -1, 2 to 1, 1 to 2, -1 to 2, -2 to 1)
                .map { (dx, dy) -> anchor.translate(dx, dy) to HEIGHT } +
            listOf(-2 to 0, 0 to -2, 2 to 0, 0 to 2).map { (dx, dy) -> anchor.translate(dx, dy) to 660 } +
            listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (dx, dy) -> anchor.translate(dx, dy) to 420 } +
            listOf(-1 to 0, 0 to -1, 1 to 0, 0 to 1).map { (dx, dy) -> anchor.translate(dx, dy) to 340 }
        val covered = candidates.firstOrNull { (position, _) -> inScene(position) && roofAt(position) == true }
        if (covered != null) {
            // Snapping below 800 on a roof-marked tile hides roofs regardless of the native terrain height.
            // Smaller rooms use a lower camera so the target stays below the native maximum pitch of 383.
            return Shot(listOf(covered.first), covered.second, true)
        }
        if (roofed) return null

        val open = positions.firstOrNull { clear(it, anchor) } ?: return null
        return Shot(listOf(open), HEIGHT, false)
    }
}
