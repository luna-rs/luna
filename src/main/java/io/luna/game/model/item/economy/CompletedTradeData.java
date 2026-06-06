package io.luna.game.model.item.economy;

import io.luna.game.model.item.Item;
import io.luna.game.model.item.ItemContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a raw completed trade between two traders.
 * <p>
 * A raw trade entry stores the exact item stacks offered by each side at the moment the trade was recorded. It does not
 * directly decide item prices. Instead, economy update logic can later inspect fields to derive price samples.
 *
 * @author lare96
 */
public final class CompletedTradeData {

    /**
     * The wall-clock time this trade was recorded.
     */
    private final long timestamp;

    /**
     * The name of the first trader.
     */
    private final String traderA;

    /**
     * The name of the second trader.
     */
    private final String traderB;

    /**
     * The item stacks offered by {@code traderA}.
     */
    private final List<Item> sideA;

    /**
     * The item stacks offered by {@code traderB}.
     */
    private final List<Item> sideB;

    /**
     * Creates a new {@link CompletedTradeData}.
     * <p>
     * Both containers are copied into regular item lists immediately. Null container slots are ignored, preserving only
     * the actual item stacks involved in the trade.
     *
     * @param traderA The name or identifier of the first trader.
     * @param traderB The name or identifier of the second trader.
     * @param containerA The accepted trade container belonging to {@code traderA}.
     * @param containerB The accepted trade container belonging to {@code traderB}.
     */
    public CompletedTradeData(String traderA, String traderB, ItemContainer containerA, ItemContainer containerB) {
        this.traderA = traderA;
        this.traderB = traderB;
        timestamp = System.currentTimeMillis();
        sideA = buildItems(containerA);
        sideB = buildItems(containerB);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompletedTradeData)) return false;
        CompletedTradeData that = (CompletedTradeData) o;
        return timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(timestamp);
    }

    /**
     * Copies all non-null items from {@code container} into a list.
     * <p>
     * This strips empty container slots while preserving the item stacks that were actually part of the trade.
     *
     * @param container The item container to copy from.
     * @return A list containing every non-null item stack in {@code container}.
     */
    private List<Item> buildItems(ItemContainer container) {
        List<Item> itemList = new ArrayList<>();
        for (Item item : container) {
            if (item != null) {
                itemList.add(item);
            }
        }
        return itemList;
    }

    /**
     * @return The trade timestamp in epoch milliseconds.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return The first trader username.
     */
    public String getTraderA() {
        return traderA;
    }

    /**
     * @return The second trader username.
     */
    public String getTraderB() {
        return traderB;
    }

    /**
     * @return The first trade side.
     */
    public List<Item> getSideA() {
        return sideA;
    }

    /**
     * @return The second trade side.
     */
    public List<Item> getSideB() {
        return sideB;
    }
}