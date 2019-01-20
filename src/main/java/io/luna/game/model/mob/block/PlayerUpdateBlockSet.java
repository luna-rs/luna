package io.luna.game.model.mob.block;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;

import java.util.List;

import static io.luna.game.model.mob.block.UpdateState.UPDATE_LOCAL;

/**
 * An {@link UpdateBlockSet} implementation that handles the encoding of {@link Player} update
 * blocks.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class PlayerUpdateBlockSet extends UpdateBlockSet<Player> {

    /**
     * An immutable list of update blocks.
     */
    private static final List<UpdateBlock> UPDATE_BLOCKS = List.of(
        new GraphicUpdateBlock(),
        new AnimationUpdateBlock(),
        new ForcedChatUpdateBlock(),
        new ChatUpdateBlock(),
        new ForcedMovementUpdateBlock(),
        new InteractionUpdateBlock(),
        new AppearanceUpdateBlock(),
        new FacePositionUpdateBlock(),
        new PrimaryHitUpdateBlock(),
        new SecondaryHitUpdateBlock()
    );

    /**
     * Creates a new {@link PlayerUpdateBlockSet}.
     */
    public PlayerUpdateBlockSet() {
        super(UPDATE_BLOCKS);
    }

    @Override
    public void addBlockSet(Player player, ByteMessage msg, UpdateState state) {
        // If we can use a cached update block and the player has one, do it here.
        boolean isCachingBlocks = state == UPDATE_LOCAL;
        
        if (isCachingBlocks && player.hasCachedBlock()) {
            msg.putBytes(player.getCachedBlock());
            return;
        }

        // Otherwise, encode and cache a new set of update blocks.
        var blockMsg = ByteMessage.raw();
        
        try {
            encodeBlockSet(player, blockMsg, state);
            msg.putBytes(blockMsg);

            // TODO We can probably cache update blocks in any phase.
            if (isCachingBlocks) {
                player.setCachedBlock(blockMsg);
            }
        } finally {
            blockMsg.release();
        }
    }

    @Override
    public void encodeBlock(Player player, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForPlayer(player, blockMsg);
    }
}