package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that displays the run energy value.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateRunEnergyMessageWriter extends MessageWriter {

    /**
     * The run energy.
     */
    private final int energy;

    /**
     * Creates a new {@link UpdateRunEnergyMessageWriter}.
     *
     * @param energy The run energy.
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
