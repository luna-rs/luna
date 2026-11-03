package game.obj.entrances

import io.luna.game.model.Position

/**
 * The Mourner Headquarters basement trapdoor's native IDs and fixed landing, shared with route generation.
 * These retain the existing trapdoor script's journey and do not change its return ladder.
 *
 * @author lare96
 */
object MournerBasement {
    const val CLOSED = 8783
    const val OPEN = 8784
    val POSITION = Position(2542, 3327)
    val LANDING = Position(2044, 4649)
}
