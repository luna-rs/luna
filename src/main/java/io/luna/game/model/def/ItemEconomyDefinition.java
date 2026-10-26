package io.luna.game.model.def;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Defines an item's initial economy guide price and popularity independently of its recorded trade history.
 *
 * @author lare96
 */
public final class ItemEconomyDefinition implements Definition {

    /**
     * Repository of all {@link ItemEconomyDefinition}s keyed by unnoted item id.
     */
    public static final DefinitionRepository<ItemEconomyDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The item id this economy definition belongs to.
     */
    private final int id;

    /**
     * The initial whole-GP guide price, or {@code null} to use the item definition's value.
     */
    private final Integer initialPrice;

    /**
     * The initial popularity in effective-sample units, used only for bot selection.
     */
    private final long initialPopularity;

    /**
     * Creates a new {@link ItemEconomyDefinition}.
     *
     * @param id The unnoted item id.
     * @param initialPrice The optional positive whole-GP starting price.
     * @param initialPopularity The nonnegative popularity seed.
     */
    public ItemEconomyDefinition(int id, Integer initialPrice, long initialPopularity) {
        checkArgument(id >= 0 && id != 995, "Invalid economy item ID: %s", id);
        checkArgument(initialPrice == null || initialPrice > 0, "Initial price must be positive: %s", id);
        checkArgument(initialPopularity >= 0, "Initial popularity must be nonnegative: %s", id);
        this.id = id;
        this.initialPrice = initialPrice;
        this.initialPopularity = initialPopularity;
    }

    /**
     * Adds a diminishing seed to real history without changing price resistance or persisted sample counts.
     * The seed contribution halves when real samples reach its initial value and approaches zero thereafter.
     *
     * @param samples The observed effective sample count.
     * @return The popularity used for bot item selection.
     */
    public double computePopularity(long samples) {
        double observed = Math.max(0L, samples);
        return observed + (initialPopularity == 0 ? 0.0 :
                initialPopularity / (1.0 + observed / initialPopularity));
    }

    @Override
    public int id() {
        return id;
    }

    /**
     * Returns the initial guide price override.
     *
     * @return The initial whole-GP price, or {@code null} when no override is configured.
     */
    public Integer getInitialPrice() {
        return initialPrice;
    }

    /**
     * Returns the initial popularity seed.
     *
     * @return The popularity in effective-sample units.
     */
    public long getInitialPopularity() {
        return initialPopularity;
    }
}
