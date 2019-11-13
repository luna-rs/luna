package io.luna.game.model.item.shop;

import com.google.common.collect.ImmutableSet;

import java.util.Arrays;

/**
 * The enumerated type whose elements represent currencies that can be used to purchase items
 * from shops.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum Currency {
    COINS(995),
    TOKKUL(6529),
    CASTLE_WARS_TICKETS(4067),
    AGILITY_ARENA_TICKETS(2996);

    /**
     * An immutable set of currency identifiers.
     */
    public static final ImmutableSet<Integer> IDENTIFIERS = Arrays.stream(values()).map(currency -> currency.id)
            .collect(ImmutableSet.toImmutableSet());

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The singular name.
     */
    private final String singularName;

    /**
     * The plural name.
     */
    private final String pluralName;

    /**
     * Creates a new {@link Currency}.
     *
     * @param id The item identifier.
     */
    Currency(int id) {
        this.id = id;
        pluralName = name().toLowerCase().replace("_", " ");
        singularName = computeSingularName();
    }

    /**
     * Returns either the singular or plural form based on the value.
     *
     * @param value The value.
     * @return The currency name.
     */
    public String computeName(int value) {
        return value > 1 ? pluralName : singularName;
    }

    /**
     * Computes the singular name from the plural name.
     *
     * @return The computed singular name.
     */
    String computeSingularName() {
        int lastIndex = pluralName.length() - 1;
        if (pluralName.charAt(lastIndex) == 's') {
            return pluralName.substring(0, lastIndex);
        }
        return pluralName;
    }

    /**
     * @return The item identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The singular name.
     */
    public String getSingularName() {
        return singularName;
    }

    /**
     * @return The plural name.
     */
    public String getPluralName() {
        return pluralName;
    }
}
