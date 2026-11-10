package io.luna.game.model.item.economy;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Retains a fixed amount of recent coin-trade evidence from distinct trader pairs for one commodity.
 * Windows are owned by the economy service and intentionally restart empty; persisted guides remain authoritative.
 *
 * @author lare96
 */
final class RecentTradeWindow {

    static final int CAPACITY = 32;
    static final long MAX_AGE_MILLIS = Duration.ofHours(24).toMillis();

    /**
     * A normalized, direction-independent pair of actual traders.
     *
     * @author lare96
     */
    private record Pair(String first, String second) {
    }

    /**
     * One actual payment, with at most one retained observation for each trader pair.
     *
     * @author lare96
     */
    private record Observation(Pair pair, double price, long timestamp) {
    }

    /**
     * Conservative sale evidence and the first time any contributing observation expires.
     *
     * @author lare96
     */
    record Reference(double price, int observations, int independentPairs, long lastObserved, long expiresAt) {
    }

    private final Observation[] observations = new Observation[CAPACITY];
    private final Set<Pair> cyclePairs = new HashSet<>();
    private final Map<String, Integer> cycleActors = new HashMap<>();
    private long cycle = Long.MIN_VALUE;

    /**
     * Admits at most one payment per pair, two per participant and thirty-two per commodity in a service cycle.
     * Repeated, reversed, self, stale and future-dated trades cannot supply extra movement or independent evidence.
     */
    boolean record(String traderA, String traderB, double price, long timestamp, long now, long currentCycle) {
        String first = normalize(traderA);
        String second = normalize(traderB);
        if (first == null || second == null || first.equals(second) || !Double.isFinite(price) || price <= 0 ||
                !recent(timestamp, now)) {
            return false;
        }
        Pair pair = first.compareTo(second) < 0 ? new Pair(first, second) : new Pair(second, first);
        if (cycle != currentCycle) {
            cycle = currentCycle;
            cyclePairs.clear();
            cycleActors.clear();
        }
        if (cyclePairs.size() == CAPACITY || cyclePairs.contains(pair) ||
                cycleActors.getOrDefault(first, 0) >= 2 || cycleActors.getOrDefault(second, 0) >= 2) {
            return false;
        }
        int slot = 0;
        long oldest = Long.MAX_VALUE;
        for (int index = 0; index < CAPACITY; index++) {
            Observation observation = observations[index];
            if (observation != null && observation.pair().equals(pair)) {
                if (timestamp <= observation.timestamp()) return false;
                slot = index;
                break;
            }
            if (observation == null) {
                slot = index;
                oldest = Long.MIN_VALUE;
            } else if (observation.timestamp() < oldest) {
                slot = index;
                oldest = observation.timestamp();
            }
        }
        observations[slot] = new Observation(pair, price, timestamp);
        cyclePairs.add(pair);
        cycleActors.merge(first, 1, Integer::sum);
        cycleActors.merge(second, 1, Integer::sum);
        return true;
    }

    /**
     * Allows a distant payment only after three disjoint trader pairs agree and that cohort is a window majority.
     */
    boolean corroborates(double price, long now) {
        int total = 0;
        int matching = 0;
        Set<String> independent = new HashSet<>();
        int pairs = 0;
        for (Observation observation : observations) {
            if (observation == null || !recent(observation.timestamp(), now)) continue;
            total++;
            if (observation.price() < price * 0.8 || observation.price() > price * 1.25) continue;
            matching++;
            Pair pair = observation.pair();
            if (!independent.contains(pair.first()) && !independent.contains(pair.second())) {
                independent.add(pair.first());
                independent.add(pair.second());
                pairs++;
            }
        }
        return pairs >= 3 && matching * 2 > total;
    }

    /**
     * Returns the lower quartile of recent real payments only when three disjoint pairs support a sale market.
     */
    Reference reference(long now) {
        double[] prices = new double[CAPACITY];
        int count = 0;
        int pairs = 0;
        long latest = 0;
        long earliest = Long.MAX_VALUE;
        Set<String> independent = new HashSet<>();
        for (Observation observation : observations) {
            if (observation == null || !recent(observation.timestamp(), now)) continue;
            prices[count++] = observation.price();
            latest = Math.max(latest, observation.timestamp());
            earliest = Math.min(earliest, observation.timestamp());
            Pair pair = observation.pair();
            if (!independent.contains(pair.first()) && !independent.contains(pair.second())) {
                independent.add(pair.first());
                independent.add(pair.second());
                pairs++;
            }
        }
        if (pairs < 3) return null;
        Arrays.sort(prices, 0, count);
        if (!corroborates(prices[(count - 1) / 2], now)) return null;
        return new Reference(prices[(count - 1) / 4], count, pairs, latest, earliest + MAX_AGE_MILLIS);
    }

    private static boolean recent(long timestamp, long now) {
        return timestamp > 0 && timestamp <= now && now - timestamp < MAX_AGE_MILLIS;
    }

    private static String normalize(String name) {
        if (name == null) return null;
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() || normalized.length() > 32 ? null : normalized;
    }
}
