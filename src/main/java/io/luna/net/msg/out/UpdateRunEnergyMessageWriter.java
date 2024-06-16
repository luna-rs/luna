package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

import java.util.OptionalInt;

/**
 * A {@link GameMessageWriter} implementation that displays the run energy value.
 *
 * @author lare96
 */
public final class UpdateRunEnergyMessageWriter extends GameMessageWriter {

    /**
     * The run energy.
     */
    private final OptionalInt energy;

    /**
     * Creates a new {@link UpdateRunEnergyMessageWriter}.
     *
     * @param energy The run energy.
     */
    public UpdateRunEnergyMessageWriter(int energy) {
        this.energy = OptionalInt.of(energy);
    }

    /**
     * Creates a new {@link UpdateRunEnergyMessageWriter} where the energy is updated to the user's current amount.
     */
    public UpdateRunEnergyMessageWriter() {
        energy = OptionalInt.empty();
    }

    @Override
    public ByteMessage write(Player player) {
        int runEnergy = energy.orElse((int) player.getRunEnergy());
        ByteMessage msg = ByteMessage.message(125);
        msg.put(runEnergy);
        return msg;
    }
}
