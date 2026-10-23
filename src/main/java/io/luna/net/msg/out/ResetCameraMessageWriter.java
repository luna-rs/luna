package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * Releases cutscene camera control and clears all five camera shake slots in the 377 client.
 *
 * @author lare96
 */
public final class ResetCameraMessageWriter extends GameMessageWriter {
    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        return ByteMessage.message(148, buffer);
    }
}
