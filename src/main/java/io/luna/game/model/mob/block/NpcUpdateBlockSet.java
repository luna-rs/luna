package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.net.codec.ByteMessage;

/**
 * Handles encoding of all {@link UpdateBlock} types relevant to NPCs.
 * <p>
 * NPC update sets differ from player update sets in both order and available block types.
 * This class defines:
 * </p>
 *
 * <ul>
 *     <li>Which update blocks apply to NPCs.</li>
 *     <li>The order they must be encoded in.</li>
 *     <li>How each block is encoded for the NPC update mask.</li>
 * </ul>
 *
 * <p>
 * The update pipeline calls {@link AbstractUpdateBlockSet#addBlockSet(Mob, ByteMessage, UpdateState)} during NPC
 * synchronization. NPCs never cache block sets (unlike players).
 * </p>
 *
 * @author lare96
 */
public final class NpcUpdateBlockSet extends AbstractUpdateBlockSet<Npc> {

    @Override
    public void addBlockSet(Npc npc, ByteMessage msg, UpdateState state) {
        if (npc.hasCachedBlock()) {
            msg.putBytes(npc.getCachedBlock());
            return;
        }

        ByteMessage blockMsg = ByteMessage.raw();
        try {
            encodeBlockSet(npc, blockMsg, state);
            msg.putBytes(blockMsg);
            npc.setCachedBlock(blockMsg);
        } finally {
            // Release the temporary buffer now that the cached copy has been made.
            blockMsg.release();
        }
    }

    @Override
    public void encodeBlock(Npc npc, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForNpc(blockMsg, npc.getBlockData());
    }

    @Override
    public ImmutableList<UpdateBlock> computeBlocks() {
        return ImmutableList.of(
                new TransformUpdateBlock(),
                new InteractionUpdateBlock(),
                new PrimaryHitUpdateBlock(),
                new GraphicUpdateBlock(),
                new ForcedChatUpdateBlock(),
                new FacePositionUpdateBlock(),
                new AnimationUpdateBlock(),
                new SecondaryHitUpdateBlock()
        );
    }
}
