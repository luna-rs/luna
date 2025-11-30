
package io.luna.game.model.mob.block;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;

/**
 * Handles encoding of all {@link UpdateBlock} types relevant to players.
 *
 * <p>
 * The player update pipeline supports block caching to avoid repeatedly encoding update masks
 * when possible. This class:
 * </p>
 *
 * <ul>
 *     <li>Determines if cached blocks may be reused.</li>
 *     <li>Builds block sets when required.</li>
 *     <li>Defines the list and encoding order of all player-specific update blocks.</li>
 * </ul>
 *
 * <p>
 * Player update blocks include appearance, chat, forced movement, hitsplats, graphics,
 * and other visible effects.
 * </p>
 *
 * @author lare96
 */
public final class PlayerUpdateBlockSet extends AbstractUpdateBlockSet<Player> {

    @Override
    public void addBlockSet(Player player, ByteMessage msg, UpdateState state) {
        // The block has already been encoded for someone this cycle; reuse it.
        if (state == UpdateState.UPDATE_LOCAL && player.hasCachedBlock()) {
            msg.putBytes(player.getCachedBlock());
            return;
        }

        ByteMessage blockMsg = ByteMessage.raw();
        try {
            encodeBlockSet(player, blockMsg, state);
            msg.putBytes(blockMsg);

            // Cache the encoded block for someone else to reuse within this cycle.
            if(state == UpdateState.UPDATE_LOCAL) {
                player.setCachedBlock(blockMsg);
            }
        } finally {
            // Release the temporary buffer now that the cached copy has been made.
            blockMsg.release();
        }
    }

    @Override
    public void encodeBlock(Player player, UpdateBlock block, ByteMessage blockMsg) {
        block.encodeForPlayer(player, blockMsg, player.getBlockData());
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
