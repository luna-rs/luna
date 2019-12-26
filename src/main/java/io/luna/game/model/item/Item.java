package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a single item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Item {

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
    // TODO change to update amount
    public Item changeAmount(int add) {
        boolean positive = add > 0;
        int newAmount = amount + add;

        // Handle potential overflows and underflows.
        if(newAmount < 0) {
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
