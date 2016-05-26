package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code INTERACTION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcInteractionUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcInteractionUpdateBlock}.
     */
    public NpcInteractionUpdateBlock() {
        super(0x20, UpdateFlag.INTERACTION);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        msg.putShort(mob.getInteractionIndex());
    }
}
