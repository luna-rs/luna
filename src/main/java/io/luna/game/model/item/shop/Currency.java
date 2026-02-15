package io.luna.game.model.item.shop;

import com.google.common.collect.ImmutableSet;

import java.util.Arrays;

/**
 * An enum representing currencies that may be used to purchase items from a {@link Shop}.
 * <p>
 * Each currency maps to an item id. The enum also provides singular/plural names for human-readable
 * messaging (e.g. "coin" vs "coins").
 *
 * @author lare96
 */
public enum Currency {

    /**
     * Standard coins currency.
     */
    COINS(995),

    /**
     * Tokkul currency.
     */
    TOKKUL(6529),

    /**
     * Castle Wars ticket currency.
     */
    CASTLE_WARS_TICKETS(4067),

    /**
     * Agility Arena ticket currency.
     */
    AGILITY_ARENA_TICKETS(2996);

    /**
     * An immutable set of all currency item identifiers.
     * <p>
     * This is commonly used to prevent shops from accepting currency items as sellable goods.
     */
    public static final ImmutableSet<Integer> IDENTIFIERS = Arrays.stream(values())
            .map(currency -> currency.id)
            .collect(ImmutableSet.toImmutableSet());

    /**
     * The item id that represents this currency.
     */
    private final int id;

    /**
     * Human-readable singular form of the currency name.
     */
    private final String singularName;

    /**
     * Human-readable plural form of the currency name.
     */
    private final String pluralName;

    /**
     * Creates a new {@link Currency} enum constant.
     * <p>
     * Names are derived from the enum constant name and converted to lowercase with underscores replaced by spaces.
     *
     * @param id The item id that represents this currency.
     */
    Currency(int id) {
        this.id = id;
        pluralName = name().toLowerCase().replace("_", " ");
        singularName = computeSingularName();
    }

    /**
     * Returns either the singular or plural name depending on {@code value}.
     *
     * @param value The amount being referenced.
     * @return The singular name if {@code value <= 1}, otherwise the plural name.
     */
    public String computeName(int value) {
        return value > 1 ? pluralName : singularName;
    }

    /**
     * Computes a singular form of {@link #pluralName}.
     * <p>
     * If the plural name ends with {@code 's'}, it is trimmed; otherwise it is returned unchanged.
     *
     * @return The computed singular currency name.
     */
    String computeSingularName() {
        int lastIndex = pluralName.length() - 1;
        if (pluralName.charAt(lastIndex) == 's') {
            return pluralName.substring(0, lastIndex);
        }
        return pluralName;
    }

    /**
     * @return The item id for this currency.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The singular currency name.
     */
    public String getSingularName() {
        return singularName;
    }

    /**
     * @return The plural currency name.
     */
    public String getPluralName() {
        return pluralName;
    }
}
