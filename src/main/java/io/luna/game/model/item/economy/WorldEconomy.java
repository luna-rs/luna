package io.luna.game.model.item.economy;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.gson.reflect.TypeToken;
import io.luna.Luna;
import io.luna.game.model.World;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.Item;
import io.luna.util.GsonUtils;
import kotlin.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.stream.Collectors.toMap;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * Tracks Luna's dynamic item guide prices and applies completed trade samples over time.
 * <p>
 * The economy service stores one active {@link ItemPriceData} snapshot per tracked tradeable item. Completed trades are
 * queued with {@link #addTrade(CompletedTradeData)} and processed on a fixed schedule. Each processed trade is
 * interpreted as one of the supported economy sample types, then used to move affected item prices toward the observed
 * trade value.
 * <p>
 * Prices can be persisted through JSON or SQL depending on the active serializer configuration. JSON mode writes the
 * entire active price map to disk. SQL mode writes only pending active-price updates and price-history snapshots through
 * batch SQL tasks.
 *
 * @author lare96
 */
public final class WorldEconomy extends AbstractScheduledService {

    /**
     * The logger used for economy load/update messages.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The JSON price file used when the world is configured for JSON persistence.
     */
    private static final Path PRICE_PATH = Paths.get("data", "game", "bots", "items", "economy", "prices.json");

    /**
     * The Gson type used to load the JSON price map.
     */
    private static final Type PRICE_MAP_TYPE = new TypeToken<Map<Integer, ItemPriceData>>() {
    }.getType();

    /**
     * The world that owns this economy service.
     */
    private final World world;

    /**
     * The economy settings used for update frequency, acceptable trade ranges, and price movement limits.
     */
    private final WorldEconomySettings settings;

    /**
     * The active guide price snapshots.
     * <p>
     * Keys are item ids. Values are immutable {@link ItemPriceData} snapshots. Price updates replace the old snapshot
     * with a new one.
     */
    final Map<Integer, ItemPriceData> prices = new ConcurrentHashMap<>();

    /**
     * Completed trades waiting to be processed by the next economy update cycle.
     */
    private final Queue<CompletedTradeData> trades = new ConcurrentLinkedQueue<>();

    /**
     * Active price snapshots that need to be persisted.
     * <p>
     * SQL batch update tasks consume entries from this map as they build their batch. The map is keyed by item id so
     * multiple updates for the same item in one cycle collapse into the latest snapshot.
     */
    private final Map<Integer, ItemPriceData> priceUpdates = new ConcurrentHashMap<>();

    /**
     * Price snapshots that need to be written to price history.
     * <p>
     * SQL history tasks consume entries from this queue as they build their batch. Unlike {@link #priceUpdates}, history
     * entries are queued individually so each price movement can be recorded as its own snapshot.
     */
    private final Queue<ItemPriceData> historyUpdates = new ConcurrentLinkedQueue<>();

    /**
     * The number of active price entries known after the last update cycle.
     * <p>
     * This is used to detect newly-created price data even when no completed trades are currently queued.
     */
    private int lastPricesAmount;

    /**
     * Creates a new world economy service.
     *
     * @param world The world that owns this economy service.
     */
    public WorldEconomy(World world) {
        this.world = world;
        settings = Luna.settings().economy();
    }

    /**
     * Loads saved economy prices before the service starts processing trades.
     * <p>
     * JSON mode loads the full price map from {@link #PRICE_PATH}. SQL mode delegates loading to
     * {@link LoadEconomySqlTask}. If no saved data exists, prices are created lazily as tradeable items are encountered.
     *
     * @throws Exception If the price directory cannot be created, JSON loading fails, or SQL loading fails.
     */
    @Override
    protected void startUp() throws Exception {
        if (world.getSerializerManager().isJson()) {
            Files.createDirectories(PRICE_PATH.getParent());
            if (Files.exists(PRICE_PATH)) {
                try (BufferedReader reader = Files.newBufferedReader(PRICE_PATH)) {
                    Map<Integer, ItemPriceData> loaded = GsonUtils.GSON.fromJson(reader, PRICE_MAP_TYPE);
                    if (loaded != null) {
                        prices.putAll(loaded);
                    }
                }
            }
        } else {
            var loadEconomyTask = new LoadEconomySqlTask(world);
            loadEconomyTask.run();
        }
        lastPricesAmount = prices.size();
        logger.info("A total of {} economy prices have been loaded.", box(lastPricesAmount));
    }

    /**
     * Processes queued trades and persists any resulting price changes.
     * <p>
     * Trades are classified by offer shape:
     * <ul>
     *     <li>One item stack for coins is handled by {@link #itemForCoins(Item, Item)}.</li>
     *     <li>One item stack for one other item stack is handled by {@link #itemForItem(Item, Item)}.</li>
     *     <li>Multiple item stacks for coins are handled by {@link #multiItemForCoins(List, double)}.</li>
     *     <li>All other mixed trades are handled by {@link #bartering(List, List)}.</li>
     * </ul>
     * Price updates are persisted after all currently queued trades have been processed.
     *
     * @throws Exception If trade processing or persistence fails.
     */
    @Override
    protected void runOneIteration() throws Exception {
        if (trades.isEmpty() && prices.size() == lastPricesAmount) {
            return;
        }

        for (; ; ) {
            CompletedTradeData tradeData = trades.poll();
            if (tradeData == null) {
                break;
            }

            List<Item> sideA = tradeData.getSideA();
            List<Item> sideB = tradeData.getSideB();

            if (sideA.size() == 1 && sideB.size() == 1) {
                Item itemA = sideA.getFirst();
                Item itemB = sideB.getFirst();
                if (itemA.getId() == itemB.getId()) {
                    continue;
                }

                if (itemA.getId() == 995 || itemB.getId() == 995) {
                    itemForCoins(itemA, itemB);
                } else {
                    itemForItem(itemA, itemB);
                }
            } else if (sideA.size() == 1 || sideB.size() == 1) {
                List<Item> barterItems = sideA.size() > 1 ? sideA : sideB;
                List<Item> coinItem = sideA != barterItems ? sideA : sideB;

                if ((barterItems == sideA && sideB.getFirst().getId() != 995) ||
                        (barterItems == sideB && sideA.getFirst().getId() != 995)) {
                    bartering(sideA, sideB);
                    continue;
                }

                multiItemForCoins(barterItems, coinItem.getFirst().getAmount());
            } else {
                bartering(sideA, sideB);
            }
        }

        lastPricesAmount = prices.size();

        int updates = priceUpdates.size();
        savePrices();
        logger.info("A total of {} economy prices have been updated.", box(updates));
    }

    @Override
    protected Scheduler scheduler() {
        Duration priceUpdateFrequency = Duration.ofMinutes(settings.priceUpdateFrequencyMinutes());
        return Scheduler.newFixedRateSchedule(priceUpdateFrequency, priceUpdateFrequency);
    }

    /**
     * Queues a completed trade for the next economy update cycle.
     *
     * @param entry The completed trade to process.
     */
    public void addTrade(CompletedTradeData entry) {
        trades.add(entry);
    }

    /**
     * Resolves active price data for a tradeable item.
     * <p>
     * Coins are not tracked because one coin is always worth one GP. Untradeable items return {@code null}. Tradeable
     * items are created lazily using {@link ItemPriceData#ItemPriceData(int)} when no active price data exists yet.
     * Newly-created prices are queued for active-price persistence and history persistence.
     *
     * @param id The item id to resolve.
     * @return The active price data for the item, or {@code null} if the item should not be tracked.
     */
    public ItemPriceData resolvePriceData(int id) {
        if (id == 995 || !ItemDefinition.ALL.retrieve(id).isTradeable()) {
            return null;
        }
        return prices.computeIfAbsent(id, key -> {
            ItemPriceData newData = new ItemPriceData(id);
            priceUpdates.put(newData.id, newData);
            historyUpdates.add(newData);
            return newData;
        });
    }

    /**
     * Returns the whole-GP guide price for an item.
     *
     * @param id The item id.
     * @return The floored guide price.
     */
    public long getPrice(int id) {
        return (long) Math.floor(getRealPrice(id));
    }

    /**
     * Returns the whole-GP guide value of an item stack.
     *
     * @param item The item stack to value.
     * @return The floored internal total guide value.
     */
    public long getTotalPrice(Item item) {
        return (long) Math.floor(getRealTotalPrice(item));
    }

    /**
     * Returns the internal guide price for an item.
     * <p>
     * Coins always return {@code 1.0}. Tracked tradeable items return their active guide price. Untracked items fall
     * back to their computed initial economy price.
     *
     * @param id The item id.
     * @return The internal guide price.
     */
    public double getRealPrice(int id) {
        if (id == 995) {
            return 1.0;
        }
        ItemPriceData data = resolvePriceData(id);
        if (data == null) {
            return ItemPriceData.computeInitialPrice(id);
        }
        return data.price;
    }

    /**
     * Returns the internal guide value of an item stack.
     *
     * @param item The item stack to value.
     * @return The internal guide price multiplied by the item amount.
     */
    public double getRealTotalPrice(Item item) {
        return getRealPrice(item.getId()) * (double) item.getAmount();
    }

    /**
     * Processes a trade where one side offered coins and the other side offered a single item stack.
     * <p>
     * The observed per-item offer price is calculated from the coin amount divided by the traded item amount. The
     * sample is ignored if the observed price is outside the configured acceptable trade range.
     *
     * @param itemA The first trade offer.
     * @param itemB The second trade offer.
     */
    private void itemForCoins(Item itemA, Item itemB) {
        Item tradedItem = itemA.getId() != 995 ? itemA : itemB;
        Item coins = itemA.getId() == 995 ? itemA : itemB;
        double offerPrice = (double) coins.getAmount() / (double) tradedItem.getAmount();

        ItemPriceData data = resolvePriceData(tradedItem.getId());
        if (data == null) {
            return;
        }

        double realPrice = data.price;
        if (!withinRange(offerPrice, realPrice)) {
            return;
        }

        ItemPriceData newData = data.setCalculatedPrice(offerPrice, SampleType.ITEM_FOR_COINS, tradedItem);
        if (newData != data) {
            prices.put(data.id, newData);
            priceUpdates.put(data.id, newData);
            historyUpdates.add(newData);
        }
    }

    /**
     * Processes a direct item-for-item trade.
     * <p>
     * Each item stack is valued using current guide prices. If the two sides are within the configured acceptable trade
     * range, each item is moved toward the per-item value implied by the opposite side.
     *
     * @param itemA The item stack from the first side.
     * @param itemB The item stack from the second side.
     */
    private void itemForItem(Item itemA, Item itemB) {
        ItemPriceData dataA = resolvePriceData(itemA.getId());
        ItemPriceData dataB = resolvePriceData(itemB.getId());
        if (dataA == null || dataB == null) {
            return;
        }

        double valueA = dataA.price * itemA.getAmount();
        double valueB = dataB.price * itemB.getAmount();
        if (!withinRange(valueA, valueB)) {
            return;
        }

        double tradePriceA = valueB / itemA.getAmount();
        double tradePriceB = valueA / itemB.getAmount();

        var newDataA = dataA.setCalculatedPrice(tradePriceA, SampleType.ITEM_FOR_ITEM, itemA);
        var newDataB = dataB.setCalculatedPrice(tradePriceB, SampleType.ITEM_FOR_ITEM, itemB);

        if (newDataA != dataA) {
            prices.put(dataA.id, newDataA);
            priceUpdates.put(dataA.id, newDataA);
            historyUpdates.add(newDataA);
        }
        if (newDataB != dataB) {
            prices.put(dataB.id, newDataB);
            priceUpdates.put(dataB.id, newDataB);
            historyUpdates.add(newDataB);
        }
    }

    /**
     * Processes a trade where multiple item stacks were exchanged for coins.
     * <p>
     * The total guide value of the item side is compared against the coin offer. If the trade is within the accepted
     * range, each non-coin item is moved proportionally toward the observed coin-side value.
     *
     * @param barterItems The item side of the trade.
     * @param totalCoins The number of coins offered by the opposite side.
     */
    private void multiItemForCoins(List<Item> barterItems, double totalCoins) {
        double totalValue = 0.0;
        for (Item item : barterItems) {
            totalValue += item.getId() == 995 ? item.getAmount() : getRealTotalPrice(item);
        }

        if (!withinRange(totalValue, totalCoins) || totalValue <= 0.0 || totalCoins <= 0) {
            return;
        }

        for (Item item : barterItems) {
            if (item.getId() == 995) {
                continue;
            }

            ItemPriceData data = resolvePriceData(item.getId());
            if (data == null) {
                return;
            }

            double tradePrice = data.price * (totalCoins / totalValue);
            var newData = data.setCalculatedPrice(tradePrice, SampleType.MULTI_ITEM_FOR_COINS, item);
            if (newData != data) {
                prices.put(data.id, newData);
                priceUpdates.put(data.id, newData);
                historyUpdates.add(newData);
            }
        }
    }

    /**
     * Processes a mixed item barter trade.
     * <p>
     * Exact same-item, same-amount entries are treated as cancelled-out trade components and ignored. Partial overlaps
     * are intentionally left in the bundle because barter trades are treated as whole-trade price signals rather than
     * strict net-item exchanges.
     * <p>
     * The higher-value side is moved downward toward the lower-value side, while the lower-value side is moved upward
     * toward the higher-value side.
     *
     * @param sideA The first trade side.
     * @param sideB The second trade side.
     */
    private void bartering(List<Item> sideA, List<Item> sideB) {
        var excludedA = sideA.stream().collect(toMap(Item::getId, Item::getAmount, Integer::sum));
        var excludedB = sideB.stream().collect(toMap(Item::getId, Item::getAmount, Integer::sum));

        double totalValueA = 0.0;
        double totalValueB = 0.0;

        for (Item item : sideA) {
            int id = item.getId();
            if (excludedB.getOrDefault(id, 0) == item.getAmount()) {
                continue;
            }
            excludedB.remove(id);
            totalValueA += id == 995 ? item.getAmount() : getRealTotalPrice(item);
        }

        for (Item item : sideB) {
            int id = item.getId();
            if (excludedA.getOrDefault(id, 0) == item.getAmount()) {
                continue;
            }
            excludedA.remove(id);
            totalValueB += id == 995 ? item.getAmount() : getRealTotalPrice(item);
        }

        if (totalValueA == totalValueB || !withinRange(totalValueA, totalValueB)) {
            return;
        }

        var itemsValueA = new Triple<>(totalValueA, sideA, excludedB);
        var itemsValueB = new Triple<>(totalValueB, sideB, excludedA);
        var winner = itemsValueA.getFirst() > itemsValueB.getFirst() ? itemsValueA : itemsValueB;
        var loser = itemsValueA != winner ? itemsValueA : itemsValueB;

        double winnerPriceMultiplier = loser.getFirst() / winner.getFirst();
        double loserPriceMultiplier = winner.getFirst() / loser.getFirst();

        for (Item item : winner.getSecond()) {
            if (winner.getThird().containsKey(item.getId()) || item.getId() == 995) {
                continue;
            }

            ItemPriceData data = resolvePriceData(item.getId());
            if (data == null) {
                continue;
            }

            double tradePrice = data.price * winnerPriceMultiplier;
            var newData = data.setCalculatedPrice(tradePrice, SampleType.BARTERING, item);
            if (newData != data) {
                prices.put(data.id, newData);
                priceUpdates.put(data.id, newData);
                historyUpdates.add(newData);
            }
        }

        for (Item item : loser.getSecond()) {
            if (loser.getThird().containsKey(item.getId()) || item.getId() == 995) {
                continue;
            }

            ItemPriceData data = resolvePriceData(item.getId());
            if (data == null) {
                continue;
            }

            double tradePrice = data.price * loserPriceMultiplier;
            var newData = data.setCalculatedPrice(tradePrice, SampleType.BARTERING, item);
            if (newData != data) {
                prices.put(data.id, newData);
                priceUpdates.put(data.id, newData);
                historyUpdates.add(newData);
            }
        }
    }

    /**
     * Persists active prices and pending history snapshots.
     * <p>
     * JSON mode writes the full active price map to a temporary file and then replaces the real price file. SQL mode
     * delegates persistence to batch tasks. The SQL batch tasks consume pending entries from {@link #historyUpdates} and
     * {@link #priceUpdates} as they add them to their batches.
     *
     * @throws IOException If JSON persistence fails.
     */
    private void savePrices() throws IOException {
        if (world.getSerializerManager().isJson()) {
            Path tempPath = PRICE_PATH.resolveSibling(PRICE_PATH.getFileName() + ".tmp");
            GsonUtils.writeJson(new HashMap<>(prices), tempPath);
            try {
                Files.move(
                        tempPath,
                        PRICE_PATH,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempPath, PRICE_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            priceUpdates.clear();
            historyUpdates.clear();
        } else {
            var priceHistoryTask = new BatchPriceHistorySqlTask(world, historyUpdates);
            var priceUpdateTask = new BatchPriceUpdateSqlTask(world, priceUpdates.values());
            priceHistoryTask.run();
            priceUpdateTask.run();
        }
    }

    /**
     * Checks whether an observed value is close enough to a guide value to be trusted.
     * <p>
     * The accepted range is built around {@code targetValue}. Values below the configured underpay ratio or above the
     * configured overpay ratio are ignored as outliers.
     *
     * @param value The observed value.
     * @param targetValue The current guide value to compare against.
     * @return {@code true} if {@code value} is finite, positive, and inside the accepted range.
     */
    private boolean withinRange(double value, double targetValue) {
        if (!Double.isFinite(value) || !Double.isFinite(targetValue)) {
            return false;
        }
        if (value <= 0.0 || targetValue <= 0.0) {
            return false;
        }

        double minimumValue = targetValue * (1.0 - settings.maximumUnderpayRatio());
        double maximumValue = targetValue * (1.0 + settings.maximumOverpayRatio());
        return value >= minimumValue && value <= maximumValue;
    }
}