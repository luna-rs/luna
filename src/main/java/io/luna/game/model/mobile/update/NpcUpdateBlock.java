package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.msg.out.NpcUpdateMessageWriter;

/**
 * An {@link UpdateBlock} implementation specific to {@link Npc}s contained within an {@link UpdateBlockSet} and sent within
 * a {@link NpcUpdateMessageWriter}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class NpcUpdateBlock extends UpdateBlock<Npc> {

    /**
     * Creates a new {@link NpcUpdateBlock}.
     *
     * @param mask The bit mask for this update block.
     * @param flag The update flag associated with this update block.
     */
    public NpcUpdateBlock(int mask, UpdateFlag flag) {
        super(mask, flag);
    }
}
