package io.luna.game.model.item.shop;

import static com.google.common.base.Preconditions.checkState;

/**
 * A model representing a policy for how items are restocked in {@link Shop} types.
 *
 * @author lare96
 */
public final class RestockPolicy {

    /**
     * A builder for {@link RestockPolicy} instances.
     */
    private static final class Builder {

        /**
         * The amount to be restocked.
         */
        private int amount = -1;

        /**
         * The rate at which the restock occurs.
         */
        private int rate = -1;

        /**
         * The amount that the restock will start at. Defaults to 0.
         */
        private int startPercent = 0;

        /**
         * Sets the amount to be restocked.
         */
        public Builder setAmount(int amount) {
            this.amount = amount;
            return this;
        }

        /**
         * Sets the rate at which the restock occurs.
         */
        public Builder setRate(int rate) {
            this.rate = rate;
            return this;

        }

        /**
         * Sets the percentage of items remaining that the restock will start at. Defaults to 0.
         */
        public Builder setStartPercent(int startPercent) {
            this.startPercent = startPercent;
            return this;
        }

        /**
         * Creates a new {@link RestockPolicy} with the specified values.
         */
        public RestockPolicy build() {
            checkState(amount == -1 || amount > 0, "[amount] must be -1 or > 0");
            checkState(rate == -1 || rate > 0, "[rate] must be -1 or > 0");
            checkState(startPercent >= 0 && startPercent <= 99, "[startPercent] must be >= 0 or <= 99");
            return new RestockPolicy(amount, rate, startPercent);
        }
    }

    /**
     * The {@code DISABLED} restock policy. No items are restocked.
     */
    public static final RestockPolicy DISABLED = new Builder().build();

    /**
     * The {@code SLOW} restock policy. One item is restocked every {@code 15} seconds.
     */
    public static final RestockPolicy SLOW = new Builder().setAmount(1).setRate(25).build();

    /**
     * The {@code DEFAULT} restock policy. One item is restocked every {@code 4.8} seconds.
     */
    public static final RestockPolicy DEFAULT = new Builder().setAmount(1).setRate(8).build();

    /**
     * The {@code FAST} restock policy. One item is restocked every {@code 0.6} seconds.
     */
    public static final RestockPolicy FAST = new Builder().setAmount(1).setRate(1).build();

    /**
     * The amount to be restocked.
     */
    private final int amount;

    /**
     * The rate at which the restock occurs.
     */
    private final int rate;

    /**
     * The percentage of items remaining that the restock will start at.
     */
    private final int startPercent;

    /**
     * Creates a new {@link RestockPolicy}.
     *
     * @param amount The amount to be restocked.
     * @param rate The rate in which the restock occurs.
     * @param startPercent The percentage of items remaining that the restock will start at.
     */
    private RestockPolicy(int amount, int rate, int startPercent) {
        this.amount = amount;
        this.rate = rate;
        this.startPercent = startPercent;
    }

    /**
     * @return The amount to be restocked.
     */
    public int getAmount() {
        return amount;
    }

    /**
     * @return The rate at which the restock occurs.
     */
    public int getRate() {
        return rate;
    }

    /**
     * @return The percentage of items remaining that the restock will start at.
     */
    public int getStartPercent() {
        return startPercent;
    }
}