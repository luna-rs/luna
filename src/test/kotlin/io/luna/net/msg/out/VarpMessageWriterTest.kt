package io.luna.net.msg.out

import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varp
import io.luna.net.codec.ByteOrder
import io.luna.net.codec.ValueType
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Protects signed varp values, including Barrows counts occupying bit 31, from byte truncation.
 *
 * @author lare96
 */
class VarpMessageWriterTest {
    @Test
    fun signedByteBoundariesAndFullWidthValuesRoundTrip() {
        if (VarpDefinition.ALL.get(453).isEmpty) {
            VarpDefinition.ALL.storeDefinition(VarpDefinition(453, 0))
        }
        val player = mock(Player::class.java)
        for (value in listOf(Int.MIN_VALUE, -131072, -129, -128, -1, 0, 127, 128, Int.MAX_VALUE)) {
            val buffer = Unpooled.buffer()
            try {
                val message = VarpMessageWriter(Varp(453, value)).write(player, buffer)
                if (value in -128..127) {
                    assertEquals(182, message.opcode)
                    assertEquals(453, message.getShort(ValueType.ADD))
                    assertEquals(value, (128 - buffer.readUnsignedByte()).toByte().toInt())
                } else {
                    assertEquals(115, message.opcode)
                    assertEquals(value, message.getInt(ByteOrder.INVERSE_MIDDLE))
                    assertEquals(453, message.getShort(ByteOrder.LITTLE))
                }
                assertEquals(0, buffer.readableBytes())
            } finally {
                buffer.release()
            }
        }
    }
}
