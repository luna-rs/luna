package io.luna.game.model.mob.update;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;

/**
 * A model representing a player update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class PlayerUpdateBlock extends UpdateBlock<Player> {

    /**
     * Creates a new {@link PlayerUpdateBlock}.
     *
     * @param mask The bit mask.
     * @param flag The update flag.
     */
    public PlayerUpdateBlock(int mask, UpdateFlag flag) {
        super(mask, flag);
    }
}
