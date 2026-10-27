package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Selects a looping 377 background track, or stops all music with id -1/65535.
 *
 * @author lare96
 */
public final class MusicMessageWriter extends GameMessageWriter {

    private final int trackId;

    public MusicMessageWriter(int trackId) {
        checkArgument(trackId >= -1 && trackId <= 65535, "Invalid music track id: %s", trackId);
        this.trackId = trackId;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(220, buffer);
        msg.putShort(trackId, ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
