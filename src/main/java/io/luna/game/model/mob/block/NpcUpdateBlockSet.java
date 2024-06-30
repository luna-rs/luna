package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Npc;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link AbstractUpdateBlockSet} implementation that handles the encoding of {@link Npc} update
 * blocks.
 *
 * @author lare96
 */
public class NpcUpdateBlockSet extends AbstractUpdateBlockSet<Npc> {

    @Override
    public void addBlockSet(Npc npc, ByteMessage msg, UpdateState state) {
        encodeBlockSet(npc, msg, state);
    }

    @Override
    public void encodeBlock(Npc npc, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForNpc(npc, blockMsg);
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
