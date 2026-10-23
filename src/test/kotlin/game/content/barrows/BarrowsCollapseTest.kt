package game.content.barrows

import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.net.msg.out.CameraShakeMessageWriter
import io.luna.net.msg.out.ResetCameraMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.random.Random

/**
 * Verifies restored collapse timing, safety after exit/death, and revision 377 camera packet encoding.
 *
 * @author lare96
 */
class BarrowsCollapseTest {
    @Test
    fun savedCollapseRestoresCameraAndOnlyDamagesLivingPlayersInTunnels() {
        val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val manager = PersistentVarpManager(player)
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.position).thenReturn(Position(3551, 9694))
        manager.setValue(PersistentVarp.BARROWS, BarrowsRun.COLLAPSING_MASK)
        val random = Random(688)
        BarrowsCollapse.enter(player)
        verify(player).queue(any(CameraShakeMessageWriter::class.java))
        repeat(9) { BarrowsCollapse.process(player, random) }
        verify(player, never()).damage(anyInt())
        BarrowsCollapse.process(player, random)
        verify(player).damage(intThat { it in 1..2 })
        `when`(player.position).thenReturn(Position(3555, 9697, 3))
        repeat(30) { BarrowsCollapse.process(player, random) }
        verify(player, times(1)).damage(anyInt())
        `when`(player.position).thenReturn(Position(3200, 3200))
        repeat(30) { BarrowsCollapse.process(player, random) }
        verify(player).queue(any(ResetCameraMessageWriter::class.java))
        `when`(player.position).thenReturn(Position(3551, 9694))
        `when`(player.isAlive).thenReturn(false)
        repeat(30) { BarrowsCollapse.process(player, random) }
        verify(player, times(1)).damage(anyInt())
    }

    @Test
    fun cameraPacketsMatch377ClientHandlers() {
        val player = mock(Player::class.java)
        val buffer = Unpooled.buffer()
        try {
            val shake = CameraShakeMessageWriter(0, 5, 0, 0).write(player, buffer)
            assertEquals(67, shake.opcode)
            assertEquals(listOf(0, 5, 0, 0), List(4) { shake.get().toInt() })
            buffer.clear()
            val reset = ResetCameraMessageWriter().write(player, buffer)
            assertEquals(148, reset.opcode)
            assertEquals(0, buffer.readableBytes())
        } finally {
            buffer.release()
        }
    }
}
