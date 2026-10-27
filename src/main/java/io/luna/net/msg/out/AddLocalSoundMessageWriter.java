package io.luna.net.msg.out;

import io.luna.game.model.LocalSound;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link GameMessageWriter} implementation that displays a {@link LocalSound}.
 *
 * @author lare96
 */
public final class AddLocalSoundMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The sound ID.
     */
    private final int soundId;

    /**
     * The sound radius.
     */
    private final int radius;

    /**
     * The sound repeat count encoded in the low three bits.
     */
    private final int loops;

    /**
     * The position offset.
     */
    private final int offset;

    /**
     * Creates a new {@link AddLocalSoundMessageWriter}.
     *
     * @param soundId The sound ID.
     * @param radius The sound radius.
     * @param loops The sound repeat count, from one to seven.
     * @param offset The offset.
     */
    public AddLocalSoundMessageWriter(int soundId, int radius, int loops, int offset) {
        checkArgument(radius >= 0 && radius <= 15, "Invalid sound radius.");
        checkArgument(loops >= 1 && loops <= 7, "Invalid sound loop count.");
        this.soundId = soundId;
        this.radius = radius;
        this.loops = loops;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(41, buffer);
        msg.put(offset);
        msg.putShort(soundId, ByteOrder.BIG);
        msg.put((radius << 4) | loops);
        return msg;
    }
}
