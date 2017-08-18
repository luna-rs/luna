package io.luna.game.model.mob.update;

import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a group of update blocks. This model <strong>must remain stateless</strong> so instances
 * can be shared concurrently.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class UpdateBlockSet<E extends Mob> {

    /**
     * A global instance of the player update block set.
     */
    public static final UpdateBlockSet<Player> PLAYER_BLOCK_SET = new UpdateBlockSet<>();

    /**
     * A global instance of the NPC update block set.
     */
    public static final UpdateBlockSet<Npc> NPC_BLOCK_SET = new UpdateBlockSet<>();

    static { /* Initialize the player and NPC update block sets. */
        PLAYER_BLOCK_SET.add(new PlayerGraphicUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerAnimationUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerForceChatUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerChatUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerForceMovementUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerInteractionUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerAppearanceUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerFacePositionUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerPrimaryHitUpdateBlock());
        PLAYER_BLOCK_SET.add(new PlayerSecondaryHitUpdateBlock());

        NPC_BLOCK_SET.add(new NpcAnimationUpdateBlock());
        NPC_BLOCK_SET.add(new NpcSecondaryHitUpdateBlock());
        NPC_BLOCK_SET.add(new NpcGraphicUpdateBlock());
        NPC_BLOCK_SET.add(new NpcInteractionUpdateBlock());
        NPC_BLOCK_SET.add(new NpcForceChatUpdateBlock());
        NPC_BLOCK_SET.add(new NpcPrimaryHitUpdateBlock());
        NPC_BLOCK_SET.add(new NpcTransformUpdateBlock());
        NPC_BLOCK_SET.add(new NpcFacePositionUpdateBlock());
    }

    /**
     * An ordered set containing update blocks.
     */
    private final Set<UpdateBlock<E>> updateBlocks = new LinkedHashSet<>();

    /**
     * Adds an update block to this set.
     */
    private void add(UpdateBlock<E> block) {
        checkState(updateBlocks.add(block), "updateBlocks.contains(block)");
    }

    /**
     * Encodes update blocks for a player or NPC.
     */
    public void encodeUpdateBlocks(E forMob, ByteMessage msg, UpdateState state) {
        if (forMob.getUpdateFlags().isEmpty() && state != UpdateState.ADD_LOCAL) {
            return;
        }

        if (forMob.getType() == EntityType.PLAYER) {
            encodePlayerBlocks(forMob, msg, state);
        } else if (forMob.getType() == EntityType.NPC) {
            encodeNpcBlocks(forMob, msg, state);
        } else {
            throw new IllegalStateException("forMob.getType() must be PLAYER or NPC");
        }
    }

    /**
     * Encodes update blocks for a player, specifically.
     */
    private void encodePlayerBlocks(E forMob, ByteMessage msg, UpdateState state) {
        Player player = (Player) forMob;
        boolean cacheBlocks = (state != UpdateState.ADD_LOCAL && state != UpdateState.UPDATE_SELF);

        if (player.getCachedBlock() != null && cacheBlocks) {
            msg.putBytes(player.getCachedBlock());
            return;
        }

        ByteMessage encodedBlocks = encodeBlocks(forMob, state);
        msg.putBytes(encodedBlocks);
        if (cacheBlocks) {
            player.setCachedBlock(encodedBlocks);
        }
        encodedBlocks.release();
    }

    /**
     * Encodes update blocks specifically for an NPC, specifically.
     */
    private void encodeNpcBlocks(E forMob, ByteMessage msg, UpdateState state) {
        ByteMessage encodedBlocks = encodeBlocks(forMob, state);
        msg.putBytes(encodedBlocks);

        encodedBlocks.release();
    }

    /**
     * Encodes update blocks and returns the buffer containing the data.
     */
    private ByteMessage encodeBlocks(E forMob, UpdateState state) {
        ByteMessage encodedBlock = ByteMessage.message();

        int mask = 0;
        Set<UpdateBlock<E>> writeBlocks = new LinkedHashSet<>();

        for (UpdateBlock<E> updateBlock : updateBlocks) {
            if (state == UpdateState.ADD_LOCAL && updateBlock.getFlag() == UpdateFlag.APPEARANCE) {
                mask |= updateBlock.getMask();
                writeBlocks.add(updateBlock);
                continue;
            }
            if (state == UpdateState.UPDATE_SELF && updateBlock.getFlag() == UpdateFlag.CHAT) {
                continue;
            }
            if (!forMob.getUpdateFlags().get(updateBlock.getFlag())) {
                continue;
            }

            mask |= updateBlock.getMask();
            writeBlocks.add(updateBlock);
        }

        if (mask >= 0x100) {
            mask |= 0x40;
            encodedBlock.putShort(mask, ByteOrder.LITTLE);
        } else {
            encodedBlock.put(mask);
        }

        writeBlocks.forEach(it -> it.write(forMob, encodedBlock));
        return encodedBlock;
    }
}
