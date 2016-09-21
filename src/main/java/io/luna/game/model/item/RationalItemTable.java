package io.luna.game.model.item;

import com.google.common.collect.ImmutableList;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;
import io.netty.util.internal.ThreadLocalRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A model representing an immutable table of rational items.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RationalItemTable {

    /**
     * An immutable list of rational items.
     */
    private final ImmutableList<RationalItem> items;

    /**
     * Creates a new {@link RationalItemTable}.
     *
     * @param fromItems The rational items.
     */
    public RationalItemTable(RationalItem... fromItems) {
        items = ImmutableList.copyOf(fromItems);
    }

    /**
     * Runs through the table in a linear fashion, determining if each item can be 'selected' based on
     * its rational value.
     * <p>
     * Will return an empty list if no items were selected.
     */
    public List<Item> selectLinear() {
        List<Item> selected = new ArrayList<>();
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        for (RationalItem rationalItem : items) { /* Run through the table. */
            Rational itemChance = rationalItem.getChance();

            if (itemChance.doubleValue() >= rand.nextDouble()) { /* Can we select? */
                selected.add(rationalItem.toItem());
            }
        }
        return selected;
    }

    /**
     * Computes a single item from the table at random, and determines if it can be 'selected' based on its
     * rational value.
     */
    public Optional<Item> selectIndexed() {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        RationalItem item = RandomUtils.random(items); /* Compute random item. */
        Rational chance = item.getChance();

        if (chance.doubleValue() >= rand.nextDouble()) { /* Can we select? */
            return Optional.of(item.toItem());
        } else {
            return Optional.empty();
        }
    }

    /**
     * @return An immutable list of rational items.
     */
    public ImmutableList<RationalItem> getItems() {
        return items;
    }
}
