package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * A {@link PlayerUpdateBlock} implementation for the {@code FORCE_CHAT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerForceChatUpdateBlock extends PlayerUpdateBlock {

    /**
     * Creates a new {@link PlayerForceChatUpdateBlock}.
     */
    public PlayerForceChatUpdateBlock() {
        super(4, UpdateFlag.FORCE_CHAT);
    }

    @Override
    public void write(Player mob, ByteMessage msg) {
        String forceChat = mob.getForceChat().get();
        msg.putString(forceChat);
    }
}
