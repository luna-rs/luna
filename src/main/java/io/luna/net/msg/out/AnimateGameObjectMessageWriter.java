package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that animates a {@link GameObject}.
 *
 * @author lare96
 */
public final class AnimateGameObjectMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    /**
     * The object offset.
     */
    private final int offset;

    /**
     * The object type.
     */
    private final int type;

    /**
     * The object direction.
     */
    private final int direction;

    /**
     * The animation ID.
     */
    private final int id;

    /**
     * Creates an {@link AnimateGameObjectMessageWriter}.
     *
     * @param offset The object offset.
     * @param type The object type.
     * @param direction The object direction.
     * @param id The animation ID.
     */
    public AnimateGameObjectMessageWriter(int offset, int type, int direction, int id) {
        this.offset = offset;
        this.type = type;
        this.direction = direction;
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(142);
        msg.putShort(id);
        msg.put((type << 2) + (direction & 3), ValueType.ADD);
        msg.put(offset);
        return msg;
    }
}
