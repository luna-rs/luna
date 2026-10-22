package game.content.barrows

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates candidate reward eligibility and ranges, without endorsing the source's unresolved quantities.
 *
 * @author lare96
 */
class BarrowsRewardDataTest {
    @Test
    fun allBrotherSubsetsUseOnlyTheirOwnFourRewardsAndIgnoreChestAndCountBits() {
        for (flags in 0..63) {
            val killed = BarrowsBrother.entries.filter { it.killed.read(flags) == 1 }
            val parent = BarrowsVarbit.MONSTER_SCORE.write(flags, 1000)
            val result = BarrowsRewardData.parameters(parent)
            assertEquals(killed.size + 1, result.rolls)
            assertEquals(1000 + killed.size * 2, result.potential)
            assertEquals(450 - 58 * killed.size, result.uniqueDenominator)
            assertEquals(killed.size * 4, result.uniquePool.size)
            assertEquals(result.uniquePool.size, result.uniquePool.toSet().size)
            val expected = killed.flatMap { BarrowsRewardData.uniqueItems.getValue(it) }
            assertEquals(expected, result.uniquePool)
            assertEquals(result, BarrowsRewardData.parameters(parent or -65536))
        }
        val all = BarrowsRewardData.parameters(BarrowsVarbit.MONSTER_SCORE.write(63, 656))
        assertEquals(7, all.rolls)
        assertEquals(668, all.potential)
        assertEquals(102, all.uniqueDenominator)
        assertEquals(24, all.uniquePool.size)
    }

    @Test
    fun defaultBandsCoverEachNonzeroRollExactlyOnceAndRespectThresholds() {
        assertNull(BarrowsRewardData.commonBand(0))
        for (roll in 1..1012) {
            assertEquals(1, BarrowsRewardData.commonBands.count { roll in it.rolls })
            assertNotNull(BarrowsRewardData.commonBand(roll))
        }
        assertEquals(listOf(995), BarrowsRewardData.commonBand(380)!!.itemIds)
        assertEquals(listOf(558), BarrowsRewardData.commonBand(381)!!.itemIds)
        assertEquals(listOf(4740), BarrowsRewardData.commonBand(1005)!!.itemIds)
        assertEquals(listOf(985, 987), BarrowsRewardData.commonBand(1006)!!.itemIds)
        assertEquals(listOf(985, 987), BarrowsRewardData.commonBand(1011)!!.itemIds)
        assertEquals(listOf(1149), BarrowsRewardData.commonBand(1012)!!.itemIds)
    }

    @Test
    fun uniqueItemsAreUnnotedPristineGearFromThe377Cache() {
        val definitions = Files.newBufferedReader(Path.of("data/dumps/items.json")).use {
            JsonParser.parseReader(it).asJsonArray.filterNot { entry -> entry.isJsonNull }
                .associate { entry -> entry.asJsonObject.get("id").asInt to entry.asJsonObject }
        }
        for ((brother, ids) in BarrowsRewardData.uniqueItems) {
            for (id in ids) {
                val definition = definitions.getValue(id)
                val name = definition.get("name").asString.lowercase()
                assertTrue(name.startsWith(brother.name.lowercase()), name)
                assertFalse(name.any { it.isDigit() }, name)
                assertFalse(definition.getAsJsonObject("unnoted_id").get("is_present").asBoolean, name)
            }
        }
        val expected = mapOf(995 to "Coins", 558 to "Mind rune", 562 to "Chaos rune", 560 to "Death rune",
                             565 to "Blood rune", 4740 to "Bolt rack", 985 to "Half of a key",
                             987 to "Half of a key", 1149 to "Dragon med helm")
        for (id in BarrowsRewardData.commonBands.flatMap { it.itemIds }) {
            assertEquals(expected.getValue(id), definitions.getValue(id).get("name").asString)
        }
    }
}
