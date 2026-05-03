package io.luna.game.model.item.economy;

/**
 * Defines configurable settings for Luna's world economy system.
 * <p>
 * The economy stores internal prices as floating-point values so small changes can accumulate over time, but many game
 * systems eventually convert those values into whole-number GP prices. For that reason, minimum prices and maximum
 * bounds are still expressed in normal GP terms.
 *
 * @author lare96
 */
public final class WorldEconomySettings {

    /**
     * The base strength applied to price movement before sample count, sample confidence, historical weight, and
     * movement caps are applied.
     * <p>
     * A value of {@code 1.0} means the raw difference between the current guide price and observed trade price is used
     * at full strength before later dampening is applied.
     */
    private final double basePriceMovementStrength;

    /**
     * The minimum guide price for non-stackable items.
     * <p>
     * This prevents ordinary items from collapsing to extremely low or zero-value prices.
     */
    private final int minimumPrice;

    /**
     * The minimum guide price for stackable items.
     * <p>
     * Stackable items use a lower minimum because resources, runes, ammunition, and other bulk-traded items may
     * naturally settle at very small per-unit prices.
     */
    private final int minimumStackablePrice;

    /**
     * The number of minutes between scheduled economy price update passes.
     * <p>
     * During each pass, completed trades are drained from the trade queue and used to update item guide prices.
     */
    private final int priceUpdateFrequencyMinutes;

    /**
     * The maximum fraction a price may move during a single price update.
     * <p>
     * A value of {@code 0.25} means a price can move by at most {@code 25%} in one update pass, even if trade samples
     * imply a larger change.
     */
    private final double maximumPriceMovement;

    /**
     * The maximum accepted overpay ratio when deciding whether a trade is close enough to current guide value to be
     * tracked.
     * <p>
     * A value of {@code 1.0} allows trades up to {@code 100%} above the comparison value. Trades above this range are
     * treated as outliers and ignored for price movement.
     */
    private final double maximumOverpayRatio;

    /**
     * The maximum accepted underpay ratio when deciding whether a trade is close enough to current guide value to be
     * tracked.
     * <p>
     * A value of {@code 0.55} allows trades down to {@code 55%} below the comparison value. Trades below this range are
     * treated as outliers and ignored for price movement.
     */
    private final double maximumUnderpayRatio;

    /**
     * Returns the base strength applied to price movement before other modifiers are applied.
     */
    public double basePriceMovementStrength() {
        return basePriceMovementStrength;
    }

    /**
     * Returns the minimum guide price for non-stackable items.
     */
    public int minimumPrice() {
        return minimumPrice;
    }

    /**
     * Returns the minimum guide price for stackable items.
     */
    public int minimumStackablePrice() {
        return minimumStackablePrice;
    }

    /**
     * Returns the number of minutes between scheduled economy price update passes.
     */
    public int priceUpdateFrequencyMinutes() {
        return priceUpdateFrequencyMinutes;
    }

    /**
     * Returns the maximum fraction a price may move during a single update.
     */
    public double maximumPriceMovement() {
        return maximumPriceMovement;
    }

    /**
     * Returns the maximum accepted overpay ratio for tracked trades.
     */
    public double maximumOverpayRatio() {
        return maximumOverpayRatio;
    }

    /**
     * Returns the maximum accepted underpay ratio for tracked trades.
     */
    public double maximumUnderpayRatio() {
        return maximumUnderpayRatio;
    }

    /**
     * Private constructor.
     */
    private WorldEconomySettings(double basePriceMovementStrength,
                                 int minimumPrice,
                                 int minimumStackablePrice,
                                 int priceUpdateFrequencyMinutes,
                                 double maximumPriceMovement,
                                 double maximumOverpayRatio,
                                 double maximumUnderpayRatio) {
        this.basePriceMovementStrength = basePriceMovementStrength;
        this.minimumPrice = minimumPrice;
        this.minimumStackablePrice = minimumStackablePrice;
        this.priceUpdateFrequencyMinutes = priceUpdateFrequencyMinutes;
        this.maximumPriceMovement = maximumPriceMovement;
        this.maximumOverpayRatio = maximumOverpayRatio;
        this.maximumUnderpayRatio = maximumUnderpayRatio;
    }
}