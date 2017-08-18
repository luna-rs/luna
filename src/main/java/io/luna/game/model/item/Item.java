package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a single item.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Item {

    /**
     * An empty array of items.
     */
    public static final Item[] EMPTY_ARRAY = {};

    /**
     * Determines if {@code id} is within range.
     */
    public static boolean isIdWithinRange(int id) {
        return id > 0 && id < ItemDefinition.SIZE;
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
        checkArgument(isIdWithinRange(id), "id out of range");
        checkArgument(amount > 0, "amount <= 0");

        this.id = id;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("amount", amount).toString();
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
     * @return The amount.
     */
    public int getAmount() {
        return amount;
    }

    /**
     * Creates a new item with {@code amount + addAmount} and the same identifier.
     */
    public Item createAndIncrement(int addAmount) {
        if (addAmount < 0) { /* Same effect as decrementing. */
            return createAndDecrement(Math.abs(addAmount));
        }

        int newAmount = amount + addAmount;

        if (newAmount < amount) { /* An overflow. */
            newAmount = Integer.MAX_VALUE;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates a new item with {@code amount - removeAmount} and the same identifier.
     */
    public Item createAndDecrement(int removeAmount) {
        if (removeAmount < 0) { /* Same effect as incrementing. */
            return createAndIncrement(Math.abs(removeAmount));
        }

        int newAmount = amount - removeAmount;

        /* Value too low or an overflow. */
        if (newAmount < 1 || newAmount > amount) {
            newAmount = 1;
        }
        return new Item(id, newAmount);
    }

    /**
     * Creates a new item with {@code newAmount} and the same identifier.
     */
    public Item createWithAmount(int newAmount) {
        if (amount == newAmount) {
            return this;
        }
        return new Item(id, newAmount);
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * Creates a new item with {@code newId} and the same amount.
     */
    public Item createWithId(int newId) {
        if (id == newId) {
            return this;
        }
        return new Item(newId, amount);
    }

    /**
     * Returns the item definition.
     */
    public ItemDefinition getItemDef() {
        return ItemDefinition.get(id);
    }

    /**
     * Returns the equipment definition.
     */
    public EquipmentDefinition getEquipDef() {
        return EquipmentDefinition.get(id);
    }
}
