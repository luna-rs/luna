package io.luna.game.model.mob.block;

import com.google.common.collect.AbstractIterator;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.MobReference;
import io.luna.game.model.mob.Player;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A non-owning view of the player sessions observing an NPC or bot.
 * Only session handles are stored; iteration resolves players still registered in their original world slots.
 * Update workers may add or remove observers concurrently, and expired handles are discarded when read.
 *
 * @author lare96
 */
public final class LocalHumanView implements Iterable<Player> {

    /**
     * Exact session handles, shared with the players rather than allocated for each visibility relationship.
     */
    private final Set<MobReference> observers = ConcurrentHashMap.newKeySet();

    /**
     * Adds a registered player session to this view.
     *
     * @param player The observing player.
     * @return Whether the session was newly added.
     */
    public boolean add(Player player) {
        MobReference reference = MobReference.capture(player);
        return reference != null && observers.add(reference);
    }

    /**
     * Removes the exact observing session without affecting another login with the same username.
     *
     * @param player The player leaving the view.
     * @return Whether its session handle was removed.
     */
    public boolean remove(Player player) {
        MobReference reference = MobReference.capture(player);
        return reference != null && observers.remove(reference);
    }

    /**
     * Returns whether this view has no live observers.
     *
     * @return Whether no stored handle resolves to a player.
     */
    public boolean isEmpty() {
        for (MobReference reference : observers) {
            if (reference.resolve() instanceof Player) {
                return false;
            }
            observers.remove(reference);
        }
        return true;
    }

    /**
     * Returns whether this view has a live observer.
     *
     * @return Whether a stored handle resolves to a player.
     */
    public boolean isNotEmpty() {
        return !isEmpty();
    }

    /**
     * Counts the currently registered observing sessions.
     *
     * @return The number of live players in this view.
     */
    public int size() {
        int size = 0;
        for (Player ignored : this) {
            size++;
        }
        return size;
    }

    /**
     * Drops every observer handle from this view.
     */
    public void clear() {
        observers.clear();
    }

    @Override
    public Iterator<Player> iterator() {
        Iterator<MobReference> references = observers.iterator();
        return new AbstractIterator<>() {
            @Override
            protected Player computeNext() {
                while (references.hasNext()) {
                    MobReference reference = references.next();
                    Mob mob = reference.resolve();
                    if (mob instanceof Player player) {
                        return player;
                    }
                    observers.remove(reference);
                }
                return endOfData();
            }
        };
    }
}
