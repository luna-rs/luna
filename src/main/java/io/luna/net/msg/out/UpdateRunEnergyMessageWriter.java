package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays the run energy value.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateRunEnergyMessageWriter extends GameMessageWriter {

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
