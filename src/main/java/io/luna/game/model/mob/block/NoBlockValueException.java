package io.luna.game.model.mob.block;

import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.NoSuchElementException;

/**
 * Thrown when a required value is missing while encoding an {@link UpdateBlock}.
 * <p>
 * Many update blocks store optional values internally before encoding. If an update block attempts to encode but
 * lacks the required information (e.g., missing damage, movement data, or appearance), this exception is thrown to
 * signal a misuse of the update system.
 * </p>
 *
 * <p>
 * This exception identifies which {@link UpdateFlag} caused the problem, helping developers debug update
 * pipeline issues.
 * </p>
 *
 * @author lare96
 */
public final class NoBlockValueException extends NoSuchElementException {

    /**
     * The update flag whose value was missing.
     */
    private final UpdateFlag flag;

    /**
     * Creates a new exception.
     *
     * @param flag The update flag missing required data.
     */
    public NoBlockValueException(UpdateFlag flag) {
        super("Missing value for update block " + flag);
        this.flag = flag;
    }

    /**
     * @return The flag whose data was missing.
     */
    public UpdateFlag getFlag() {
        return flag;
    }
}
