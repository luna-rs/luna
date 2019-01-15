package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Animation;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;

/**
 * An {@link UpdateBlock} implementation for the {@code ANIMATION} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AnimationUpdateBlock extends UpdateBlock {

    /**
     * Creates a new {@link AnimationUpdateBlock}.
     */
    public AnimationUpdateBlock() {
        super(UpdateFlag.ANIMATION);
    }

    @Override
    public void encodeForPlayer(Player player, ByteMessage msg) {
        Animation animation = unwrap(player.getAnimation());
        msg.putShort(animation.getId(), ByteOrder.LITTLE);
        msg.put(animation.getDelay(), ValueType.NEGATE);
    }

    @Override
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        Animation animation = unwrap(npc.getAnimation());
        msg.putShort(animation.getId(), ByteOrder.LITTLE);
        msg.put(animation.getDelay());
    }

    @Override
    public int getPlayerMask() {
        return 8;
    }

    @Override
    public int getNpcMask() {
        return 16;
    }
}
