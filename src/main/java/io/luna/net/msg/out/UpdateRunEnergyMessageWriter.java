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

    public UpdateRunEnergyMessageWriter() {
        energy = -1;
    }

    @Override
    public ByteMessage write(Player player) {
        var msg = ByteMessage.message(110);
        msg.put(energy != -1 ? energy : (int) player.getRunEnergy());
        return msg;
    }
}
