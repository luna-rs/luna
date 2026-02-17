package io.luna.game.model.item;

import java.util.Objects;

/**
 * Represents an {@link Item} paired with a container slot index.
 * <p>
 * This is primarily used for:
 * <ul>
 *   <li>Serialization/persistence of container state (saving/loading items with their slot)</li>
 *   <li>UI refresh packets that require explicit slot indices</li>
 *   <li>Converting between raw slot data and {@link Item} instances</li>
 * </ul>
 * <p>
 * <b>Immutability:</b> Instances are immutable.
 * <p>
 * <b>Equality:</b> Two {@link IndexedItem}s are equal if and only if their {@code index}, {@code id}, and
 * {@code amount} are all equal.
 *
 * @author lare96
 */
public class IndexedItem {

    /**
     * The container slot index where the item resides.
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
     * Creates a new {@link IndexedItem} with an {@code amount} of {@code 1}.
     *
     * @param index The slot index.
     * @param id The item identifier.
     */
    public IndexedItem(int index, int id) {
        this(index, id, 1);
    }

    /**
     * Creates a new {@link IndexedItem}.
     *
     * @param index The slot index.
     * @param id The item identifier.
     * @param amount The item amount.
     */
    public IndexedItem(int index, int id, int amount) {
        this.index = index;
        this.id = id;
        this.amount = amount;
    }

    /**
     * Creates a new {@link IndexedItem} from an {@link Item}.
     *
     * @param index The slot index.
     * @param item The item to copy id/amount from.
     * @throws NullPointerException if {@code item} is null.
     */
    public IndexedItem(int index, Item item) {
        this(index, item.getId(), item.getAmount());
    }

    /**
     * Two {@link IndexedItem}s are equal if their slot index, id, and amount are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IndexedItem) {
            IndexedItem other = (IndexedItem) obj;
            return index == other.index && id == other.id && amount == other.amount;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, id, amount);
    }

    /**
     * Converts this indexed entry into a plain {@link Item} (dropping index information).
     *
     * @return A new {@link Item} with this entry's {@link #id} and {@link #amount}.
     */
    public Item toItem() {
        return new Item(id, amount);
    }

    /**
     * @return The slot index.
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
