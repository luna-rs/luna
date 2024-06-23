package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.StandardInterface;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} that opens an interface.  Use {@link StandardInterface} instead of using this
 * packet directly.
 *
 * @author lare96
 */
public final class InterfaceMessageWriter extends GameMessageWriter {

    /**
     * The interface identifier.
     */
    private final int id;

    /**
     * Creates a new {@link InterfaceMessageWriter}.
     *
     * @param id The interface identifier.
     */
    public InterfaceMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(159);
        msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
