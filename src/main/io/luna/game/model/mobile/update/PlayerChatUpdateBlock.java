package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Chat;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;

/**
 * An {@link PlayerUpdateBlock} implementation that handles the {@link Chat} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerChatUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerChatUpdateBlock}.
     */
    public PlayerChatUpdateBlock() {
        super(0x80, UpdateFlag.CHAT);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        msg.putShort(((mob.getChat().getColor() & 0xff) << 8) + (mob.getChat().getEffects() & 0xff), ByteOrder.LITTLE);
        msg.put(mob.getRights().getOpcode());
        msg.put(mob.getChat().getMessage().length, ByteTransform.C);
        msg.putBytesReverse(mob.getChat().getMessage());
    }
}
