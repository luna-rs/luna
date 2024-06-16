package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.WalkableInterface;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays a walkable interface. Use {@link WalkableInterface}
 * instead of using this packet directly.
 *
 * @author lare96
 */
public final class WalkableInterfaceMessageWriter extends GameMessageWriter {

    /**
     * The interface identifier.
     */
    private final int id;

    /**
     * Creates a new {@link WalkableInterfaceMessageWriter}.
     *
     * @param id The interface identifier.
     */
    public WalkableInterfaceMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(50);
        msg.putShort(id);
        return msg;
    }
}
