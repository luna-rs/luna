package io.luna.game.model.item.shop;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a policy for how items are restocked in {@link Shop}s.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class RestockPolicy {

    /**
     * The {@code DISABLED} restock policy. No items are restocked.
     */
    public static final RestockPolicy DISABLED = new RestockPolicy();

    /**
     * The {@code SLOW} restock policy. One item is restocked every {@code 15} seconds.
     */
    public static final RestockPolicy SLOW = new RestockPolicy(1, 25);

    /**
     * The {@code DEFAULT} restock policy. One item is restocked every {@code 4.8} seconds.
     */
    public static final RestockPolicy DEFAULT = new RestockPolicy(1, 8);

    /**
     * The {@code FAST} restock policy. One item is restocked every {@code 0.6} seconds.
     */
    public static final RestockPolicy FAST = new RestockPolicy(1, 1);

    /**
     * The amount to be restocked.
     */
    private final int stockAmount;

    /**
     * The rate in which the restock occurs.
     */
    private final int tickRate;

    /**
     * Creates a new {@link RestockPolicy}.
     *
     * @param stockAmount The amount to be restocked.
     * @param tickRate The rate in which the restock occurs.
     */
    public RestockPolicy(int stockAmount, int tickRate) {
        checkArgument(stockAmount > 0, "Stock amount must be > 0.");
        checkArgument(tickRate > 0, "Tick rate must be > 0.");
        this.stockAmount = stockAmount;
        this.tickRate = tickRate;
    }

    /**
     * Creates a new {@code DISABLED} {@link RestockPolicy}. Should only be invoked once.
     */
    private RestockPolicy() {
        stockAmount = -1;
        tickRate = -1;
    }

    /**
     * @return The amount to be restocked.
     */
    public int getStockAmount() {
        return stockAmount;
    }

    /**
     * @return The rate in which the restock occurs.
     */
    public int getTickRate() {
        return tickRate;
    }
}