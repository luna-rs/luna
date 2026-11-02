package engine.item.developerItem

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Validates coordinate forms, the complete destination registry and ambiguity handling.
 *
 * @author lare96
 */
class DeveloperNavigationTest {
    @Test fun coordinatesRespectHeightAndBounds() {
        assertEquals(Position(3200, 3201, 2), DeveloperNavigation.resolve(" 3200 , 3201 ", 2).position)
        assertEquals(Position(3200, 3201, 3), DeveloperNavigation.resolve("3200,3201,3", 0).position)
        for (input in listOf("", "3200", "1,2,3,4", "1,,2", "1,2,4", "-1,2", "16384,2", "1.5,2", "999999999999,2")) {
            val result = DeveloperNavigation.resolve(input, 0)
            assertNull(result.position, input)
            assertTrue(result.message.isNotBlank(), input)
        }
    }

    @Test fun everyRegisteredNameResolvesToItsEstablishedAnchor() {
        for (place in DeveloperNavigation.destinations) {
            assertEquals(place.position, DeveloperNavigation.resolve(
                " ${place.kind}:${place.name.lowercase().replace('_', ' ')} ", 3).position, place.name)
        }
        assertNull(DeveloperNavigation.resolve("not a real destination", 0).position)
    }

    @Test fun duplicateNamesRequireQualification() {
        val places = listOf(DeveloperNavigation.Destination("zone", "SAME_NAME", Position(1, 2)),
            DeveloperNavigation.Destination("subzone", "SAME_NAME", Position(3, 4)))
        assertTrue(DeveloperNavigation.resolve("same name", 0, places).message.startsWith("Ambiguous"))
        assertEquals(Position(3, 4), DeveloperNavigation.resolve("subzone:same name", 0, places).position)
    }

    @Test fun spacesUseCurrentHeightAndTruncatedNamesNeverChooseArbitrarily() {
        assertEquals(Position(3200, 3201, 2), DeveloperNavigation.resolve(" 3200   3201 ", 2).position)
        assertEquals(Position(3200, 3201, 3), DeveloperNavigation.resolve("3200 3201 3", 2).position)
        val places = listOf(DeveloperNavigation.Destination("zone", "LONG_PLACE_NAME", Position(1, 2)),
            DeveloperNavigation.Destination("subzone", "LONG_PLACE_OTHER", Position(3, 4)))
        val ambiguous = DeveloperNavigation.resolve("long place", 0, places)
        assertNull(ambiguous.position)
        assertEquals(places, ambiguous.matches)
        assertEquals(Position(1, 2), DeveloperNavigation.resolve("long place n", 0, places).position)
        for (place in DeveloperNavigation.destinations) {
            val input = place.name.lowercase().replace('_', ' ').take(12).trim()
            val result = DeveloperNavigation.resolve(input, 0)
            // Exact names may take precedence; otherwise the full original must remain selectable.
            assertTrue(result.position != null || result.matches.contains(place), place.name)
        }
    }

    @Test fun nativeNameEncodingPreservesSpaceSeparatedCoordinatesAndPrefixes() {
        for (input in listOf("3200 3201", "3200 3201 3")) {
            val decoded = io.luna.util.StringUtils.decodeFromBase37(io.luna.util.StringUtils.encodeToBase37(input))
            assertEquals(DeveloperNavigation.resolve(input, 2).position, DeveloperNavigation.resolve(decoded, 2).position)
        }
        val decoded = io.luna.util.StringUtils.decodeFromBase37(io.luna.util.StringUtils.encodeToBase37("wizards towe"))
        val result = DeveloperNavigation.resolve(decoded, 0)
        assertTrue(result.position != null || result.matches.isNotEmpty())
    }
}
