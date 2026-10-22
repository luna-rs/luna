package game.content.barrows

import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.net.codec.ByteOrder
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.login.LoginRequestMessage
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Checks prayer timing, floors, head selection and memory-mode behavior through the area lifecycle.
 *
 * @author lare96
 */
class BarrowsDrainTest {
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val prayer = mock(Skill::class.java)
    private val packets = mutableListOf<GameMessageWriter>()
    private val manager = PersistentVarpManager(player)
    private val controllers = ControllerManager(player)
    private var points = 99

    @BeforeEach
    fun setup() {
        if (WidgetDefinition.ALL.get(4535).isEmpty) {
            val def = mock(WidgetDefinition::class.java)
            `when`(def.id()).thenReturn(4535)
            WidgetDefinition.ALL.storeDefinition(def)
        }
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.skill(Skill.PRAYER)).thenReturn(prayer)
        `when`(prayer.level).thenAnswer { points }
        doAnswer { points = it.getArgument<Int>(0); null }.`when`(prayer).level = anyInt()
        doAnswer { packets += it.getArgument<GameMessageWriter>(0); null }
            .`when`(player).queue(any(GameMessageWriter::class.java))
        position(Position(3555, 9697, 3))
    }

    private fun position(position: Position) {
        `when`(player.position).thenReturn(position)
        controllers.checkPosition()
    }

    @Test
    fun drainsEveryThirtyTicksAcrossCryptAndTunnelMovesAndRestartsAfterExit() {
        repeat(29) { controllers.process() }
        assertEquals(99, points)
        position(Position(3537, 9711, 0))
        controllers.process()
        assertEquals(91, points)
        repeat(30) { controllers.process() }
        assertEquals(83, points)
        position(Position(3563, 3289))
        repeat(90) { controllers.process() }
        assertEquals(83, points)
        position(Position(3555, 9697, 3))
        repeat(29) { controllers.process() }
        assertEquals(83, points)
        controllers.process()
        assertEquals(75, points)
    }

    @Test
    fun allSixBrothersIncreaseDrainToFourteenAndZeroDeactivatesPrayers() {
        manager.setValue(PersistentVarp.BARROWS_KILLS, 63)
        BarrowsDrain.drain(player)
        assertEquals(85, points)
        points = 4
        BarrowsDrain.drain(player)
        assertEquals(0, points)
        verify(player.combat.prayers).deactivateAll()
    }

    @Test
    fun eachDefeatedBrotherUsesItsNormalOrGreenItemAndResetsAnimationFirst() {
        for (brother in BarrowsBrother.entries) {
            manager.setValue(PersistentVarp.BARROWS_KILLS, brother.killed.write(0, 1))
            for (plane in listOf(3, 0)) {
                position(Position(3555, 9697, plane))
                packets.clear()
                BarrowsDrain.drain(player)
                assertEquals(3, packets.size)
                assertTrue(packets.first() is WidgetAnimationMessageWriter)
                assertTrue(packets.last() is WidgetAnimationMessageWriter)
                val buffer = Unpooled.buffer()
                try {
                    val message = (packets[1] as WidgetItemModelMessageWriter).write(player, buffer)
                    assertEquals(100, message.getShort())
                    assertEquals(4761 + brother.ordinal * 2 + if (plane == 0) 1 else 0,
                                 message.getShort(ByteOrder.LITTLE))
                } finally {
                    buffer.release()
                }
            }
        }
    }

    @Test
    fun lowMemoryAndNoDefeatedBrothersSuppressHeadsButNotPrayerDrain() {
        packets.clear()
        BarrowsDrain.drain(player)
        assertEquals(91, points)
        assertTrue(packets.isEmpty())
        manager.setValue(PersistentVarp.BARROWS_KILLS, 63)
        `when`(player.isLowMemory).thenReturn(true)
        BarrowsDrain.drain(player)
        assertEquals(77, points)
        assertTrue(packets.isEmpty())
        assertTrue(LoginRequestMessage("fixture", "fixture", null, null, null, true).isLowMemory)
        assertFalse(LoginRequestMessage("fixture", "fixture", null, null, null).isLowMemory)
    }
}
