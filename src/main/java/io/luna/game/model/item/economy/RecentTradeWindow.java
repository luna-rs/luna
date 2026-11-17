package io.luna.game.model.item.economy;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Retains bounded admission history and recent coin-trade evidence for one commodity.
 * Ordinary and quarantined payments each have thirty-two slots, so outliers cannot evict ordinary sales.
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
     * The most recently admitted timestamp for a pair, including trades with only inferred prices.
     *
     * @author lare96
     */
    private record Admission(Pair pair, long timestamp) {
    }

    /**
     * Conservative sale evidence and the first time any contributing observation expires.
     *
     * @author lare96
     */
    record Reference(double price, int observations, int independentPairs, long lastObserved, long expiresAt) {
    }

    private final Observation[] observations = new Observation[CAPACITY];
    private final Observation[] quarantined = new Observation[CAPACITY];
    private final Admission[] admissions = new Admission[CAPACITY];
    private final Set<Pair> cyclePairs = new HashSet<>();
    private final Map<String, Integer> cycleActors = new HashMap<>();
    private long cycle = Long.MIN_VALUE;
    private long candidateCycle = Long.MIN_VALUE;
    private long candidateEstablishedAt;
    private double candidatePrice;
    private boolean extremeQualified;

    /**
     * Admits at most one payment per pair, two per participant and thirty-two per commodity in a service cycle.
     * Repeated, reversed, self, stale and future-dated trades cannot supply extra movement or independent evidence.
     */
    boolean record(String traderA, String traderB, double price, long timestamp, long now, long currentCycle) {
        return record(traderA, traderB, price, timestamp, now, currentCycle, false);
    }

    /**
     * Stores an admitted actual payment in its bounded channel without publishing unqualified outliers.
     */
    boolean record(String traderA, String traderB, double price, long timestamp, long now, long currentCycle,
                   boolean extreme) {
        if (!Double.isFinite(price) || price <= 0) return false;
        refreshExtreme(now);
        Pair pair = admitPair(traderA, traderB, timestamp, now, currentCycle);
        if (pair == null) return false;
        Observation[] target = extreme ? quarantined : observations;
        Observation[] other = extreme ? observations : quarantined;
        int slot = 0;
        long oldest = Long.MAX_VALUE;
        for (int index = 0; index < CAPACITY; index++) {
            if (other[index] != null && other[index].pair().equals(pair)) other[index] = null;
            Observation observation = target[index];
            if (observation != null && observation.pair().equals(pair)) {
                slot = index;
                oldest = Long.MIN_VALUE;
            } else if (observation == null && oldest != Long.MIN_VALUE) {
                slot = index;
                oldest = Long.MIN_VALUE;
            } else if (observation != null && observation.timestamp() < oldest) {
                slot = index;
                oldest = observation.timestamp();
            }
        }
        target[slot] = new Observation(pair, price, timestamp);
        refreshExtreme(now);
        return true;
    }

    /**
     * Applies the same participant, repetition and age limits to guide-inferred trade paths.
     * Admission alone never creates actual coin-price evidence.
     */
    boolean admit(String traderA, String traderB, long timestamp, long now, long currentCycle) {
        return admitPair(traderA, traderB, timestamp, now, currentCycle) != null;
    }

    /**
     * Admits one trade once, sharing a bounded timestamp history across every sample type.
     */
    private Pair admitPair(String traderA, String traderB, long timestamp, long now, long currentCycle) {
        String first = normalize(traderA);
        String second = normalize(traderB);
        if (first == null || second == null || first.equals(second) || !recent(timestamp, now)) {
            return null;
        }
        Pair pair = first.compareTo(second) < 0 ? new Pair(first, second) : new Pair(second, first);
        if (cycle != currentCycle) {
            cycle = currentCycle;
            cyclePairs.clear();
            cycleActors.clear();
        }
        if (cyclePairs.size() == CAPACITY || cyclePairs.contains(pair) ||
                cycleActors.getOrDefault(first, 0) >= 2 || cycleActors.getOrDefault(second, 0) >= 2) {
            return null;
        }
        int slot = 0;
        long oldest = Long.MAX_VALUE;
        for (int index = 0; index < CAPACITY; index++) {
            Admission admission = admissions[index];
            if (admission != null && admission.pair().equals(pair)) {
                if (timestamp <= admission.timestamp()) return null;
                slot = index;
                oldest = Long.MIN_VALUE;
            } else if (admission == null && oldest != Long.MIN_VALUE) {
                slot = index;
                oldest = Long.MIN_VALUE;
            } else if (admission != null && admission.timestamp() < oldest) {
                slot = index;
                oldest = admission.timestamp();
            }
        }
        for (int index = 0; index < CAPACITY * 2; index++) {
            Observation observation = observation(index);
            if (observation != null && observation.pair().equals(pair) && timestamp <= observation.timestamp()) {
                return null;
            }
        }
        admissions[slot] = new Admission(pair, timestamp);
        cyclePairs.add(pair);
        cycleActors.merge(first, 1, Integer::sum);
        cycleActors.merge(second, 1, Integer::sum);
        return pair;
    }

    /**
     * Allows a distant payment only after three disjoint trader pairs agree and that cohort is a window majority.
     */
    boolean corroborates(double price, long now) {
        refreshExtreme(now);
        return corroborates(price, now, true);
    }

    /**
     * Qualifies an extreme regime only after a full cohort and a newer payment in a later service cycle.
     * This is called only after the current transaction was admitted. Waiting on an old batch cannot qualify it.
     */
    boolean corroboratesExtreme(double price, long timestamp, long now, long currentCycle) {
        refreshExtreme(now);
        double[] prices = new double[CAPACITY * 2];
        int count = 0;
        for (int index = 0; index < CAPACITY * 2; index++) {
            Observation observation = observation(index);
            if (observation != null && recent(observation.timestamp(), now)) prices[count++] = observation.price();
        }
        if (count == 0) return false;
        Arrays.sort(prices, 0, count);
        double median = prices[(count - 1) / 2];
        if (!consistent(price, median) || !corroborates(median, now, false)) return false;
        if (candidateCycle == Long.MIN_VALUE || !consistent(median, candidatePrice)) {
            candidatePrice = median;
            candidateCycle = currentCycle;
            candidateEstablishedAt = now;
            extremeQualified = false;
        } else if (currentCycle > candidateCycle && timestamp > candidateEstablishedAt && consistent(price, candidatePrice)) {
            extremeQualified = true;
        }
        return extremeQualified && consistent(price, candidatePrice);
    }

    /**
     * Requires three disjoint agreeing pairs and a strict majority of the selected real-payment evidence.
     */
    private boolean corroborates(double price, long now, boolean qualifiedOnly) {
        int total = 0;
        int matching = 0;
        Set<String> independent = new HashSet<>();
        int pairs = 0;
        for (int index = 0; index < CAPACITY * 2; index++) {
            Observation observation = observation(index);
            if (observation == null || !recent(observation.timestamp(), now)) continue;
            if (qualifiedOnly && !referenceEligible(index, observation)) continue;
            total++;
            if (!consistent(observation.price(), price)) continue;
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
        refreshExtreme(now);
        double[] prices = new double[CAPACITY * 2];
        int count = 0;
        int pairs = 0;
        long latest = 0;
        long earliest = Long.MAX_VALUE;
        Set<String> independent = new HashSet<>();
        for (int index = 0; index < CAPACITY * 2; index++) {
            Observation observation = observation(index);
            if (observation == null || !recent(observation.timestamp(), now)) continue;
            if (!referenceEligible(index, observation)) continue;
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
        if (!corroborates(prices[(count - 1) / 2], now, true)) return null;
        return new Reference(prices[(count - 1) / 4], count, pairs, latest, earliest + MAX_AGE_MILLIS);
    }

    /**
     * Invalidates qualified support as soon as its cohort expires or loses majority agreement.
     */
    private void refreshExtreme(long now) {
        if (candidateCycle != Long.MIN_VALUE && !corroborates(candidatePrice, now, false)) {
            candidateCycle = Long.MIN_VALUE;
            candidateEstablishedAt = 0;
            candidatePrice = 0;
            extremeQualified = false;
        }
    }

    /**
     * Ordinary payments remain eligible; quarantined payments require the qualified consistent cohort.
     */
    private boolean referenceEligible(int index, Observation observation) {
        return index < CAPACITY || (extremeQualified && consistent(observation.price(), candidatePrice));
    }

    /**
     * Reads either bounded channel without merging or exposing its mutable storage.
     */
    private Observation observation(int index) {
        return index < CAPACITY ? observations[index] : quarantined[index - CAPACITY];
    }

    /**
     * Uses the existing symmetric eighty-to-one-hundred-twenty-five percent consistency band.
     */
    private static boolean consistent(double price, double target) {
        return price >= target * 0.8 && price <= target * 1.25;
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
