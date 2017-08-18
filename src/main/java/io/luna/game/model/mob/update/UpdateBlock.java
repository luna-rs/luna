package io.luna.game.model.mob.update;

import com.google.common.base.MoreObjects;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.update.UpdateFlagSet.UpdateFlag;
import io.luna.net.codec.ByteMessage;

import java.util.Objects;

/**
 * A model representing an update block within an update block set. Implementations <strong>must be
 * stateless</strong> so instances can be shared concurrently.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class UpdateBlock<E extends Mob> {

    /**
     * The bit mask.
     */
    private final int mask;

    /**
     * The update flag.
     */
    private final UpdateFlag flag;

    /**
     * Creates a new {@link UpdateBlock}.
     *
     * @param mask The bit mask.
     * @param flag The update flag.
     */
    public UpdateBlock(int mask, UpdateFlagSet.UpdateFlag flag) {
        this.mask = mask;
        this.flag = flag;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", flag.name()).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(flag);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof UpdateBlock) {
            UpdateBlock<?> other = (UpdateBlock<?>) obj;
            return flag == other.flag;
        }
        return false;
    }

    /**
     * Writes data from this block into the argued buffer.
     */
    public abstract void write(E mob, ByteMessage msg);

    /**
     * @return The bit mask.
     */
    public int getMask() {
        return mask;
    }

    /**
     * @return The update flag.
     */
    public UpdateFlag getFlag() {
        return flag;
    }
}
