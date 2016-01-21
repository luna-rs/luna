package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Animation;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the {@link Animation} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerAnimationUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerAnimationUpdateBlock}.
     */
    public PlayerAnimationUpdateBlock() {
        super(8, UpdateFlag.ANIMATION);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        msg.putShort(mob.getAnimation().getId(), ByteOrder.LITTLE);
        msg.put(mob.getAnimation().getDelay(), ByteTransform.C);
    }
}
