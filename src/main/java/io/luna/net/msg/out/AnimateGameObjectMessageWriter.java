package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that animates an object.
 *
 * @author lare96
 */
public final class AnimateGameObjectMessageWriter extends GameMessageWriter {

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
        ByteMessage msg = ByteMessage.message(160);
        msg.put(offset, ValueType.SUBTRACT);
        msg.put((type << 2) + (direction & 3), ValueType.SUBTRACT);
        msg.putShort(id, ValueType.ADD);
        return msg;
    }
}
