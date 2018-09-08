package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.NoSuchElementException;

/**
 * A {@link NoSuchElementException} implementation that indicates a value used to build an update block
 * does not exist.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class NoBlockValueException extends NoSuchElementException {

    /**
     * The update block where the value is missing.
     */
    private final UpdateFlag flag;

    /**
     * Creates a new {@link NoBlockValueException}.
     *
     * @param flag The update block where the value is missing.
     */
    public NoBlockValueException(UpdateFlag flag) {
        super("Missing value for update block " + flag);
        this.flag = flag;
    }

    /**
     * @return The update block where the value is missing.
     */
    public UpdateFlag getFlag() {
        return flag;
    }
}