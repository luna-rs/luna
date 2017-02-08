package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Graphic;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code GRAPHIC} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcGraphicUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcGraphicUpdateBlock}.
     */
    public NpcGraphicUpdateBlock() {
        super(0x80, UpdateFlag.GRAPHIC);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        Graphic graphic = mob.getGraphic().get();
        msg.putShort(graphic.getId());
        msg.putInt(graphic.getHeight() << 16 | graphic.getDelay() & 0xFFFF);
    }
}