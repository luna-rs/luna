package io.luna.game.model.mobile.attr;

/**
 * An {@link RuntimeException} implementation thrown when there is a type mismatch with attributes, resulting in a {@link
 * ClassCastException}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class AttributeTypeException extends RuntimeException {

    /**
     * Creates a new {@link AttributeTypeException}.
     *
     * @param alias The {@link AttributeKey} alias which the exception is being thrown for.
     */
    public AttributeTypeException(AttributeKey<?> alias) {
        super("invalid attribute{" + alias.getName() + "} type! expected{" + alias.getTypeName() + "}");
    }
}
