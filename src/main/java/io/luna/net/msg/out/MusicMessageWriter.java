package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that plays a song.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class MusicMessageWriter extends MessageWriter {

    /**
     * The song identifier.
     */
    private final int id;

    /**
     * Creates a new {@link MusicMessageWriter}.
     *
     * @param id The song identifier.
     */
    public MusicMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(74);
        msg.putShort(id, ByteOrder.LITTLE);
        return msg;
    }
}
