package api.bot.webwalk.generate

import api.predef.*
import engine.obj.LadderDestination
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Resolves entrance interaction stands on the physical plane where the native client displays each object.
 * Raw map positions remain unchanged for object lookup and ordinary click packets.
 *
 * @author lare96
 */
internal object EntranceStands {

    /**
     * Uses native bridge lowering and the rotated footprint, then checks actual player collision reach.
     */
    fun of(obj: GameObject, gameWorld: World = world,
           canLand: (Position) -> Boolean = LadderDestination::canLand): List<Position> {
        val raw = obj.position
        if (raw.z == 0 || !gameWorld.collisionManager.isLoweredOnto(obj, raw.z - 1)) {
            return LadderDestination.climbingTiles(obj)
        }
        val turned = obj.direction == ObjectDirection.NORTH || obj.direction == ObjectDirection.SOUTH
        val width = if (turned) obj.sizeY() else obj.sizeX()
        val length = if (turned) obj.sizeX() else obj.sizeY()
        val physical = raw.translate(0, 0, -1)
        val candidates = buildList {
            for (x in physical.x until physical.x + width) {
                add(Position(x, physical.y - 1, physical.z))
                add(Position(x, physical.y + length, physical.z))
            }
            for (y in physical.y until physical.y + length) {
                add(Position(physical.x - 1, y, physical.z))
                add(Position(physical.x + width, y, physical.z))
            }
        }
        return candidates.filter {
            canLand(it) && gameWorld.collisionManager.reached(it, obj, InteractionPolicy.STANDARD_SIZE)
        }
    }
}
