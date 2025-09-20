package io.luna.game.model.mob.attr;

/**
 * Represents a type that can provide access to an {@link AttributeMap}; or in other words, a type that values can be
 * attributed to.
 *
 * @author lare96
 */
public interface Attributable {

    /**
     * @return The values being attributed to this type.
     */
    AttributeMap attributes();
}
