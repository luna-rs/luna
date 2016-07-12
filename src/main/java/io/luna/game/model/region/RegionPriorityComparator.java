package io.luna.game.model.region;

import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Player;

import java.util.Comparator;

/**
 * A {@link Comparator} implementation that compares {@link MobileEntity}s being added to the local lists of {@link Player}s.
 * The purpose of this is to prevent the loss of functionality when staggering updating.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionPriorityComparator implements Comparator<MobileEntity> {

    /**
     * The {@link Player} being updated.
     */
    private final Player player;

    /**
     * Creates a new {@link RegionPriorityComparator}.
     *
     * @param player The {@link Player} being updated.
     */
    public RegionPriorityComparator(Player player) {
        this.player = player;
    }

    @Override
    public int compare(MobileEntity left, MobileEntity right) {
        int leftMob = 0;
        int rightMob = 0;
        return Integer.compare(leftMob, rightMob);
    }

}
