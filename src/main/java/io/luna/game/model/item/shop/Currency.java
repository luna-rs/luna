package io.luna.game.model.item.shop;

/**
 * The enumerated type whose elements represent currencies that can be used to purchase items
 * from shops.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum Currency {
    COINS(995, "coin", "coins"),
    TOKKUL(6529, "tokkul"),
    CASTLE_WARS_TICKET(4067, "castle wars ticket", "castle wars tickets"),
    AGILITY_ARENA_TICKET(2996, "agility arena ticket", "agility arena tickets");

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
     * @param singularName The singular name.
     * @param pluralName The plural name.
     */
    Currency(int id, String singularName, String pluralName) {
        this.id = id;
        this.singularName = singularName;
        this.pluralName = pluralName;
    }

    /**
     * Creates a new {@link Currency} with no plural name.
     *
     * @param id The item identifier.
     * @param singularName The singular name.
     */
    Currency(int id, String singularName) {
        this(id, singularName, singularName);
    }

    /**
     * @return The item identifier.
     */
    public int getId() {
        return id;
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