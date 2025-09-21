package io.luna.game.model.item;

import io.luna.game.model.mob.attr.AttributeMap;

/**
 * A model representing a {@link DynamicItem} paired with an index.
 *
 * @author lare96
 */
public final class DynamicIndexedItem extends IndexedItem {

    /**
     * The item attributes.
     */
    private final AttributeMap attributes;

    /**
     * Creates a new {@link DynamicIndexedItem}.
     *
     * @param index The index.
     * @param id The item id.
     * @param attributes The item attributes.
     */
    public DynamicIndexedItem(int index,int id,  AttributeMap attributes) {
        super(index, id, 1);
        this.attributes = attributes;
    }

    /**
     * Creates a new {@link DynamicIndexedItem}.
     *
     * @param item The indexed item.
     * @param attributes The item attributes.
     */
    public DynamicIndexedItem(IndexedItem item, AttributeMap attributes) {
        super(item.getIndex(), item.getId(), 1);
        this.attributes = attributes;
    }

    /**
     * {@link DynamicIndexedItem} types are only equal by reference.
     */
    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    /**
     * {@link DynamicIndexedItem} types use the default system identity hashcode.
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public DynamicItem toItem() {
        return new DynamicItem(getId(), attributes);
    }

    /**
     * @return The item attributes.
     */
    public AttributeMap getAttributes() {
        return attributes;
    }
}
