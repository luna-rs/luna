package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagSet.UpdateFlag;

/**
 * A model representing an NPC update block.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class NpcUpdateBlock extends UpdateBlock<Npc> {

    /**
     * Creates a new {@link NpcUpdateBlock}.
     *
     * @param mask The bit mask.
     * @param flag The update flag.
     */
    public NpcUpdateBlock(int mask, UpdateFlag flag) {
        super(mask, flag);
    }
}
