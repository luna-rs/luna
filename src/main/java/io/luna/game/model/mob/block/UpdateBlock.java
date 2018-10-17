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
 * A model representing an update block within an update block set. Implementations <strong>must be
 * stateless</strong> so instances can be shared concurrently.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class UpdateBlock {

    /**
     * The update flag.
     */
    private final UpdateFlag flag;

    /**
     * Creates a new {@link UpdateBlock}.
     *
     * @param flag The update flag.
     */
    public UpdateBlock(UpdateFlag flag) {
        this.flag = flag;
    }

    /**
     * Encodes this update block for {@code player}. The default behaviour is to throw an exception.
     *
     * @param player The player to encode for.
     * @param msg The buffer.
     */
    public void encodeForPlayer(Player player, ByteMessage msg) {
        throw new UnsupportedOperationException(flag + " not supported for Players.");
    }

    /**
     * Encodes this update block for {@code npc}. The default behaviour is to throw an exception.
     *
     * @param npc The NPC to encode for.
     * @param msg The buffer.
     */
    public void encodeForNpc(Npc npc, ByteMessage msg) {
        throw new UnsupportedOperationException(flag + " not supported for NPCs.");
    }

    /**
     * Retrieves the player update mask for this block. Throws an exception by default.
     *
     * @return The update mask.
     */
    public int getPlayerMask() {
        throw new UnsupportedOperationException(flag + " not supported for Players.");
    }

    /**
     * Retrieves the NPC update mask for this block. Throws an exception by default.
     *
     * @return The update mask.
     */
    public int getNpcMask() {
        throw new UnsupportedOperationException(flag + " not supported for NPCs.");
    }

    /**
     * Gets the correct update mask for {@code mob}.
     *
     * @param mob The mob to get the mask for.
     * @return The update mask.
     */
    public final int getMask(Mob mob) {
        return mob.getType() == EntityType.PLAYER ? getPlayerMask() : getNpcMask();
    }

    /**
     * Unwraps an optional value, throwing an {@link NoBlockValueException} if the value doesn't
     * exist.
     *
     * @param optional The optional to unwrap.
     * @param <T> The value type.
     * @return The unwrapped value.
     * @throws NoBlockValueException If the value doesn't exist.
     */
    public final <T> T unwrap(Optional<T> optional) throws NoBlockValueException {
        return optional.orElseThrow(() -> new NoBlockValueException(flag));
    }

    /**
     * Unwraps an optional {@code int} value, throwing an {@link NoBlockValueException} if the
     * value doesn't exist.
     *
     * @param optional The optional to unwrap.
     * @return The unwrapped value.
     * @throws NoBlockValueException If the value doesn't exist.
     */
    public final int unwrap(OptionalInt optional) throws NoBlockValueException {
        return optional.orElseThrow(() -> new NoBlockValueException(flag));
    }

    /**
     * @return The update flag.
     */
    public final UpdateFlag getFlag() {
        return flag;
    }
}
