package io.luna.game.model.mob.attr;

/**
 * A {@link RuntimeException} implementation thrown when there is an attribute type mismatch.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeTypeException extends RuntimeException {

    /**
     * Creates a new {@link AttributeTypeException}.
     *
     * @param alias The alias.
     */
    public AttributeTypeException(AttributeKey<?> alias) {
        super("invalid attribute{" + alias.getName() + "} type! expected{" + alias.getTypeName() + "}");
    }
}
