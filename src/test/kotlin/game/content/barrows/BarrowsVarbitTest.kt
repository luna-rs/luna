package game.content.barrows

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * Checks the Barrows state layout against Luna's cache dump and exercises updates to shared parent varps.
 *
 * @author lare96
 */
class BarrowsVarbitTest {

    @Test
    fun layoutMatchesLunaCacheDefinitions() {
        val definitions = Files.newBufferedReader(Path.of("data/dumps/varbits.json")).use {
            JsonParser.parseReader(it).asJsonArray.associate { entry ->
                val definition = entry.asJsonObject
                definition.get("id").asInt to definition
            }
        }
        for (field in BarrowsVarbit.entries) {
            val definition = definitions.getValue(field.id)
            assertEquals(definition.get("parent_varp_id").asInt, field.parentId, field.name)
            // The saved dump reverses endpoint labels for multi-bit fields (463, 464 and 485).
            // Check its range without copying that reversal into the runtime layout.
            val endpoints = listOf(definition.get("least_significant_bit").asInt,
                                   definition.get("most_significant_bit").asInt).sorted()
            assertEquals(endpoints[0], field.firstBit, field.name)
            assertEquals(endpoints[1], field.lastBit, field.name)
        }
    }

    @Test
    fun writesRoundTripAndPreserveAllOtherBits() {
        val random = Random(688)
        for (field in BarrowsVarbit.entries) {
            val outside = (field.maxValue shl field.firstBit).inv()
            for (parent in listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE) + List(100) { random.nextInt() }) {
                for (value in listOf(0, 1, field.maxValue, random.nextInt(field.maxValue + 1))) {
                    val updated = field.write(parent, value)
                    assertEquals(value, field.read(updated), field.name)
                    assertEquals(parent and outside, updated and outside, field.name)
                }
            }
        }
    }

    @Test
    fun clearingChestAndBrotherFlagsPreservesScoreAndHighBitCount() {
        var parent = BarrowsVarbit.MONSTER_SCORE.write(0, 1000)
        parent = BarrowsVarbit.KILL_COUNT.write(parent, 32767)
        parent = BarrowsVarbit.CHEST_OPEN.write(parent, 1)
        parent = BarrowsVarbit.AHRIM_KILLED.write(parent, 1)
        parent = BarrowsVarbit.DHAROK_KILLED.write(parent, 1)
        parent = BarrowsVarbit.AHRIM_KILLED.write(parent, 0)
        parent = BarrowsVarbit.CHEST_OPEN.write(parent, 0)
        assertEquals(1000, BarrowsVarbit.MONSTER_SCORE.read(parent))
        assertEquals(32767, BarrowsVarbit.KILL_COUNT.read(parent))
        assertEquals(0, BarrowsVarbit.AHRIM_KILLED.read(parent))
        assertEquals(1, BarrowsVarbit.DHAROK_KILLED.read(parent))
        assertEquals(0, BarrowsVarbit.CHEST_OPEN.read(parent))
    }

    @Test
    fun outOfRangeValuesAreRejected() {
        for (field in BarrowsVarbit.entries) {
            assertThrows(IllegalArgumentException::class.java) { field.write(0, -1) }
            assertThrows(IllegalArgumentException::class.java) { field.write(0, field.maxValue + 1) }
        }
    }
}
