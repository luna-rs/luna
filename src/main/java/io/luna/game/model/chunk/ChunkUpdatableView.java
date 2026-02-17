package io.luna.game.model.chunk;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mob.Player;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Defines a visibility view for {@link ChunkUpdatable} types using an allow-list of viewers.
 * <p>
 * A {@link ChunkUpdatableView} answers one question: <i>“Which players are allowed to see this updatable?”</i>
 * It is used by the chunk update system to decide whether to enqueue show/hide/update messages for a specific player.
 * <p>
 * Two view modes exist:
 * <ul>
 *     <li><b>Global</b>: visible to all players.</li>
 *     <li><b>Local</b>: visible only to a specific player or a defined set of players.</li>
 * </ul>
 * <p>
 * <b>Global sentinel:</b> Global visibility is represented by a shared view {@link #GLOBAL_VIEW}.
 * <p>
 * <b>Reference retention:</b> {@link #localView(Set, boolean)} optionally retains a reference to the provided
 * {@code players} set. When {@code retainReference} is true, changes to the original set are reflected in this view.
 * This is useful for dynamic groups, but it also means the caller is responsible for thread-safety and for ensuring
 * the set remains valid for the lifetime of the view.
 *
 * @author lare96
 */
public final class ChunkUpdatableView implements Iterable<Player> {

    /**
     * Constant view backed by an empty {@link ImmutableSet} used to represent global visibility.
     * <p>
     * This is intentionally an empty set. Global checks use reference equality, so this constant must be the exact
     * instance used by global views.
     */
    private static final ChunkUpdatableView GLOBAL_VIEW = new ChunkUpdatableView(Collections.EMPTY_SET);

    /**
     * Creates a {@link ChunkUpdatableView} representing a global view.
     * <p>
     * Any {@link ChunkUpdatable} using this view is visible to all players.
     *
     * @return A global view.
     */
    public static ChunkUpdatableView globalView() {
        return GLOBAL_VIEW;
    }

    /**
     * Creates a {@link ChunkUpdatableView} representing a local view for a single player.
     * <p>
     * Any {@link ChunkUpdatable} using this view is visible only to {@code player}.
     *
     * @param player The sole allowed viewer.
     * @return A local view.
     */
    public static ChunkUpdatableView localView(Player player) {
        return new ChunkUpdatableView(ImmutableSet.of(player));
    }

    /**
     * Creates a {@link ChunkUpdatableView} representing a local view for a set of players.
     * <p>
     * When {@code retainReference} is {@code true}, the returned view wraps {@code players} directly (as an
     * unmodifiable view). Mutations to {@code players} will be reflected in this view after this method returns.
     * <p>
     * When {@code retainReference} is {@code false}, the returned view stores an immutable copy of the provided set.
     *
     * @param players The allowed viewers.
     * @param retainReference Whether to retain a live reference to {@code players}.
     * @return A local view.
     */
    public static ChunkUpdatableView localView(Set<Player> players, boolean retainReference) {
        if (retainReference) {
            return new ChunkUpdatableView(Collections.unmodifiableSet(players));
        } else {
            return new ChunkUpdatableView(ImmutableSet.copyOf(players));
        }
    }

    /**
     * The set of allowed viewers for this view.
     * <p>
     * If this is empty, the view is global.
     */
    private final Set<Player> allowedViewers;

    /**
     * Creates a new {@link ChunkUpdatableView}.
     *
     * @param allowedViewers The allowed viewers set (an empty set for global visibility).
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
        return MoreObjects.toStringHelper(this)
                .addValue(isGlobal() ? "GLOBAL" : allowedViewers.toString())
                .toString();
    }

    @Override
    public Iterator<Player> iterator() {
        return allowedViewers.iterator();
    }

    /**
     * Returns {@code true} if this view is global (visible to all players).
     *
     * @return {@code true} if global.
     */
    public boolean isGlobal() {
        return allowedViewers.isEmpty();
    }

    /**
     * Returns {@code true} if the linked {@link ChunkUpdatable} should be viewable by {@code player}.
     *
     * @param player The player to test.
     * @return {@code true} if viewable for the player.
     */
    public boolean isViewableFor(Player player) {
        return allowedViewers.isEmpty() || allowedViewers.contains(player);
    }
}
