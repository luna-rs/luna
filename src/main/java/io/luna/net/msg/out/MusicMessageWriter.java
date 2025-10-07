package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that plays a song.
 *
 * @author lare96
 */
public final class MusicMessageWriter extends GameMessageWriter {
// todo jingles use packet 249
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
        ByteMessage msg = ByteMessage.message(220);
        msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
