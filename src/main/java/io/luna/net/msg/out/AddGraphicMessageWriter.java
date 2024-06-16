package io.luna.net.msg.out;

import io.luna.game.model.StationaryGraphic;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays a {@link StationaryGraphic}.
 *
 * @author lare96
 */
public final class AddGraphicMessageWriter extends GameMessageWriter {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The offset.
     */
    private final int offset;

    /**
     * The graphic height.
     */
    private final int height;

    /**
     * The graphic delay.
     */
    private final int delay;

    /**
     * Creates a new {@link AddGraphicMessageWriter}.
     *
     * @param id The identifier.
     * @param offset The offset.
     * @param height The graphic height.
     * @param delay The graphic delay.
     */
    public AddGraphicMessageWriter(int id, int offset, int height, int delay) {
        this.id = id;
        this.offset = offset;
        this.height = height;
        this.delay = delay;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(4);
        msg.put(offset);
        msg.putShort(id);
        msg.put(height);
        msg.putShort(delay);
        return msg;
    }
}
