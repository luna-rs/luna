package io.luna.game.model.item.economy;

/**
 * Describes how directly a raw trade can be interpreted as a price sample.
 * <p>
 * Not every trade provides the same quality of pricing information. A simple item-for-coins trade gives a clear
 * observed item price, while barter trades require more estimation because both sides may contain multiple items whose
 * values depend on existing guide prices.
 * <p>
 * The confidence value acts as a weighting multiplier when the economy system aggregates samples. Higher-confidence
 * samples influence price updates more strongly, while lower-confidence samples still contribute but have less impact.
 *
 * @author lare96
 */
public enum SampleType {

    /**
     * A single non-coin item stack traded directly for coins.
     * <p>
     * This is the cleanest sample type because the observed price can be calculated directly from the coin amount
     * divided by the item amount.
     */
    ITEM_FOR_COINS(1.0),

    /**
     * Multiple non-coin item stacks traded for coins.
     * <p>
     * This is useful, but less precise than {@link #ITEM_FOR_COINS} because the total coin value must be split
     * across multiple item stacks using estimated guide-value shares.
     */
    MULTI_ITEM_FOR_COINS(0.45),

    /**
     * One non-coin item stack traded for one other non-coin item stack.
     * <p>
     * This provides an implied price, but depends on the existing guide price of the opposite item, making it less
     * direct than a coin-backed trade.
     */
    ITEM_FOR_ITEM(0.20),

    /**
     * A complex trade where both sides may contain multiple non-coin item stacks.
     * <p>
     * This is the weakest sample type because prices must be inferred from both baskets using existing guide values.
     * It can still provide useful market pressure, but should have a small influence on price movement.
     */
    BARTERING(0.075);

    /**
     * The weighting multiplier applied to samples of this type.
     * <p>
     * A confidence of {@code 1.0} means the sample receives full weight. Lower values reduce the sample's effective
     * contribution when calculating observed prices and update strength.
     */
    private final double confidence;

    /**
     * Creates a sample type with the given confidence multiplier.
     *
     * @param confidence The weighting multiplier for this sample type.
     */
    SampleType(double confidence) {
        this.confidence = confidence;
    }

    /**
     * @return The sample confidence multiplier.
     */
    public double getConfidence() {
        return confidence;
    }
}