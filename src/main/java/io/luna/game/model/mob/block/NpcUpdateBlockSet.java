package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Npc;
import io.luna.net.codec.ByteMessage;

import java.util.List;

/**
 * An {@link UpdateBlockSet} implementation that handles the encoding of {@link Npc} update
 * blocks.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class NpcUpdateBlockSet extends UpdateBlockSet<Npc> {

    /**
     * An unmodifiable list of update blocks.
     */
    private static final List<UpdateBlock> UPDATE_BLOCKS = List.of(
        new AnimationUpdateBlock(),
        new SecondaryHitUpdateBlock(),
        new GraphicUpdateBlock(),
        new InteractionUpdateBlock(),
        new ForcedChatUpdateBlock(),
        new PrimaryHitUpdateBlock(),
        new TransformUpdateBlock(),
        new FacePositionUpdateBlock()
    );

    /**
     * Creates a new {@link NpcUpdateBlockSet}.
     */
    public NpcUpdateBlockSet() {
        super(UPDATE_BLOCKS);
    }

    @Override
    public void addBlockSet(Npc npc, ByteMessage msg, UpdateState state) {
        encodeBlockSet(npc, msg, state);
    }

    @Override
    public void encodeBlock(Npc npc, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForNpc(npc, blockMsg);
    }
}