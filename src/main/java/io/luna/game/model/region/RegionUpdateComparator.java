package io.luna.game.model.region;

import com.google.common.collect.ImmutableList;
import fj.P2;
import io.luna.LunaConstants;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

import java.util.Comparator;
import java.util.function.BiFunction;

import static fj.P.p;

/**
 * A comparator that ensures that important mobs are added to the local list of a player before other (less
 * important) mobs.
 *
 * @author lare96 <http://github.org/lare96>
 * @see LunaConstants#STAGGERED_UPDATING
 */
public final class RegionUpdateComparator implements Comparator<Mob> {

    // TODO Add comparisons for people on friend list.

    /**
     * A makeshift type alias for the {@link BiFunction} type declaration.
     */
    @FunctionalInterface
    private interface WeightFactor extends BiFunction<Mob, Mob, P2<Integer, Integer>> {

    }

    /**
     * An immutable list of factors used to compare mobs.
     */
    private final ImmutableList<WeightFactor> factors = ImmutableList
            .of(this::comparePosition, this::compareSize, this::compareCombatLevel, this::compareCombat);

    /**
     * The player to compare mobs for.
     */
    private final Player player;

    /**
     * Creates a new {@link RegionUpdateComparator}.
     *
     * @param player The player  to compare mobs for.
     */
    public RegionUpdateComparator(Player player) {
        this.player = player;
    }

    @Override
    public int compare(Mob left, Mob right) {
        int leftWeight = 0;
        int rightWeight = 0;

        // Compare all factors and award weight.
        for (WeightFactor factor : factors) {
            P2<Integer, Integer> tuple = factor.apply(left, right);
            leftWeight += tuple._1();
            rightWeight += tuple._2();
        }

        // The weight is equal, meaning equal importance. Just let left win.
        if (leftWeight == rightWeight) {
            leftWeight++;
        }

        // Compare weight to determine which entity wins priority.
        return Integer.compare(leftWeight, rightWeight);
    }

    /**
     * Compare mob combat levels. Awards {@code 1} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private P2<Integer, Integer> compareCombatLevel(Mob left, Mob right) {
        int leftCombatLevel = left.getCombatLevel();
        int rightCombatLevel = right.getCombatLevel();
        return computeWeightFactor(leftCombatLevel, rightCombatLevel, 1);
    }


    /**
     * Compare mob sizes. Awards {@code 2} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private P2<Integer, Integer> compareSize(Mob left, Mob right) {
        int leftSize = left.size();
        int rightSize = right.size();
        return computeWeightFactor(leftSize, rightSize, 2);
    }

    /**
     * Compare mob positions. Awards {@code 3} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private P2<Integer, Integer> comparePosition(Mob left, Mob right) {
        int leftDistance = player.distanceFrom(left);
        int rightDistance = player.distanceFrom(right);
        return computeWeightFactor(leftDistance, rightDistance, 3);
    }

    /**
     * Compare mob combat states. Awards {@code 4} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private P2<Integer, Integer> compareCombat(Mob left, Mob right) { /* TODO finish when combat is done */
        int leftCombat = /* left.inCombat() ? 1 :*/ 0;
        int rightCombat = /* right.inCombat() ? 1 :*/ 0;
        return computeWeightFactor(leftCombat, rightCombat, 4);
    }

    /**
     * Compares the factor values and awards weight based on a which one is greater.
     *
     * @param left The left factor value.
     * @param right The right factor value.
     * @param weight The amount of weight to award.
     * @return The factor values and weight for each mob.
     */
    private P2<Integer, Integer> computeWeightFactor(int left, int right, int weight) {
        if (left > right) {
            return p(weight, 0);
        } else if (right > left) {
            return p(0, weight);
        } else {
            return p(weight, weight);
        }
    }
}
