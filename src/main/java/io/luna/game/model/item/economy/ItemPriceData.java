package io.luna.game.model.item.economy;

import io.luna.Luna;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.Item;

import static com.google.common.base.Preconditions.checkState;

/**
 * Holds one immutable economy price snapshot for a single {@link Item}.
 * <p>
 * Prices are stored internally as {@code double} values so fractional GP movement can accumulate over many economy
 * update cycles. Callers that need a concrete in-game value should convert the price to whole GP at the usage site.
 * <p>
 * Each snapshot stores the current price/sample state and the previous price/sample state. Price updates create a new
 * {@link ItemPriceData} instance instead of mutating the existing one.
 *
 * @author lare96
 */
public final class ItemPriceData {

    /**
     * The base multiplier applied to the difference between the current guide price and the observed trade price.
     * <p>
     * This is currently fixed at {@code 1.0}. Final movement is mainly controlled by raw sample scaling,
     * {@link SampleType} confidence, historical resistance, and the configured maximum movement cap.
     */
    private static final double BASE_PRICE_MOVEMENT_STRENGTH = 1.0;

    /**
     * The maximum amount of historical sample data used when calculating movement resistance.
     * <p>
     * The lifetime {@link #samples} value may continue growing beyond this value, but movement resistance treats the
     * item as having at most this many samples. This prevents old or heavily traded items from becoming practically
     * impossible to move in price.
     */
    private static final long MAX_SAMPLES = 1_000_000_000L;

    /**
     * Computes the starting economy guide price for an item.
     * <p>
     * The item's definition value is used as the base price, then clamped to the configured economy minimum. Stackable
     * items use {@code minimumStackablePrice}; non-stackable items use {@code minimumPrice}.
     *
     * @param id The item id to compute a starting price for.
     * @return The starting economy guide price for the item.
     */
    public static int computeInitialPrice(int id) {
        ItemDefinition def = ItemDefinition.ALL.retrieve(id);
        return Math.max(def.getValue(), def.isStackable() ? Luna.settings().economy().minimumStackablePrice() :
                Luna.settings().economy().minimumPrice());
    }

    /**
     * Validates an internal guide price for an item.
     * <p>
     * The price must be finite, fit within the integer GP range, and respect the configured economy minimum for the
     * item. Stackable items use {@code minimumStackablePrice}; non-stackable items use {@code minimumPrice}.
     *
     * @param checkId The item id the price belongs to.
     * @param checkPrice The internal guide price to validate.
     * @throws IllegalStateException If {@code checkPrice} is not finite.
     * @throws IllegalStateException If {@code checkPrice} exceeds {@link Integer#MAX_VALUE}.
     * @throws IllegalStateException If {@code checkPrice} is below this item's configured minimum price.
     */
    public static void checkPrice(int checkId, double checkPrice) {
        boolean stackable = ItemDefinition.ALL.retrieve(checkId).isStackable();
        int minimumPrice = stackable ? Luna.settings().economy().minimumStackablePrice() :
                Luna.settings().economy().minimumPrice();
        checkState(Double.isFinite(checkPrice), "<price> must be finite.");
        checkState(checkPrice <= Integer.MAX_VALUE, "<price> cannot exceed Integer.MAX_VALUE.");
        checkState(checkPrice >= minimumPrice, "<price> cannot be below the minimum price of " + minimumPrice + ".");
    }

    /**
     * The item id this price snapshot belongs to.
     */
    final int id;

    /**
     * The current internal guide price for this item.
     * <p>
     * This value may contain fractional GP and should be used for economy calculations.
     */
    final double price;

    /**
     * The guide price before the most recent update.
     */
    final double lastPrice;

    /**
     * The total number of effective samples that have contributed to this item's current price.
     * <p>
     * Effective samples are raw item amounts after stack-size scaling and {@link SampleType} confidence have been
     * applied.
     */
    final long samples;

    /**
     * The total effective sample count before the most recent update.
     * <p>
     * The number of effective samples added by the most recent update can be calculated with
     * {@code samples - lastSamples}.
     */
    final long lastSamples;

    /**
     * The time this price snapshot was created, in epoch milliseconds.
     * <p>
     * Since price updates create new snapshots, this also represents the update time for this instance.
     */
    final long lastUpdated;

    /**
     * Creates a price snapshot from persisted or already-calculated economy values.
     *
     * @param id The item id this price snapshot belongs to.
     * @param price The current internal guide price.
     * @param lastPrice The guide price before the most recent update.
     * @param samples The total effective sample count.
     * @param lastSamples The previous total effective sample count.
     */
    public ItemPriceData(int id, double price, double lastPrice, long samples, long lastSamples) {
        checkPrice(id, price);
        this.id = id;
        this.price = price;
        this.lastPrice = lastPrice;
        this.samples = samples;
        this.lastSamples = lastSamples;
        lastUpdated = System.currentTimeMillis();
    }

    /**
     * Creates a new price snapshot using the item's computed starting economy price.
     *
     * @param id The item id this price snapshot belongs to.
     */
    public ItemPriceData(int id) {
        this.id = id;
        price = computeInitialPrice(id);
        lastPrice = price;
        samples = 0L;
        lastSamples = samples;
        lastUpdated = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemPriceData)) return false;
        ItemPriceData that = (ItemPriceData) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    /**
     * Creates a new price snapshot with an explicitly assigned guide price.
     * <p>
     * This should be used for trusted/manual price assignments such as admin tools, migrations, imports, controlled
     * resets, or SQL-loaded state. Normal trade-driven movement should use
     * {@link #setCalculatedPrice(double, SampleType, Item)} instead.
     *
     * @param newPrice The new internal guide price.
     * @param additionalSamples The number of effective samples to add to the lifetime sample count.
     * @return A new {@link ItemPriceData} snapshot containing the updated price and sample state.
     * @throws IllegalStateException If {@code newPrice} is not finite.
     * @throws IllegalStateException If {@code newPrice} exceeds {@link Integer#MAX_VALUE}.
     * @throws IllegalStateException If {@code newPrice} is below this item's configured minimum price.
     * @throws IllegalStateException If {@code additionalSamples} is below {@code 0}.
     */
    public ItemPriceData setPrice(double newPrice, long additionalSamples) {
        checkPrice(id, newPrice);
        checkState(additionalSamples >= 0, "<additionalSamples> cannot be below 0.");
        return new ItemPriceData(id, newPrice, price, samples + additionalSamples, samples);
    }

    /**
     * Creates a new price snapshot by moving the current guide price toward an observed trade price.
     * <p>
     * Raw samples are derived from {@code samplesFrom}, converted into effective samples using {@code type}, then used
     * to calculate movement strength. If fewer than one effective sample is produced, this snapshot is returned
     * unchanged.
     *
     * @param tradePrice The observed per-item trade price.
     * @param type The source/type of trade sample being applied.
     * @param samplesFrom The item stack used to determine the raw sample count.
     * @return A new {@link ItemPriceData} snapshot if at least one effective sample was produced, otherwise this
     * snapshot.
     */
    public ItemPriceData setCalculatedPrice(double tradePrice, SampleType type, Item samplesFrom) {
        long additionalSamples = calculateSamplesFrom(samplesFrom);
        if (additionalSamples > 0L) {
            long effectiveSamples = calculateEffectiveSamples(type, additionalSamples);
            if (effectiveSamples < 1L) {
                return this;
            }
            double newPrice = calculateNewPrice(price, tradePrice, effectiveSamples, samples);
            return new ItemPriceData(id, newPrice, price, samples + effectiveSamples, samples);
        }
        return this;
    }

    /**
     * Calculates the next guide price by moving {@code oldPrice} toward {@code tradePrice}.
     * <p>
     * The raw price difference is multiplied by the base movement strength and the calculated movement strength. The
     * movement strength already accounts for new effective samples, existing historical samples, price movement
     * resistance, and the maximum movement cap.
     *
     * @param oldPrice The current guide price before this update.
     * @param tradePrice The observed per-item trade price.
     * @param additionalSamples The effective number of samples produced by this update.
     * @param currentSamples The current total effective sample count for this item.
     * @return The new internal guide price.
     */
    private double calculateNewPrice(double oldPrice, double tradePrice, long additionalSamples, long currentSamples) {
        double rawDifference = tradePrice - oldPrice;
        double movementStrength = calculateMovementStrength(additionalSamples, currentSamples);
        double newPriceDifference = rawDifference * BASE_PRICE_MOVEMENT_STRENGTH * movementStrength;
        return oldPrice + newPriceDifference;
    }

    /**
     * Calculates the raw sample count contributed by an item stack.
     * <p>
     * Stackable items use the square root of their amount. This allows larger stacks to provide more confidence while
     * preventing huge stacks from dominating the guide price. Non-stackable items contribute one sample per item.
     *
     * @param item The item stack being sampled.
     * @return The raw sample count contributed by this stack.
     */
    private int calculateSamplesFrom(Item item) {
        int amount = item.getAmount();
        if (item.getItemDef().isStackable()) {
            int scaledSamples = (int) Math.floor(Math.sqrt(amount));
            return Math.max(0, scaledSamples);
        }
        return amount;
    }

    /**
     * Converts a raw sample count into an effective sample count using the confidence of the sample source.
     *
     * @param type The source/type of trade sample.
     * @param samples The raw sample count.
     * @return The effective sample count after source confidence is applied.
     */
    private long calculateEffectiveSamples(SampleType type, long samples) {
        return (long) Math.floor(samples * type.getConfidence());
    }

    /**
     * Calculates the percentage strength used to move this item's guide price.
     * <p>
     * Movement strength is calculated by comparing the new effective sample count against this item's historical
     * sample weight. Historical samples are square-rooted, so prices become more stable over time without becoming
     * permanently frozen.
     * <p>
     * The resistance formula is:
     * <pre>
     * historicalWeight = sqrt(min(currentSamples, MAX_SAMPLES)) * (250.0 * priceMovementResistance)
     * rawStrength = additionalSamples / (historicalWeight + additionalSamples)
     * </pre>
     * The returned value is capped by {@code maximumPriceMovement}.
     *
     * @param additionalSamples The effective number of new samples contributing to this update.
     * @param currentSamples The total effective sample count already recorded for this item.
     * @return The final movement strength, capped by the configured maximum price movement.
     */
    private double calculateMovementStrength(long additionalSamples, long currentSamples) {
        if (currentSamples > MAX_SAMPLES) {
            currentSamples = MAX_SAMPLES;
        }
        double historicalWeight = Math.max(Math.sqrt(currentSamples) *
                (250.0 * Luna.settings().economy().priceMovementResistance()), 0.0);
        double rawStrength = additionalSamples / (historicalWeight + additionalSamples);
        return Math.min(Luna.settings().economy().maximumPriceMovement(), rawStrength);
    }
}