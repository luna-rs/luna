package game.content.barrows

import api.attr.Attr
import api.predef.ext.schedule
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableMessage
import io.luna.game.model.chunk.ChunkUpdatableRequest
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.net.msg.out.AddObjectMessageWriter
import io.luna.net.msg.out.RemoveObjectMessageWriter

/**
 * Crosses paired tunnel doors using each player's packed lock state.
 * Three-tick leaf visuals are private chunk updates. World objects and collision stay intact,
 * so opening a door never lets another player bypass their own maze or puzzle lock.
 *
 * @author lare96
 */
object BarrowsDoors {
    private var Player.crossingDoor by Attr.boolean()

    fun otherPosition(position: Position, direction: ObjectDirection, left: Boolean): Position {
        val sign = if (left) 1 else -1
        return when (direction) {
            ObjectDirection.WEST -> position.translate(0, sign)
            ObjectDirection.NORTH -> position.translate(sign, 0)
            ObjectDirection.EAST -> position.translate(0, -sign)
            ObjectDirection.SOUTH -> position.translate(-sign, 0)
        }
    }

    fun openPosition(position: Position, direction: ObjectDirection): Position =
        position.translate(1, direction.toNormalDirection())

    fun cross(player: Player, selected: GameObject, door: BarrowsDoor) {
        val state = BarrowsRun.state(player)
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.crossingDoor || state == 0 ||
            player.position.z != 0 || !BarrowsAreaListener.inside(player.position) ||
            !player.position.isWithinDistance(selected.position, 1) || door.locked.read(state) != 0 ||
            selected.id != door.leftId && selected.id != door.rightId) return

        val left = selected.id == door.leftId
        val otherId = if (left) door.rightId else door.leftId
        val otherPosition = otherPosition(selected.position, selected.direction, left)
        val other = player.world.locator.findObjectsOnTile(otherPosition) {
            it.id == otherId && it.objectType == selected.objectType && it.direction == selected.direction
        }.firstOrNull() ?: return

        if (door.leadsToChest && BarrowsVarbit.PUZZLE.read(state) != 3) {
            player.overlays.open(BarrowsPuzzleInterface(player.position))
            return
        }

        val openPosition = openPosition(selected.position, selected.direction)
        val otherOpenPosition = openPosition(other.position, selected.direction)
        val destination = if (player.position == openPosition || player.position == otherOpenPosition)
            selected.position else openPosition
        val view = ChunkUpdatableView.localView(player)
        fun visual(original: GameObject, id: Int, position: Position, rotation: Int) =
            GameObject.createDynamic(player.context, id, position, original.objectType,
                                     ObjectDirection.ALL[(selected.direction.id + rotation) % 4], view)
        val closed = listOf(visual(selected, selected.id, selected.position, 0),
                            visual(other, other.id, other.position, 0))
        val opened = listOf(visual(selected, if (left) 6732 else 6713, openPosition, if (left) 3 else 1),
                            visual(other, if (left) 6713 else 6732, otherOpenPosition, 2))
        player.crossingDoor = true
        closed.forEach { display(player, it, false) }
        opened.forEach { display(player, it, true) }
        player.playSound(Sound.BARROWS_DOOR_OPEN)
        player.move(destination)
        player.world.schedule(3) { task ->
            opened.forEach { display(player, it, false) }
            closed.forEach { display(player, it, true) }
            player.crossingDoor = false
            task.cancel()
        }
        BarrowsTunnelSpawns.spawn(player)
    }

    private fun display(player: Player, visual: GameObject, show: Boolean) {
        val chunk = visual.position.chunk
        val offset = chunk.offset(visual.position)
        val message: ChunkUpdatableMessage = if (show)
            AddObjectMessageWriter(visual.id, visual.objectType.id, visual.direction.id, offset)
        else RemoveObjectMessageWriter(visual.objectType.id, visual.direction.id, offset)
        player.world.chunks.load(chunk).queueUpdate(ChunkUpdatableRequest(visual, message, false))
    }
}
