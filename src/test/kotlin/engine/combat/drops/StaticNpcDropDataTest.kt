package engine.combat.drops

import api.drops.DropTableHandler
import api.drops.SimpleDropTable
import engine.bot.coordinator.skill.ScriptFixtures
import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Validates imported source roll boundaries and registration through the native JSON loader.
 *
 * @author lare96
 */
class StaticNpcDropDataTest {
    companion object {
        private val tables: Array<StaticNpcDropTable> = GsonUtils.readAsType(
            Path.of("data/game/def/npcs/drops.jsonc"), Array<StaticNpcDropTable>::class.java)

        @JvmStatic
        @BeforeAll
        fun initialize() {
            ScriptFixtures.initialize()
        }
    }

    private fun drops(id: Int): List<StaticNpcDrop> = tables.single { id in it.ids }.drops

    @Test
    fun npcIdsAreUniqueAndDoNotReplaceExistingScriptedTables() {
        val ids = tables.flatMap { it.ids }
        assertEquals(ids.size, ids.toSet().size)
        val scripted = (419..424).toSet() + (431..436) + (438..443)
        assertTrue(ids.none { it in scripted })
    }

    @Test
    fun impCommonRollIsCompleteAndChampionScrollRollsSeparately() {
        val imp = drops(708)
        val scroll = imp.single { it.id == 6803 }
        val common = imp.filter { it.table == 1 && it.rarity < 1.0 }
        assertEquals(1.0, common.sumOf { it.rarity }, 1e-12)
        assertEquals(5.0 / 128, common.single { it.id == 1474 }.rarity)
        assertEquals(1.0 / 128, common.single { it.id == 195 }.rarity)
        assertEquals(1.0 / 5000, scroll.rarity)
        assertNotEquals(1, scroll.table)
        val goblinScroll = drops(489).single { it.id == 6801 }
        assertEquals(1.0 / 5000, goblinScroll.rarity)
        assertNotEquals(1, goblinScroll.table)
    }

    @Test
    fun mediumCluesUseSeparateRollsForSourcesOutsideTheExistingClueRegistry() {
        for (id in listOf(988, 989, 990, 1076, 1077)) {
            val clue = drops(id).single { it.id == 2801 }
            assertEquals(1.0 / 128, clue.rarity)
            assertNotEquals(1, clue.table)
        }
        for (id in listOf(20, 32, 3222, 3406)) {
            assertFalse(drops(id).any { it.id in setOf(2677, 2801) })
        }
    }

    @Test
    fun guardKeepsEightEmptySlotsAndCanifisRollsUse512Slots() {
        assertEquals(120.0 / 128, drops(32).filter { it.rarity < 1.0 }.sumOf { it.rarity }, 1e-12)
        val citizen = drops(1024).filter { it.rarity < 1.0 }
        val werewolf = drops(1030).filter { it.rarity < 1.0 }
        assertEquals(10.0 / 512, citizen.single { it.id == 1353 }.rarity)
        assertEquals(32.0 / 512, werewolf.single { it.id == 1325 }.rarity)
        assertEquals(511.0 / 512, citizen.sumOf { it.rarity }, 1e-12)
        assertEquals(509.0 / 512, werewolf.sumOf { it.rarity }, 1e-12)
        for (entries in listOf(citizen, werewolf)) {
            assertEquals(1.0 / 512, entries.single { it.shared == "JEWEL" }.rarity)
        }
    }

    @Test
    fun rockGolemTiersKeepTheirDifferentRollSizesAndPaladinsHaveNoDefaultBones() {
        val low = drops(413)
        val high = drops(417)
        assertEquals(30.0 / 78, low.single { it.id == 557 && it.quantity == "5" }.rarity, 1e-12)
        assertEquals(30.0 / 128, high.single { it.id == 557 && it.quantity == "5" }.rarity)
        assertFalse(low.any { it.shared != null || it.id in setOf(1265, 1271, 1275) })
        assertEquals(2.0 / 128, high.single { it.shared == "JEWEL" }.rarity)
        assertFalse(drops(988).any { it.id == 526 && it.rarity >= 1.0 })
    }

    @Test
    fun nativeLoaderKeepsPlainRollsSimpleAndLoadsSharedRollsInTheSameSubtable() {
        val script = Class.forName("engine.combat.drops.NpcDropLoader")
            .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
        val load = script.javaClass.getMethod("load", Array<StaticNpcDropTable>::class.java)
        val freeIds = generateSequence(Int.MAX_VALUE) { it - 1 }
            .filter { DropTableHandler.getDropTable(it) == null }.take(2).toList()
        val definitions = arrayOf(
            StaticNpcDropTable(listOf(freeIds[0]), "", null, drops(413)),
            StaticNpcDropTable(listOf(freeIds[1]), "", null, drops(417)))
        load.invoke(script, definitions as Any)
        val plain = DropTableHandler.getDropTable(freeIds[0])!!.tableList
        val shared = DropTableHandler.getDropTable(freeIds[1])!!.tableList
        assertEquals(1, plain.size)
        assertInstanceOf(SimpleDropTable::class.java, plain.single())
        assertEquals(1, shared.size)
        assertInstanceOf(StaticNpcDropRoll::class.java, shared.single())
        assertTrue(shared.single().computePossibleItems().map { it.id }.containsAll(listOf(1452, 1462)))
    }
}
