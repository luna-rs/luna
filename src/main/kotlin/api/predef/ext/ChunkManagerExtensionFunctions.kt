package api.predef.ext

import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkManager
import kotlin.reflect.KClass

/**
 * Forwards to [ChunkManager.findViewable] with [KClass] instead of [Class].
 */
fun <T : Entity> ChunkManager.findViewable(position: Position, type: KClass<T>): MutableSet<T> =
    findViewable(position, type.java)