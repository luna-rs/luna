package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Graphic;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code GRAPHIC} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerGraphicUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerGraphicUpdateBlock}.
     */
    public PlayerGraphicUpdateBlock() {
        super(0x100, UpdateFlag.GRAPHIC);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        Graphic graphic = mob.getGraphic().get();
        msg.putShort(graphic.getId(), ByteOrder.LITTLE);
        msg.putInt(graphic.getHeight() << 16 | graphic.getDelay() & 0xFFFF);
    }
}
