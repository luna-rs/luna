package io.luna.game.model.chunk;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.EntityType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

import java.util.Comparator;
import java.util.function.BiFunction;

/**
 * A {@link Comparator} implementation that ensures that important mobs are added to the local list of a
 * player before other mobs.
 *
 * @author lare96
 */
public final class ChunkMobComparator implements Comparator<Mob> {

    /**
     * Represents the data that will be used to compare the left and right mobs.
     *
     * @author lare96
     */
    private static final class ComparableFactorData {

        /**
         * The data for the left mob.
         */
        private final int left;

        /**
         * The data for the right mob.
         */
        private final int right;

        /**
         * Creates a new {@link ComparableFactorData}.
         *
         * @param left The data for the left mob.
         * @param right The data for the right mob.
         */
        private ComparableFactorData(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    /**
     * A makeshift type alias for the {@link BiFunction} type declaration.
     *
     * @author lare96
     */
    @FunctionalInterface
    private interface ComparableFactor extends BiFunction<Mob, Mob, ComparableFactorData> {

    }

    /**
     * An immutable list of factors used to compare mobs.
     */
    private final ImmutableList<ComparableFactor> factors = ImmutableList.of(
            this::comparePosition,
            this::compareFriends,
            this::compareSize,
            this::compareCombatLevel,
            this::compareCombat
    );

    /**
     * The player to compare mobs for.
     */
    private final Player player;

    /**
     * Creates a new {@link ChunkMobComparator}.
     *
     * @param player The player to compare mobs for.
     */
    public ChunkMobComparator(Player player) {
        this.player = player;
    }

    @Override
    public int compare(Mob left, Mob right) {
        int leftWeight = 0;
        int rightWeight = 0;

        // Compare all factors and award weight.
        for (ComparableFactor factor : factors) {
            ComparableFactorData data = factor.apply(left, right);
            leftWeight += data.left;
            rightWeight += data.right;
        }

        // The weight is equal, meaning equal importance. Just let left win.
        if (leftWeight == rightWeight) {
            leftWeight++;
        }

        // Compare weight to determine which entity wins priority.
        return Integer.compare(leftWeight, rightWeight);
    }

    /**
     * Compare mob combat levels. Awards {@code 2} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private ComparableFactorData compareCombatLevel(Mob left, Mob right) {
        int leftCombatLevel = left.getCombatLevel();
        int rightCombatLevel = right.getCombatLevel();
        return computeWeightFactor(leftCombatLevel, rightCombatLevel, 2);
    }

    /**
     * Compare mob sizes. Awards {@code 3} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private ComparableFactorData compareSize(Mob left, Mob right) {
        int leftSize = left.size();
        int rightSize = right.size();
        return computeWeightFactor(leftSize, rightSize, 3);
    }

    /**
     * Compare mob positions. Awards {@code 3} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private ComparableFactorData comparePosition(Mob left, Mob right) {
        int leftDistance = player.computeLongestDistance(left);
        int rightDistance = player.computeLongestDistance(right);
        return computeWeightFactor(leftDistance, rightDistance, 3);
    }

    /**
     * Compares Player friends' lists. Awards {@code 4} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private ComparableFactorData compareFriends(Mob left, Mob right) {
        int leftFactor = isFriend(left) ? 1 : 0;
        int rightFactor = isFriend(right) ? 1 : 0;
        return computeWeightFactor(leftFactor, rightFactor, 4);
    }

    /**
     * Determines if {@code mob} is on {@code player}'s friend list.
     *
     * @param mob The mob.
     * @return {@code true} if the mob is on the friend's list.
     */
    private boolean isFriend(Mob mob) {
        if (mob.getType() == EntityType.PLAYER) {
            long hash = mob.asPlr().getUsernameHash();
            return player.getFriends().contains(hash);
        }
        return false;
    }

    /**
     * Compare mob combat states. Awards {@code 5} weight.
     *
     * @param left The left mob.
     * @param right The right mob.
     * @return The factor values and weight for each mob.
     */
    private ComparableFactorData compareCombat(Mob left, Mob right) { /* TODO finish when combat is done */
        int leftCombat = /* left.inCombat() ? 1 :*/ 0;
        int rightCombat = /* right.inCombat() ? 1 :*/ 0;
        return computeWeightFactor(leftCombat, rightCombat, 5);
    }

    /**
     * Compares the factor values and awards weight based on a which one is greater.
     *
     * @param left The left factor value.
     * @param right The right factor value.
     * @param weight The amount of weight to award.
     * @return The factor values and weight for each mob.
     */
    private ComparableFactorData computeWeightFactor(int left, int right, int weight) {
        if (left > right) {
            return new ComparableFactorData(weight, 1);
        } else if (right > left) {
            return new ComparableFactorData(1, weight);
        } else {
            return new ComparableFactorData(1, 1);
        }
    }
}
