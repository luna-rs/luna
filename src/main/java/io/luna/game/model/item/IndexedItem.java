package io.luna.game.model.item;

/**
 * An {@link Item} wrapper holding an additional value representing its index in some sort of collection or group. This is
 * only utilized during serialization to avoid having to serialize 'empty' ({@code null}) indexes.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class IndexedItem {

    /**
     * The {@link Item} being wrapped.
     */
    private final Item item;

    /**
     * The value describing the index.
     */
    private final int index;

    /**
     * Creates a new {@link IndexedItem}.
     *
     * @param item The {@link Item} being wrapped.
     * @param index The value describing the index.
     */
    public IndexedItem(Item item, int index) {
        this.item = item;
        this.index = index;
    }

    /**
     * @return The identifier for the wrapped {@link Item}.
     */
    public int getId() {
        return item.getId();
    }

    /**
     * @return The amount held by the wrapped {@link Item}.
     */
    public int getAmount() {
        return item.getAmount();
    }

    /**
     * @return The value describing the index.
     */
    public int getIndex() {
        return index;
    }
}