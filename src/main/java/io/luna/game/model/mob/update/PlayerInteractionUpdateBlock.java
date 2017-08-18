package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code INTERACTION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerInteractionUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerInteractionUpdateBlock}.
     */
    public PlayerInteractionUpdateBlock() {
        super(1, UpdateFlag.INTERACTION);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        int index = mob.getInteractionIndex().getAsInt();
        msg.putShort(index, ByteOrder.LITTLE);
    }
}
