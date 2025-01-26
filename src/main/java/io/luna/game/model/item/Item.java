package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;

import java.util.NoSuchElementException;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a single item.
 *
 * @author lare96
 */
public final class Item {

    /**
     * A set of search restricted items that don't show up in {@link #byName(String)} queries.
     */
    private static final ImmutableSet<Integer> SEARCH_RESTRICTED = ImmutableSet.of(6564, 6565, 6566);

    /**
     * Retrieves an item instance by name and amount.
     *
     * @param name The name.
     * @param amount The amount.
     * @return The item.
     */
    public static Item byName(String name, int amount) {
        boolean noted = name.endsWith("(noted)");
        return ItemDefinition.ALL.lookup(it -> !SEARCH_RESTRICTED.contains(it.getId()) && it.isTradeable() &&
                        it.getName().equals(name) && it.isNoted() == noted).
                map(it -> new Item(it.getId(), amount)).
                orElseThrow(() -> new NoSuchElementException("Item (" + name + ") was not valid or found."));
    }

    /**
     * Retrieves an item instance by name and with wn amount of 1.
     *
     * @param name The name.
     * @return The item.
     */
    public static Item byName(String name) {
        return byName(name, 1);
    }

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The amount.
     */
    private final int amount;

    /**
     * Creates a new {@link Item}.
     *
     * @param id The identifier.
     * @param amount The amount.
     */
    public Item(int id, int amount) {
        checkArgument(ItemDefinition.isIdValid(id), "id [" + id + "] out of range");
        checkArgument(amount >= 0, "amount <= 0");

        this.id = id;
        this.amount = amount;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Two items are only equal if the identifiers <strong>and</strong> the amounts are equal. For calculating
     * if the amount is equal to or greater, use {@link #contains(Item)}.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Item) {
            Item other = (Item) obj;
            return id == other.id && amount == other.amount;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("amount", amount).toString();
    }

    /**
     * Creates a new {@link Item}.
     *
     * @param id The identifier.
     */
    public Item(int id) {
        this(id, 1);
    }

    /**
     * Determines if this item contains {@code other}. An item that contains another item has an equal identifier
     * to that item but an amount that's equal to or greater than the item.
     *
     * @param other The other item.
     * @return {@code true} if this item contains {@code other}.
     */
    public boolean contains(Item other) {
        return id == other.id && amount >= other.amount;
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * Creates a new item with {@code newId} and the same amount.
     *
     * @param newId The new identifier.
     * @return The new item.
     */
    public Item withId(int newId) {
        if (id == newId) {
            return this;
        }
        return new Item(newId, amount);
    }

    /**
     * @return The amount.
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Creates a new item with {@code amount + addAmount} and the same identifier.
     *
     * @param add The amount to add.
     * @return The new item.
     */
    public Item addAmount(int add) {
        boolean positive = add > 0;
        int newAmount = amount + add;

        // Handle potential overflows and underflows.
        if (newAmount < 0) {
            newAmount = positive ? Integer.MAX_VALUE : 0;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates a new item with {@code newAmount} and the same identifier.
     *
     * @param newAmount The new amount.
     * @return The new item.
     */
    public Item withAmount(int newAmount) {
        if (amount == newAmount) {
            return this;
        }
        return new Item(id, newAmount);
    }

    /**
     * @return The unnoted item definition of this item. If this item definition is already unnoted, returns the same value as
     * {@link #getItemDef()}.
     */
    public ItemDefinition getUnnotedItemDef() {
        ItemDefinition current = getItemDef();
        return current.getUnnotedId().isPresent() ? ItemDefinition.ALL.retrieve(current.getUnnotedId().getAsInt()) : current;
    }

    /**
     * @return The item definition.
     */
    public ItemDefinition getItemDef() {
        return ItemDefinition.ALL.retrieve(id);
    }

    /**
     * @return The equipment definition.
     */
    public EquipmentDefinition getEquipDef() {
        return EquipmentDefinition.ALL.retrieve(id);
    }
}
