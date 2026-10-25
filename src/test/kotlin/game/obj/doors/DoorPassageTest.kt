package game.obj.doors

import io.luna.game.model.Position
import game.content.miningGuild.MiningGuild
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*

/**
 * Verifies conditional doors reuse the ordinary door swing and reject mismatched types.
 *
 * @author lare96
 */
class DoorPassageTest {

    private val type = DoorType(2559, 2070, null, null, null, 3)

    @ParameterizedTest
    @EnumSource(ObjectDirection::class)
    fun conditionalDoorUsesReversibleSingleDoorSwing(direction: ObjectDirection) {
        val position = Position(2601, 9482)
        val closed = mock(GameObject::class.java)
        `when`(closed.position).thenReturn(position)
        `when`(closed.direction).thenReturn(direction)
        `when`(closed.objectType).thenReturn(ObjectType.STRAIGHT_WALL)
        val opened = Doors.singleSwap(closed, type, true)!!
        assertEquals(2070, opened.id)
        assertEquals(position.translate(1, direction.toNormalDirection()), opened.position)
        val open = mock(GameObject::class.java)
        `when`(open.position).thenReturn(opened.position)
        `when`(open.direction).thenReturn(opened.direction)
        `when`(open.objectType).thenReturn(ObjectType.STRAIGHT_WALL)
        val restored = Doors.singleSwap(open, type, false)!!
        assertEquals(2559, restored.id)
        assertEquals(position, restored.position)
        assertEquals(direction, restored.direction)
        assertEquals(opened.position, MiningGuild.doorDestination(position, direction, position))
        assertEquals(position, MiningGuild.doorDestination(position, direction, opened.position))
    }

    @Test
    fun mismatchedDoorCannotMovePlayerOrChangeWorld() {
        val world = mock(World::class.java)
        val player = mock(Player::class.java)
        val door = mock(GameObject::class.java)
        `when`(door.id).thenReturn(2070)
        assertFalse(Doors.passThrough(world, player, door, type, true))
        verifyNoInteractions(world, player)
    }

    @Test
    fun playerMustReachDoorBeforeItOpens() {
        val world = mock(World::class.java)
        val player = mock(Player::class.java)
        val door = mock(GameObject::class.java)
        `when`(door.id).thenReturn(2559)
        `when`(door.position).thenReturn(Position(2601, 9482))
        `when`(door.direction).thenReturn(ObjectDirection.NORTH)
        `when`(door.objectType).thenReturn(ObjectType.STRAIGHT_WALL)
        `when`(player.position).thenReturn(Position(2601, 9480))
        assertFalse(Doors.passThrough(world, player, door, type, true))
        verifyNoInteractions(world)
        verify(player).position
        verifyNoMoreInteractions(player)
    }
}
