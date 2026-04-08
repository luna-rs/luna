package io.luna.game.model;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * Comparator that orders {@link Locatable} instances by increasing distance from a fixed base location.
 * <p>
 * Distance is measured using {@link Position#computeLongestDistance(Position)} from the absolute position of the base
 * locatable supplied at construction time.
 * <p>
 * When two locatables are the same distance from the base, their absolute coordinates are used as tie-breakers.
 * If they still compare equally, a final reference-identity-based tie-breaker is applied so distinct objects are less
 * likely to collapse when used in sorted collections such as {@link TreeSet}.
 *
 * @author lare96
 */
public final class LocatableDistanceComparator implements Comparator<Locatable> {

    /**
     * The absolute base position used as the distance origin.
     */
    private final Position abs;

    /**
     * Creates a new {@link LocatableDistanceComparator} using {@code locatable} as the distance origin.
     *
     * @param locatable The locatable whose absolute position will be used as the base for all distance comparisons.
     */
    public LocatableDistanceComparator(Locatable locatable) {
        abs = locatable.abs();
    }

    @Override
    public int compare(Locatable o1, Locatable o2) {
        if (o1 == o2) {
            return 0;
        }

        Position abs1 = o1.abs();
        Position abs2 = o2.abs();

        int distance1 = abs.computeLongestDistance(abs1);
        int distance2 = abs.computeLongestDistance(abs2);
        int distanceCompare = Integer.compare(distance1, distance2);
        if (distanceCompare != 0) {
            return distanceCompare;
        }

        int xCompare = Integer.compare(abs1.getX(), abs2.getX());
        if (xCompare != 0) {
            return xCompare;
        }

        int yCompare = Integer.compare(abs1.getY(), abs2.getY());
        if (yCompare != 0) {
            return yCompare;
        }

        int zCompare = Integer.compare(abs1.getZ(), abs2.getZ());
        if (zCompare != 0) {
            return zCompare;
        }

        return Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
    }
}