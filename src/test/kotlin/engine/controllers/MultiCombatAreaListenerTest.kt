package engine.controllers

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [MultiCombatAreaListener].
 *
 * @author TheLining
 */
class MultiCombatAreaListenerTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "Wilderness volcano, 3365, 3935, 0, true",
        "Fountain of Rune, 3374, 3893, 0, true",
        "Chaos Elemental, 3261, 3927, 0, true",
        "Rogues' Castle tower top, 3281, 3934, 3, true",
        "King Black Dragon, 2269, 4697, 0, true",
        "Crash Island Dungeon snake pit, 3021, 5488, 0, true",
        "Ranging Guild top floor, 2668, 3427, 2, true",
        "Ranging Guild first floor, 2668, 3427, 1, false",
        "Falador cow field, 3025, 3305, 0, false",
        "North of the Grand Tree, 2460, 3560, 0, false",
        "Lumbridge, 3222, 3218, 0, false"
    )
    fun `multi-combat status`(place: String, x: Int, y: Int, z: Int, multi: Boolean) {
        assertEquals(multi, MultiCombatAreaListener.inside(Position(x, y, z)), place)
    }
}
