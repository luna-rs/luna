package io.luna.game.model.item;

import java.util.Objects;

/**
 * A model representing an item paired with an index.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class IndexedItem {

    /**
     * The index.
     */
    private final int index;

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The item amount.
     */
    private final int amount;

    /**
     * Creates a new {@link IndexedItem}.
     *
     * @param index The index.
     * @param id The item identifier.
     * @param amount The item amount.
     */
    public IndexedItem(int index, int id, int amount) {
        this.index = index;
        this.id = id;
        this.amount = amount;
    }

    /**
     * Creates a new {@link IndexedItem}.
     *
     * @param index The index.
     * @param item The item.
     */
    public IndexedItem(int index, Item item) {
        this(index, item.getId(), item.getAmount());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IndexedItem) {
            IndexedItem other = (IndexedItem) obj;
            return index == other.index &&
                    id == other.id &&
                    amount == other.amount;
        }
        return false;
    }

    @Override
    public int hashCode() {
         return Objects.hash(index, id, amount);
    }

    /**
     * Returns this indexed item as an {@link Item}.
     *
     * @return The converted indexed item.
     */
    public Item toItem() {
        return new Item(id, amount);
    }

    /**
     * @return The index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The item identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The item amount.
     */
    public int getAmount() {
        return amount;
    }
}