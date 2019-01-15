package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that removes an object.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class RemoveObjectMessageWriter extends GameMessageWriter {

    /**
     * The object type.
     */
    private final int type;

    /**
     * The object orientation.
     */
    private final int direction;

    /**
     * The offset.
     */
    private final int offset;

    /**
     * Creates a new {@link RemoveObjectMessageWriter}.
     *
     * @param type The object type.
     * @param direction The object orientation.
     * @param offset The offset.
     */
    public RemoveObjectMessageWriter(int type, int direction, int offset) {
        this.type = type;
        this.direction = direction;
        this.offset = offset;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(101);
        msg.put(type + direction, ValueType.NEGATE);
        msg.put(offset);
        return msg;
    }
}