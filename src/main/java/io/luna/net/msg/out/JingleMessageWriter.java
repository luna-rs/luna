package io.luna.net.msg.out;

import game.player.Jingles;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that plays a jingle (short tune).
 *
 * @author lare96
 */
public class JingleMessageWriter extends GameMessageWriter {

    /**
     * The jingle.
     */
    private final Jingles jingle;

    /**
     * The last played song (?).
     * <p>
     * TODO@0.5.0 Find out exactly what this is used for and implement it. Maybe the previously playing music track?
     */
    private final int lastPlayed = -1;

    /**
     * Creates a new {@link JingleMessageWriter}.
     *
     * @param jingle The jingle.
     */
    public JingleMessageWriter(Jingles jingle) {
        this.jingle = jingle;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(249, buffer);
        msg.putShort(jingle.getId(), ByteOrder.LITTLE);
        msg.putMedium(lastPlayed, ByteOrder.MIDDLE);
        return msg;
    }
}
