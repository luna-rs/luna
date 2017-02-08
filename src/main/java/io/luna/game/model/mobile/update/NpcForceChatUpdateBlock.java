package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link NpcUpdateBlock} implementation for the {@code FORCE_CHAT} update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcForceChatUpdateBlock extends NpcUpdateBlock {

    /**
     * Creates a new {@link NpcForceChatUpdateBlock}.
     */
    public NpcForceChatUpdateBlock() {
        super(1, UpdateFlag.FORCE_CHAT);
    }

    @Override
    public void write(Npc mob, ByteMessage msg) {
        String forceChat = mob.getForceChat().get();
        msg.putString(forceChat);
    }
}