package io.luna.game.model.chunk;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mob.Player;

import java.util.Collections;
import java.util.Set;

/**
 * Defines a view for {@link ChunkUpdatable} types consisting of a set of viewers.
 *
 * @author lare96
 */
public final class ChunkUpdatableView {

    /**
     * Represents a global view.
     */
    public static final Set<Player> GLOBAL = Collections.EMPTY_SET;

    /**
     * Returns a {@link ChunkUpdatableView} representing a global view. Any {@link ChunkUpdatable} using this view
     * will be visible to all players.
     */
    public static ChunkUpdatableView globalView() {
        return new ChunkUpdatableView(GLOBAL);
    }

    /**
     * Returns a {@link ChunkUpdatableView} representing a local view for {@code player}. Any {@link ChunkUpdatable} using this view
     * will be visible only to {@code player}.
     */
    public static ChunkUpdatableView localView(Player player) {
        return new ChunkUpdatableView(ImmutableSet.of(player));
    }

    /**
     * Returns a {@link ChunkUpdatableView} representing a local view. Any {@link ChunkUpdatable} using this view
     * will be visible only to the set of {@code players}.
     * <p>
     * {@code retainReferences} being {@code true} synchronizes this view with {@code players}, meaning changes to that
     * set will reflect within this view even after this function completes. Otherwise, a new internal copy is created.
     */
    public static ChunkUpdatableView localView(Set<Player> players, boolean retainReference) {
        if (retainReference) {
            return new ChunkUpdatableView(Collections.unmodifiableSet(players));
        } else {
            return new ChunkUpdatableView(ImmutableSet.copyOf(players));
        }
    }

    /**
     * The viewers.
     */
    private final Set<Player> allowedViewers;

    /**
     * Creates a new {@link ChunkUpdatableView}.
     *
     * @param allowedViewers The viewers.
     */
    private ChunkUpdatableView(Set<Player> allowedViewers) {
        this.allowedViewers = allowedViewers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkUpdatableView)) return false;
        ChunkUpdatableView that = (ChunkUpdatableView) o;
        return Objects.equal(allowedViewers, that.allowedViewers);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(allowedViewers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).addValue(isGlobal() ? "GLOBAL" : allowedViewers.toString()).toString();
    }

    /**
     * @return {@code true} if this view is global.
     */
    public boolean isGlobal() {
        return allowedViewers == GLOBAL;
    }

    /**
     * Determines if the {@link ChunkUpdatable} linked to this view will be viewable for {@code player}.
     *
     * @param player The player to check.
     * @return {@code true} if this is viewable for {@code player}.
     */
    public boolean isViewableFor(Player player) {
        return allowedViewers == GLOBAL || allowedViewers.contains(player);
    }

    /**
     * @return The viewers.
     */
    public Set<Player> getAllowedViewers() {
        return allowedViewers;
    }
}
