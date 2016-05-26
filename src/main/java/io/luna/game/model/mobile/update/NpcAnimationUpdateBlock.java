package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code ANIMATION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcAnimationUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcAnimationUpdateBlock}.
     */
    public NpcAnimationUpdateBlock() {
        super(0x10, UpdateFlag.ANIMATION);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        msg.putShort(mob.getAnimation().getId(), ByteOrder.LITTLE);
        msg.put(mob.getAnimation().getDelay());
    }
}
