package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A single item that can be contained within item containers and ground items.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Item {

    /**
     * The identifier for this {@code Item}.
     */
    private final int id;

    /**
     * The amount of this {@code Item}.
     */
    private final int amount;

    /**
     * Creates a new {@link Item}.
     *
     * @param id The identifier for this {@code Item}.
     * @param amount The amount of this {@code Item}.
     */
    public Item(int id, int amount) {
        checkArgument(id > 0 && id < ItemDefinition.DEFINITIONS.size(), "invalid item id");
        checkArgument(amount > 0, "amount <= 0");

        this.id = id;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("amount", amount).toString();
    }

    /**
     * Creates a new {@link Item} with an {@code amount} of {@code 1}.
     *
     * @param id The identifier for this {@code Item}.
     */
    public Item(int id) {
        this(id, 1);
    }

    /**
     * Creates a new item with {@code amount + addAmount} and the same identifier. The returned {@code Item} <strong>does
     * not</strong> hold any references to this one. It will also have a maximum amount of {@code Integer.MAX_VALUE}.
     *
     * @param addAmount The amount to add.
     * @return The newly incremented {@code Item}.
     */
    public Item createAndIncrement(int addAmount) {
        if (addAmount < 0) { // Same effect as decrementing.
            return createAndDecrement(Math.abs(addAmount));
        }

        int newAmount = amount + addAmount;

        if (newAmount < amount) { // An overflow.
            newAmount = Integer.MAX_VALUE;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates a new item with {@code amount - removeAmount} and the same identifier. The returned {@code Item} <strong>does
     * not</strong> hold any references to this one. It will also have a minimum amount of {@code 1}.
     *
     * @param removeAmount The amount to remove.
     * @return The newly incremented {@code Item}.
     */
    public Item createAndDecrement(int removeAmount) {
        if (removeAmount < 0) { // Same effect as incrementing.
            return createAndIncrement(Math.abs(removeAmount));
        }

        int newAmount = amount - removeAmount;

        // Value too low, or an overflow.
        if (newAmount < 1 || newAmount > amount) {
            newAmount = 1;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates a new item with {@code newAmount} and the same identifier as this instance.  The returned {@code Item}
     * <strong>does not</strong> hold any references to this one unless {@code amount == newAmount}. It will throw an
     * exception on overflows and negative values.
     *
     * @param newAmount The new amount to set.
     * @return The newly amount set {@code Item}.
     */
    public Item createWithAmount(int newAmount) {
        if (amount == newAmount) {
            return this;
        }
        return new Item(id, newAmount);
    }

    /**
     * @return The definition instance for this {@code Item}.
     */
    public ItemDefinition getItemDef() {
        return ItemDefinition.DEFINITIONS.get(id);
    }

    /**
     * @return The equipment definition for this {@code Item}.
     */
    public EquipmentDefinition getEquipmentDef() {
        return EquipmentDefinition.getDefinition(id);
    }

    /**
     * @return The identifier for this {@code Item}.
     */
    public int getId() {
        return id;
    }

    /**
     * Creates a new item with {@code newId} and the same amount as this instance. The returned {@code Item} <strong>does
     * not</strong> hold any references to this one unless {@code id == newId}. It will throw an exception on an invalid id.
     *
     * @param newId The new id to set.
     * @return The newly id set {@code Item}.
     */
    public Item createWithId(int newId) {
        if (id == newId) {
            return this;
        }
        return new Item(newId, amount);
    }

    /**
     * @return The amount of this {@code Item}.
     */
    public int getAmount() {
        return amount;
    }
}
