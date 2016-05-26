package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link NpcUpdateBlock} implementation that handles the {@code FORCE_CHAT} update block.
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
        msg.putString(mob.getForceChat());
    }
}