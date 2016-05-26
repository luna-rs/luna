package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code GRAPHIC} update block.
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
        msg.putShort(mob.getGraphic().getId());
        msg.putInt(mob.getGraphic().getHeight() << 16 | mob.getGraphic().getDelay() & 0xFFFF);
    }
}