package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Graphic;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the {@link Graphic} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class PlayerGraphicUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerGraphicUpdateBlock}.
     */
    public PlayerGraphicUpdateBlock() {
        super(0x100, UpdateFlag.GRAPHIC);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        msg.putShort(mob.getGraphic().getId(), ByteOrder.LITTLE);
        msg.putInt(mob.getGraphic().getHeight() << 16 | mob.getGraphic().getDelay() & 0xFFFF);
    }
}
