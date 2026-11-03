package io.luna.net.msg.out

import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Checks native position-hint height and the fixed-length clear packet independently of gameplay.
 *
 * @author lare96
 */
class PositionHintMessageWriterTest {
    @Test
    fun hintPacketsSetNativeHeightAndClearWithFixedLength() {
        val p = mock(Player::class.java)
        for (target in listOf(Position(2809, 3194), null)) {
            val buffer = Unpooled.buffer()
            try {
                PositionHintMessageWriter(target, 60).write(p, buffer)
                assertEquals(6, buffer.readableBytes())
                assertEquals(if (target == null) 0 else 2, buffer.readUnsignedByte().toInt())
                assertEquals(target?.x ?: 0, buffer.readUnsignedShort())
                assertEquals(target?.y ?: 0, buffer.readUnsignedShort())
                assertEquals(if (target == null) 0 else 60, buffer.readUnsignedByte().toInt())
            } finally { buffer.release() }
        }
    }
}
