package io.luna.game.model.item.economy;

import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Derives ordinary net commodity quantities without modifying the accepted exchange or its receipts.
 * Notes and inventory-slot layouts describe the same commodity; stateful variants have no ordinary guide sample.
 *
 * @author lare96
 */
record NormalizedTrade(List<NormalizedTrade.Commodity> sideA, List<NormalizedTrade.Commodity> sideB) {

    /**
     * A net ordinary commodity quantity, which can exceed one integer-sized inventory stack.
     *
     * @author lare96
     */
    record Commodity(int id, long amount) {
    }

    /**
     * Returns the net exchange, or {@code null} when any component cannot be priced as an ordinary commodity.
     * Excluding the whole exchange avoids attributing payment for a custom item to its ordinary companions.
     */
    static NormalizedTrade from(CompletedTradeData trade) {
        Map<Integer, Long> quantities = new TreeMap<>();
        if (!accumulate(trade.getSideA(), quantities, 1) || !accumulate(trade.getSideB(), quantities, -1)) {
            return null;
        }
        List<Commodity> sideA = new ArrayList<>();
        List<Commodity> sideB = new ArrayList<>();
        for (var entry : quantities.entrySet()) {
            long amount = entry.getValue();
            if (amount > 0) sideA.add(new Commodity(entry.getKey(), amount));
            else if (amount < 0) sideB.add(new Commodity(entry.getKey(), Math.negateExact(amount)));
        }
        return new NormalizedTrade(List.copyOf(sideA), List.copyOf(sideB));
    }

    /**
     * Accumulates checked signed quantities only after validating the complete ordinary item identity.
     */
    private static boolean accumulate(List<Item> items, Map<Integer, Long> quantities, int direction) {
        for (Item item : items) {
            if (item == null || item.isDynamic() || item.getAmount() <= 0) return false;
            var definition = ItemDefinition.ALL.get(item.getId()).orElse(null);
            if (definition == null || (definition.isNoted() && definition.getUnnotedId().isEmpty())) return false;
            int id = definition.getUnnotedId().orElse(item.getId());
            var base = ItemDefinition.ALL.get(id).orElse(null);
            if (base == null || base.isNoted() || (id != 995 && (!definition.isTradeable() || !base.isTradeable()))) {
                return false;
            }
            long amount = (long) item.getAmount() * direction;
            quantities.put(id, Math.addExact(quantities.getOrDefault(id, 0L), amount));
        }
        return true;
    }
}
