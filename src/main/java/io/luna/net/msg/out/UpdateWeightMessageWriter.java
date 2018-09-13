package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays the weight value.
 *
 * @author lare96 <http://github.org/lare96>
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
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(240);
        msg.putShort(weight);
        return msg;
    }
}
