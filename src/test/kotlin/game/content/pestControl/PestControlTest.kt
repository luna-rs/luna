package game.content.pestControl

import com.google.common.collect.ImmutableList
import io.luna.LunaContext
import io.luna.game.cache.Cache
import io.luna.game.cache.Archive
import io.netty.buffer.ByteBuf
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.codec.WidgetDefinitionDecoder
import io.luna.game.cache.map.MapIndex
import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercises original-era admission, settlement and the actual revision 377 map and reward interface.
 *
 * @author lare96
 */
class PestControlTest {
    private fun round(): PestControlRound<Int> = PestControlRound<Int>().also {
        repeat(5) { player -> assertTrue(it.join(player)) }
        repeat(500) { _ -> it.launch() }
        assertTrue(it.active)
    }

    @Test fun minimumAndMaximumCapacityAndFifoPriority() {
        val round = PestControlRound<Int>()
        repeat(4) { assertTrue(round.join(it)) }
        repeat(1000) { assertTrue(round.launch().isEmpty()) }
        assertTrue(round.join(4))
        assertEquals((0..4).toList(), round.launch())
        round.finish(false)
        repeat(30) { assertTrue(round.join(it)) }
        assertFalse(round.join(0))
        assertEquals((0..24).toList(), round.launch())
        assertEquals((25..29).toList(), round.waiting())
        assertTrue(round.launch().isEmpty())
        round.finish(false)
        repeat(499) { assertTrue(round.launch().isEmpty()) }
        assertEquals((25..29).toList(), round.launch())
    }

    @Test fun timerIsTwentyMinutesAndDeadKnightLosesAtBoundary() {
        val round = round()
        repeat(1999) { assertNull(round.tick(true)) }
        assertEquals(true, round.tick(true))
        assertEquals(5, round.finish(true).size)
        assertTrue(round.finish(true).isEmpty())
        assertNull(round.tick(true))
        val losing = round()
        repeat(1999) { losing.tick(true) }
        assertEquals(false, losing.tick(false))
    }

    @Test fun onlyFourDistinctPortalsWin() {
        val round = round()
        assertFalse(round.destroyPortal(-1))
        assertFalse(round.destroyPortal(4))
        repeat(3) { assertTrue(round.destroyPortal(it)); assertFalse(round.destroyPortal(it)) }
        assertNull(round.tick(true))
        assertTrue(round.destroyPortal(3))
        assertEquals(true, round.tick(true))
    }

    @Test fun participationLogoutLossAndRepeatedSettlement() {
        val round = round()
        round.credit(0, 49)
        round.credit(1, 50)
        repeat(10) { round.credit(2, 5) }
        round.credit(3, 100)
        round.leave(3)
        round.credit(3, 100)
        round.credit(4, -100)
        assertEquals(mapOf(0 to false, 1 to true, 2 to true, 4 to false), round.finish(true))
        assertTrue(round.finish(true).isEmpty())
        val losing = round()
        losing.credit(0, 100)
        assertTrue(losing.finish(false).values.none { it })
    }

    @Test fun abandonmentAndContributionOverflow() {
        val round = round()
        round.credit(0, Int.MAX_VALUE)
        round.credit(0, Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, round.contribution(0))
        repeat(5) { round.leave(it) }
        assertEquals(false, round.tick(true))
        assertTrue(round.finish(false).isEmpty())
    }

    @Test fun rewardsUseOriginalSinglePointRatesAndLevelRequirement() {
        assertEquals(15, PestControlRound.POINT_CAP)
        assertEquals(0, PestControlTraining.experience(24, Skill.ATTACK))
        assertEquals(104, PestControlTraining.experience(25, Skill.ATTACK))
        assertEquals(1633, PestControlTraining.experience(99, Skill.ATTACK))
        assertEquals(1633, PestControlTraining.experience(99, Skill.RANGED))
        assertEquals(816, PestControlTraining.experience(99, Skill.PRAYER))
        assertEquals(0, PestControlTraining.experience(99, Skill.COOKING))
    }

    @Test fun revision377AssetsContainEntryDefencesAndCombatTraining() {
        assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")), "Local revision 377 cache required")
        Cache().use { cache ->
            cache.open()
            val decoder = MapDecoder()
            val indices = ImmutableList.builder<MapIndex>()
            decoder.decode(cache, indices)
            val regions = setOf(Position(2656, 2592).region, Position(2656, 2650).region)
            decoder.handle(mock(LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region in regions }))
            val objects = cache.mapIndexTable.objectSet.objects
            for (id in listOf(14306, 14307, 14314, 14315, 14233, 14235)) {
                assertTrue(objects.any { it.objectId == id }, "Missing Pest Control object $id")
            }
            assertEquals(Position(2658, 2639, 1), objects.single { it.objectId == 14315 }.position)
            assertEquals(Position(2660, 2639), objects.single { it.objectId == 14314 }.position)
            val widgets = ImmutableList.builder<WidgetDefinition>()
            WidgetDefinitionDecoder().decode(cache, widgets)
            val definitions = widgets.build().associateBy { it.id() }
            assertEquals("Void Knights' Training Options", definitions[18758]?.disabledText)
            assertTrue(definitions.getValue(18691).children.contains(18783))
            val buf = Archive.decode(cache.getFile(0, 3)).getFileData("data")
            try {
                val buttons = mutableSetOf<Int>()
                val reader = WidgetDefinitionDecoder()
                val method = reader.javaClass.getDeclaredMethod("decodeEntry", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, ByteBuf::class.java).apply { isAccessible = true }
                buf.readUnsignedShort()
                var parent = -1
                while (buf.isReadable) {
                    var id = buf.readUnsignedShort()
                    if (id == 65535) { parent = buf.readUnsignedShort(); id = buf.readUnsignedShort() }
                    if (parent == 18691 && buf.getUnsignedByte(buf.readerIndex() + 1).toInt() != 0) {
                        buttons += id
                    }
                    method.invoke(reader, id, parent, buf)
                }
                assertTrue(buttons.containsAll((18759..18773).toList() + 18779))
                assertFalse(18780 in buttons, "The confirm label is not the actionable button")
            } finally { buf.release() }
        }
    }
}
