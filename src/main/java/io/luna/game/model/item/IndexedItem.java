package io.luna.game.model.item;

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
     * The item.
     */
    private final Item item;

    /**
     * Creates a new {@link IndexedItem}.
     *
     * @param index The index.
     * @param item The item.
     */
    public IndexedItem(int index, Item item) {
        this.item = item;
        this.index = index;
    }

    /**
     * Returns the item identifier.
     */
    public int getId() {
        return item.getId();
    }

    /**
     * Returns the item amount.
     */
    public int getAmount() {
        return item.getAmount();
    }

    /**
     * @return The index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The item.
     */
    public Item getItem() {
        return item;
    }
}