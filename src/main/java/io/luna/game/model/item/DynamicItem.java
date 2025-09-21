package io.luna.game.model.item;

import io.luna.game.model.mob.attr.Attributable;
import io.luna.game.model.mob.attr.AttributeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Represents an {@link Item} with a stateful {@link AttributeMap}.
 * <p>
 * Unlike regular items, {@code DynamicItem} instances can carry arbitrary attributes that persist
 * across containers (such as inventories, banks, and trade screens) and even when converted to
 * {@link GroundItem} or back. Because of this, they are always treated as <b>non-stackable</b> and
 * have a maximum quantity of {@code 1} within any {@link ItemContainer}. When a {@code DynamicItem} is
 * removed by reference from containers, only the item matching the exact reference will be removed.
 * </p>
 * <p>
 * Example use cases include:
 * <ul>
 *   <li>Custom durability values (e.g., a pickaxe with charges left).</li>
 *   <li>Unique identifiers (e.g., for quest or event items).</li>
 *   <li>Temporary modifiers</li>
 * </ul>
 *
 * @author lare96
 */
public final class DynamicItem extends Item implements Attributable {

    /**
     * The attribute map.
     */
    private AttributeMap attributes = new AttributeMap();

    /**
     * Creates a new {@link DynamicItem}.
     *
     * @param id The item id.
     * @param attributes The attribute map.
     */
    public DynamicItem(int id, AttributeMap attributes) {
        super(id, 1);
        this.attributes = attributes;
    }

    /**
     * Creates a new {@link DynamicItem} with an empty attribute map and an amount of {@code 1}.
     *
     * @param id The item id.
     */
    public DynamicItem(int id) {
        super(id, 1);
    }

    /**
     * Creates a new {@link DynamicItem}.
     *
     * @param item The item.
     * @param attributes The attribute map.
     */
    public DynamicItem(Item item, AttributeMap attributes) {
        this(item.getId(), attributes);
    }

    /**
     * {@link DynamicItem} types are only equal by reference.
     */
    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    /**
     * {@link DynamicItem} types use the default system identity hashcode.
     */
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Forwards to {@link Item#withId(int)} while retaining attributes.
     */
    @Override
    public DynamicItem withId(int newId) {
        return new DynamicItem(super.withId(newId), attributes);
    }

    /**
     * Forwards to {@link Item#withIndex(int)} while retaining attributes.
     */
    @Override
    public DynamicIndexedItem withIndex(int index) {
        return new DynamicIndexedItem(super.withIndex(index), attributes);
    }

    /**
     * Throws an exception if the amount changes.
     */
    @Override
    public DynamicItem withAmount(int newAmount) {
        checkArgument(newAmount == 1, "DynamicItem types can only have an amount of 1.");
        return this;
    }

    /**
     * Throws an exception if the amount changes.
     */
    @Override
    public DynamicItem addAmount(int add) {
        checkArgument(add == 0, "DynamicItem types can only have an amount of 1.");
        return this;
    }

    @Override
    public AttributeMap attributes() {
        return attributes;
    }

    /**
     * Replaces the backing map of attributes with {@code newAttributes}.
     *
     * @param newAttributes The new attributes, must be non-null.
     */
    public void setAttributes(AttributeMap newAttributes) {
        attributes = requireNonNull(newAttributes);
    }
}
