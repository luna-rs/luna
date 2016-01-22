package io.luna.game.model.mobile.update;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.codec.ByteMessage;

/**
 * An {@link UpdateBlockSet} implementation containing relevant functions for handling {@code Player} update blocks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PlayerUpdateBlockSet extends UpdateBlockSet<Player> {

    /**
     * The current {@code UpdatePhase} of this {@code PlayerUpdateBlockSet}.
     */
    private UpdatePhase updatePhase = UpdatePhase.UPDATE_SELF;

    /**
     * If the {@code APPEARANCE} block should be forced.
     */
    private boolean forceAppearance;

    /**
     * If the {@code CHAT} block should be ignored.
     */
    private boolean ignoreChat;

    @Override
    protected boolean needsEncodeBlocks(Player forMob, Player player, ByteMessage msg) {
        forceAppearance = (updatePhase == UpdatePhase.ADD_LOCAL);
        ignoreChat = (updatePhase == UpdatePhase.UPDATE_SELF);

        if (forMob.getUpdateFlags().isEmpty() && !forceAppearance) {
            return false;
        }
        if (forMob.getCachedBlock() != null && canCacheBlocks()) {
            msg.putBytes(forMob.getCachedBlock());
            return false;
        }
        return true;
    }

    @Override
    protected boolean canEncodeBlock(Player forMob, UpdateBlock<Player> block) {
        if (block.getFlag() == UpdateFlag.APPEARANCE && forceAppearance) {
            addBlock(block);
            return false;
        } else if (block.getFlag() == UpdateFlag.CHAT && ignoreChat) {
            return false;
        }
        return true;
    }

    @Override
    protected void onEncodeFinish(Player forMob, Player player, ByteMessage encodedBlock) {
        if (canCacheBlocks()) {
            player.setCachedBlock(encodedBlock.getBuffer());
        }

        if (updatePhase == UpdatePhase.UPDATE_SELF) {
            updatePhase = UpdatePhase.UPDATE_LOCAL;
        } else if (updatePhase == UpdatePhase.UPDATE_LOCAL) {
            updatePhase = UpdatePhase.ADD_LOCAL;
        }
    }

    /**
     * @return {@code true} if the {@link UpdateBlock}s can be cached, {@code false} otherwise.
     */
    private boolean canCacheBlocks() {
        return !forceAppearance && !ignoreChat;
    }

    /**
     * An enumerated type whose elements represent the update phases of player updating.
     *
     * @author lare96 <http://github.org/lare96>
     */
    private enum UpdatePhase {

        /**
         * A {@link Player} is updating for themself.
         */
        UPDATE_SELF,

        /**
         * A {@link Player} is updating for the people around them.
         */
        UPDATE_LOCAL,

        /**
         * A {@link Player} is adding new people that have appeared around them.
         */
        ADD_LOCAL
    }
}
