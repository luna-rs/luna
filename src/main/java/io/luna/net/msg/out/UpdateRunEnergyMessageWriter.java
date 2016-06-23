package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that sends the run energy value to the client.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateRunEnergyMessageWriter extends MessageWriter {

    /**
     * The run energy value to send.
     */
    private final int energy;

    /**
     * Creates a new {@link UpdateRunEnergyMessageWriter}.
     *
     * @param energy The run energy value to send.
     */
    public UpdateRunEnergyMessageWriter(int energy) {
        this.energy = energy;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(110);
        msg.put(energy);
        return msg;
    }
}
