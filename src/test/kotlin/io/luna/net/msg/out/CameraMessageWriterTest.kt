package io.luna.net.msg.out

import io.luna.game.model.mob.Player
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Verifies revision 377 camera packet encoding independently of the content that sends it.
 *
 * @author lare96
 */
class CameraMessageWriterTest {
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
