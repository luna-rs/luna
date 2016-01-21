package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.msg.out.SendPlayerUpdateMessage;

/**
 * An {@link UpdateBlock} implementation specific to {@link Player}s contained within an {@link UpdateBlockSet} and sent
 * within a {@link SendPlayerUpdateMessage}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class PlayerUpdateBlock extends UpdateBlock<Player> {

    /**
     * Creates a new {@link PlayerUpdateBlock}.
     *
     * @param mask The bit mask for this update block.
     * @param flag The update flag associated with this update block.
     */
    public PlayerUpdateBlock(int mask, UpdateFlag flag) {
        super(mask, flag);
    }
}
