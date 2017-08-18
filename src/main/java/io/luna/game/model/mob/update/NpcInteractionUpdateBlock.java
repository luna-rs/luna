package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code INTERACTION} update block.
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
        int index = mob.getInteractionIndex().getAsInt();
        msg.putShort(index);
    }
}
