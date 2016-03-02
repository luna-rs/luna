package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the {@code INTERACTION} update block.
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
        msg.putShort(mob.getInteractionIndex(), ByteOrder.LITTLE);
    }
}
