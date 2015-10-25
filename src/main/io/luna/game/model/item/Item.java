package io.luna.game.model.item;

import static com.google.common.base.Preconditions.checkArgument;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mobile.Player;

/**
 * A single item that can be contained within a {@link Player}s inventory or
 * equipment container. They are also be contained within {@link ItemEntity}s.
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
		checkArgument(id < 0 || id >= ItemDefinition.DEFINITIONS.length, "invalid item id");
		checkArgument(amount > 0, "amount <= 0");
		this.id = id;
		this.amount = amount;
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
	 * Increments the amount by {@code addAmount}. The returned {@code Item}
	 * <strong>does not</strong> hold any references to this one. It will also
	 * have a maximum amount of {@code Integer.MAX_VALUE}.
	 * 
	 * @param addAmount The amount to add.
	 * @return The newly incremented {@code Item}.
	 */
	public Item increment(int addAmount) {
		if (addAmount < 0) { // Same effect as decrementing.
			return decrement(Math.abs(addAmount));
		}

		int newAmount = amount + addAmount;

		if (newAmount < amount) { // An overflow.
			newAmount = Integer.MAX_VALUE;
		}
		return new Item(id, newAmount);
	}

	/**
	 * Decrements the amount by {@code removeAmount}. The returned {@code Item}
	 * <strong>does not</strong> hold any references to this one. It will also
	 * have a minimum amount of {@code 1}.
	 * 
	 * @param removeAmount The amount to remove.
	 * @return The newly incremented {@code Item}.
	 */
	public Item decrement(int removeAmount) {
		if (removeAmount < 0) { // Same effect as incrementing.
			return increment(Math.abs(removeAmount));
		}

		int newAmount = amount - removeAmount;

		// Value too low, or an overflow.
		if (newAmount < 1 || newAmount > amount) {
			newAmount = 1;
		}
		return new Item(id, newAmount);
	}

	/**
	 * Sets the amount to {@code newAmount}. The returned {@code Item}
	 * <strong>does not</strong> hold any references to this one. It will throw
	 * an exception on overflows and negative values.
	 * 
	 * @param newAmount The new amount to set.
	 * @return The newly amount set {@code Item}.
	 */
	public Item setAmount(int newAmount) {
		return new Item(id, newAmount);
	}

	/**
	 * @return The {@link ItemDefinition} assigned to this {@code Item}.
	 */
	public ItemDefinition definition() {
		return ItemDefinition.DEFINITIONS[id];
	}

	/**
	 * @return The identifier for this {@code Item}.
	 */
	public int getId() {
		return id;
	}

	/**
	 * @return The amount of this {@code Item}.
	 */
	public int getAmount() {
		return amount;
	}
}
