package io.luna.game.model.mob.block;

import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Represents a single update block within a mob update cycle.
 * <p>
 * Update blocks are responsible for serializing a specific type of update onto the outgoing player update stream
 * (e.g., appearance changes, animations, hitmarks). They operate as small, atomic, stateless components that can be
 * reused across threads and across mobs.
 * </p>
 *
 * <h2>Design Requirements</h2>
 * <ul>
 *     <li><strong>Stateless:</strong> Update blocks must not store per-mob information. They are shared singletons
 *     referenced from the update block sets.</li>
 *     <li><strong>Selective Encoding:</strong> A block only encodes if its corresponding {@link UpdateFlag} is
 *     present in the mob’s {@link UpdateFlagSet}.</li>
 *     <li><strong>Dual-Path Encoding:</strong> Every block must define whether it supports players and/or NPCs.</li>
 * </ul>
 * <p>
 * The update engine combines a set of these blocks into a single bitmask, encodes their data, and pushes them to the
 * client as part of the 317 update protocol.
 * </p>
 *
 * @author lare96
 */
public abstract class UpdateBlock {

    /**
     * The flag associated with this update block. When the mob flags this type in its {@link UpdateFlagSet}, this
     * block will be encoded.
     */
    private final UpdateFlag flag;

    /**
     * Creates a new {@link UpdateBlock}.
     *
     * @param flag The update flag this block represents.
     */
    public UpdateBlock(UpdateFlag flag) {
        this.flag = flag;
    }

    /**
     * Encodes this update block for a {@link Player}.
     * Subclasses must override if they support player encoding.
     *
     * @param player The player receiving the update.
     * @param msg The outgoing buffer.
     * @throws UnsupportedOperationException If the block is not valid for players.
     */
    public void encodeForPlayer(Player player, ByteMessage msg) {
        throw new UnsupportedOperationException(flag + " not supported for Players.");
    }

    /**
     * Encodes this update block for an {@link Npc}.
     * Subclasses must override if they support NPC encoding.
     *
     * @param npc The NPC whose data is being encoded.
     * @param msg The outgoing buffer.
     * @throws UnsupportedOperationException If the block is not valid for NPCs.
     */
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        throw new UnsupportedOperationException(flag + " not supported for NPCs.");
    }

    /**
     * Returns the bitmask value representing this block when applied to a player.
     *
     * @return The player mask bit.
     * @throws UnsupportedOperationException If the block is not valid for players.
     */
    public int getPlayerMask() {
        throw new UnsupportedOperationException(flag + " not supported for Players.");
    }

    /**
     * Returns the bitmask value representing this block when applied to an NPC.
     *
     * @return The NPC mask bit.
     * @throws UnsupportedOperationException If the block is not valid for NPCs.
     */
    public int getNpcMask() {
        throw new UnsupportedOperationException(flag + " not supported for NPCs.");
    }

    /**
     * Returns the correct mask for the given mob type.
     *
     * @param mob The mob to determine mask type from.
     * @return The mask specific to player or NPC encoding.
     */
    public final int getMask(Mob mob) {
        return mob.getType() == EntityType.PLAYER ? getPlayerMask() : getNpcMask();
    }

    /**
     * Unwraps an {@link Optional}, throwing a {@link NoBlockValueException} if no value is present.
     *
     * @param optional The optional to unwrap.
     * @param <T> The contained type.
     * @return The value contained in the optional.
     */
    public final <T> T unwrap(Optional<T> optional) throws NoBlockValueException {
        return optional.orElseThrow(() -> new NoBlockValueException(flag));
    }

    /**
     * Unwraps an {@link OptionalInt}, throwing a {@link NoBlockValueException} if absent.
     *
     * @param optional The optional containing an integer.
     * @return The value.
     */
    public final int unwrap(OptionalInt optional) throws NoBlockValueException {
        return optional.orElseThrow(() -> new NoBlockValueException(flag));
    }

    /**
     * Returns the {@link UpdateFlag} associated with this block.
     *
     * @return The flag.
     */
    public final UpdateFlag getFlag() {
        return flag;
    }
}
