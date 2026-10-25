package game.skill.thieving.lockedDoor

import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Checks free exit and locked entry on each orientation of the Yanille door.
 *
 * @author lare96
 */
class YanilleDoorActionTest {

    @ParameterizedTest
    @EnumSource(ObjectDirection::class)
    fun invertedLockAllowsExitFromDoorTile(direction: ObjectDirection) {
        val door = Position(2601, 9482)
        assertFalse(YanilleDoorAction.isLocked(door, direction, door))
        assertTrue(YanilleDoorAction.isLocked(door, direction,
                                             door.translate(1, direction.toNormalDirection())))
    }
}
