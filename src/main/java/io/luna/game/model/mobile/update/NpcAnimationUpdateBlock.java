package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Animation;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code ANIMATION} update block.
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
        Animation animation = mob.getAnimation().get();
        msg.putShort(animation.getId(), ByteOrder.LITTLE);
        msg.put(animation.getDelay());
    }
}
