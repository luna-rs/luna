package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Npc;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link UpdateBlockSet} implementation that handles the encoding of {@link Npc} update
 * blocks.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class NpcUpdateBlockSet extends UpdateBlockSet<Npc> {

    /**
     * An immutable list of update blocks.
     */
    private static final ImmutableList<UpdateBlock> UPDATE_BLOCKS = ImmutableList.of(
            new AnimationUpdateBlock(),
            new SecondaryHitUpdateBlock(),
            new GraphicUpdateBlock(),
            new InteractionUpdateBlock(),
            new ForcedChatUpdateBlock(),
            new PrimaryHitUpdateBlock(),
            new TransformUpdateBlock(),
            new FacePositionUpdateBlock());

    /**
     * Creates a new {@link NpcUpdateBlockSet}.
     */
    public NpcUpdateBlockSet() {
        super(UPDATE_BLOCKS);
    }

    @Override
    public void addBlockSet(Npc npc, ByteMessage msg, UpdateState state) {
        ByteMessage blockMsg = ByteMessage.message();
        try {
            // Encode the block set and add it to the main update buffer.
            encodeBlockSet(npc, blockMsg, state);
            msg.putBytes(blockMsg);
        } finally {
            blockMsg.release();
        }
    }

    @Override
    public void encodeBlock(Npc npc, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForNpc(npc, blockMsg);
    }
}