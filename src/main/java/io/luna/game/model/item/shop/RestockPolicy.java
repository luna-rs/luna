package io.luna.game.model.item.shop;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model that defines how a {@link Shop} replenishes its stock over time.
 * <p>
 * A {@link RestockPolicy} determines:
 * <ul>
 *     <li><b>Rate</b>: how often restock logic runs (in game ticks).</li>
 *     <li><b>Amount</b>: how many units are added per restock cycle.</li>
 *     <li><b>Aggression</b>: whether restocking begins as soon as stock falls below the original amount, or only once
 *     stock is depleted.</li>
 * </ul>
 * <p>
 * Policies are immutable and are built via {@link Builder}. Several commonly used presets are provided as constants.
 *
 * @author lare96
 */
public final class RestockPolicy {

    /**
     * A slow restock policy (adds {@code 1} item every {@code 10} ticks).
     */
    public static final RestockPolicy SLOW = new Builder().setRate(10).setAmount(1).build();

    /**
     * A default restock policy (adds {@code 1} item every {@code 3} ticks).
     */
    public static final RestockPolicy DEFAULT = new Builder().setRate(3).setAmount(1).build();

    /**
     * A fast restock policy (adds {@code 1} item every {@code 1} tick).
     */
    public static final RestockPolicy FAST = new Builder().setRate(1).setAmount(1).build();

    /**
     * A builder for {@link RestockPolicy} instances.
     * <p>
     * The builder provides a fluent API for assembling a policy. All values are validated to ensure a policy is
     * always created in a usable state.
     */
    public static final class Builder {

        /**
         * The amount to restock per cycle.
         */
        private int amount = -1;

        /**
         * The tick-rate between restock cycles.
         */
        private int rate = -1;

        /**
         * Whether this policy is aggressive.
         */
        private boolean aggressive;

        /**
         * Sets how many items are added each time restocking occurs.
         *
         * @param amount The restock amount per cycle.
         * @return This builder instance.
         * @throws IllegalStateException If {@code amount <= 0}.
         */
        public Builder setAmount(int amount) {
            checkState(amount > 0, "Amount to restock must be > 0.");
            this.amount = amount;
            return this;
        }

        /**
         * Sets how frequently restocking occurs.
         * <p>
         * The rate is measured in game ticks.
         *
         * @param rate The tick delay between restock cycles.
         * @return This builder instance.
         * @throws IllegalStateException If {@code rate <= 0}.
         */
        public Builder setRate(int rate) {
            checkState(rate > 0, "Restock rate must be > 0.");
            this.rate = rate;
            return this;
        }

        /**
         * Enables aggressive restocking behavior.
         * <p>
         * When aggressive restocking is enabled, items are eligible for restocking earlier than normal
         * (exact trigger threshold is defined by the {@link Shop} / {@link RestockTask} logic).
         *
         * @return This builder instance.
         */
        public Builder setAggressive() {
            aggressive = true;
            return this;
        }

        /**
         * Builds an immutable {@link RestockPolicy} using the configured builder values.
         *
         * @return A new immutable restock policy.
         * @throws IllegalStateException If required values are invalid.
         */
        public RestockPolicy build() {
            checkState(amount != -1, "Amount to restock was not set.");
            checkState(rate != -1, "Restock rate was not set.");
            return new RestockPolicy(amount, rate, aggressive);
        }
    }

    /**
     * The amount added each time a restock cycle runs.
     */
    private final int amount;

    /**
     * The tick-rate delay between restock cycles.
     */
    private final int rate;

    /**
     * Whether restocking is aggressive.
     * <p>
     * If aggressive restocking is enabled, items may begin restocking as soon as they drop below their original
     * stock level. Otherwise, they wait until there is no stock left.
     */
    private final boolean aggressive;

    /**
     * Creates a new {@link RestockPolicy}.
     *
     * @param amount Amount added per cycle.
     * @param rate Tick delay between cycles.
     * @param aggressive Whether aggressive restocking is enabled.
     */
    private RestockPolicy(int amount, int rate, boolean aggressive) {
        this.amount = amount;
        this.rate = rate;
        this.aggressive = aggressive;
    }

    /**
     * @return The amount added per restock cycle.
     */
    public int getAmount() {
        return amount;
    }

    /**
     * @return The tick delay between restock cycles.
     */
    public int getRate() {
        return rate;
    }

    /**
     * @return {@code true} if aggressive restocking is enabled.
     */
    public boolean isAggressive() {
        return aggressive;
    }
}
