package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;

import static io.luna.game.model.mob.block.UpdateState.UPDATE_LOCAL;

/**
 * An {@link AbstractUpdateBlockSet} implementation that handles the encoding of {@link Player} update
 * blocks.
 *
 * @author lare96
 */
public class PlayerUpdateBlockSet extends AbstractUpdateBlockSet<Player> {

    @Override
    public void addBlockSet(Player player, ByteMessage msg, UpdateState state) {
        // If we can use a cached update block and the player has one, do it here.
        boolean isCachingBlocks = state == UPDATE_LOCAL;
        if (isCachingBlocks && player.hasCachedBlock()) {
            msg.putBytes(player.getCachedBlock());
            return;
        }

        // Otherwise, encode and cache a new set of update blocks.
        ByteMessage blockMsg = ByteMessage.raw();
        try {
            encodeBlockSet(player, blockMsg, state);
            msg.putBytes(blockMsg);

            // TODO We can probably cache update blocks in any phase. More research needed
            if (isCachingBlocks) {
                player.setCachedBlock(blockMsg);
            }
        } finally {
            // TODO Resource leak here? Could have something to do with caching the blocks?
            blockMsg.release();
        }
    }

    @Override
    public void encodeBlock(Player player, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForPlayer(player, blockMsg);
    }

    @Override
    public ImmutableList<UpdateBlock> computeBlocks() {
        return ImmutableList.of(
                new AnimationUpdateBlock(),
                new ForcedChatUpdateBlock(),
                new ExactMovementUpdateBlock(),
                new InteractionUpdateBlock(),
                new FacePositionUpdateBlock(),
                new GraphicUpdateBlock(),
                new AppearanceUpdateBlock(),
                new SecondaryHitUpdateBlock(),
                new ChatUpdateBlock(),
                new PrimaryHitUpdateBlock()
        );
    }
}
