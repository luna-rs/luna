package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * Adds a position based sound to the world.
 *
 * @author lare96
 */
public final class AddLocalSoundMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The sound ID.
     */
    private final int soundId;

    /**
     * ???
     */
    private final int radius; // loops? delay?

    /**
     * ???
     */
    private final int type; // loops? delay?

    /**
     * The offset.
     */
    private final int offset;

    /**
     * Creates a new {@link AddLocalSoundMessageWriter}.
     *
     * @param soundId The sound ID.
     * @param radius ???
     * @param type ???
     * @param offset The offset.
     */
    public AddLocalSoundMessageWriter(int soundId, int radius, int type, int offset) {
        this.soundId = soundId;
        this.radius = radius;
        this.type = type;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(41);
        msg.put(offset);
        msg.putShort(soundId, ByteOrder.BIG);
        msg.put((radius << 4) + (type & 7));
        return msg;
    }
}
