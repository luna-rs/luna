package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that displays the weight value.
 *
 * @author lare96
 */
public final class UpdateWeightMessageWriter extends GameMessageWriter {

    /**
     * The weight.
     */
    private final int weight;

    /**
     * Creates new {@link UpdateWeightMessageWriter}.
     *
     * @param weight The weight.
     */
    public UpdateWeightMessageWriter(int weight) {
        this.weight = weight;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(174, buffer);
        msg.putShort(weight);
        return msg;
    }
}
