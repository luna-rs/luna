package io.luna.game.model.item;

import com.google.common.collect.Range;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing an item within a rational item table.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RationalItem {

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The amount range.
     */
    private final Range<Integer> amount;

    /**
     * The chance.
     */
    private final Rational chance;

    /**
     * Creates a new {@link RationalItem}.
     *
     * @param id The identifier.
     * @param amount The amount range.
     * @param chance The chance.
     */
    public RationalItem(int id, Range<Integer> amount, Rational chance) {
        checkArgument(amount.hasLowerBound() && amount.hasUpperBound(), "<amount> requires lower and upper bounds");
        checkArgument(amount.lowerEndpoint() > 0, "<amount> lower endpoint must be > 0");

        this.id = id;
        this.amount = amount;
        this.chance = chance;
    }

    /**
     * Creates a new {@link RationalItem} with a fixed amount.
     *
     * @param id The identifier.
     * @param amount The amount.
     * @param chance The chance.
     */
    public RationalItem(int id, int amount, Rational chance) {
        this(id, Range.closed(amount, amount), chance);
    }

    /**
     * Returns an item with the same identifier and a randomized amount.
     */
    public Item toItem() {
        return new Item(id, RandomUtils.random(amount));
    }

    /**
     * @return The identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The amount.
     */
    public Range<Integer> getAmount() {
        return amount;
    }

    /**
     * @return The chance.
     */
    public Rational getChance() {
        return chance;
    }
}