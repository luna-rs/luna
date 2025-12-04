package io.luna.game.model.mob.block;

import com.google.common.collect.Sets;
import io.luna.game.model.World;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.bot.Bot;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Maintains the set of local mobs around a given owner.
 * <p>
 * This repository separates two related concepts:
 * </p>
 * <ul>
 *     <li>
 *         <b>Updating view</b> – the ordered collections used by the updating threads when encoding player and
 *         NPC update blocks. These are stored in {@link #updatingPlayers} and {@link #updatingNpcs} and are only
 *         ever mutated from an updating thread.
 *     </li>
 *     <li>
 *         <b>Local view</b> – the thread-safe collections that represent which players and NPCs are currently
 *         visible. These are stored in {@link #localPlayers} and {@link #localNpcs}, and can be read safely from any
 *         thread.
 *     </li>
 * </ul>
 * <p>
 * The general pattern is:
 * </p>
 * <ol>
 *     <li>When a mob enters view, {@link #add(Mob)} is called from the updating thread to populate both the updating
 *     view and the local view sets.</li>
 *     <li>The updating thread iterates and prunes {@link #updatingPlayers} and {@link #updatingNpcs} via
 *     {@link #forUpdatingPlayers(Predicate)} and {@link #forUpdatingNpcs(Predicate)}.</li>
 *     <li>When a mob should leave the local view, {@link #removeLocal(Mob)} removes it from the thread-safe
 *     local sets while the updating view is cleaned up via {@code removeIf} in the iteration methods.</li>
 * </ol>
 * <p>
 * All mutation methods enforce that they are only called from the updating thread via {@link #assertUpdatingThread()},
 * ensuring that non-thread-safe structures are not accessed concurrently.
 * </p>
 *
 * @author lare96
 */
public final class LocalMobRepository {

    /**
     * Players that are currently in the updating view.
     * <p>
     * This is an ordered set used by the updating threads when encoding player update blocks. It is <b>not</b>
     * thread-safe and must only be mutated from an updating thread, as enforced by {@link #assertUpdatingThread()}.
     * </p>
     */
    private final Set<Player> updatingPlayers = new LinkedHashSet<>(255);

    /**
     * NPCs that are currently in the updating view.
     * <p>
     * This is an ordered set used by the updating threads when encoding NPC update blocks. It is <b>not</b>
     * thread-safe and must only be mutated from an updating thread, as enforced by {@link #assertUpdatingThread()}.
     * </p>
     */
    private final Set<Npc> updatingNpcs = new LinkedHashSet<>(255);

    /**
     * The thread-safe set of local players visible to the owner.
     */
    private final Set<Player> localPlayers = Sets.newConcurrentHashSet();

    /**
     * The thread-safe set of local NPCs visible to the owner.
     */
    private final Set<Npc> localNpcs = Sets.newConcurrentHashSet();

    /**
     * The owning player.
     */
    private final Player player;

    /**
     * Creates a new {@link LocalMobRepository}.
     *
     * @param player The owning player.
     */
    public LocalMobRepository(Player player) {
        this.player = player;
    }

    /**
     * Adds a mob to both the updating view and the appropriate local view set.
     * <p>
     * <ul>
     *     <li>If the mob is a {@link Player}, it is added to {@link #updatingPlayers} and{@link #localPlayers}.</li>
     *     <li>If the mob is an {@link Npc}, it is added to {@link #updatingNpcs} and {@link #localNpcs}.</li>
     * </ul>
     * This method must only be called from an updating thread; otherwise an exception is thrown.
     *
     * @param mob The mob entering the local view.
     * @return {@code true} if the mob was successfully added to one of the backing update sets.
     */
    public boolean add(Mob mob) {
        assertUpdatingThread();
        if (mob instanceof Player) {
            Player otherPlayer = mob.asPlr();
            if(updatingPlayers.add(otherPlayer)) {
                localPlayers.add(otherPlayer);
                if (otherPlayer.isBot()) {
                    Bot otherBot = otherPlayer.asBot();
                    otherBot.getLocalHumans().add(player);
                }
                return true;
            }
        } else if (mob instanceof Npc) {
            Npc npc = mob.asNpc();
            if(updatingNpcs.add(npc)) {
                localNpcs.add(npc);
                npc.getLocalHumans().add(player);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes a mob from the thread-safe local view sets.
     * <p>
     * This method intentionally does <b>not</b> modify {@link #updatingPlayers} or {@link #updatingNpcs}. Those
     * collections are pruned by the updating thread via {@link #forUpdatingPlayers(Predicate)} and
     * {@link #forUpdatingNpcs(Predicate)}, which call {@code removeIf} on the backing sets.
     * <p>
     * Effectively:
     * <ul>
     *     <li>Players are removed from {@link #localPlayers}.</li>
     *     <li>NPCs are removed from {@link #localNpcs}.</li>
     * </ul>
     * This method must only be called from an updating thread.
     *
     * @param mob The mob leaving the local view.
     */
    public void removeLocal(Mob mob) {
        assertUpdatingThread();
        if (mob instanceof Player) {
            Player otherPlayer = mob.asPlr();
            localPlayers.remove(otherPlayer);
            if (otherPlayer.isBot()) {
                Bot otherBot = otherPlayer.asBot();
                otherBot.getLocalHumans().remove(player);
            }
        } else if (mob instanceof Npc) {
            Npc npc = mob.asNpc();
            localNpcs.remove(npc);
            npc.getLocalHumans().remove(player);
        }
    }

    /**
     * Determines if the given mob is currently in the thread-safe local view.
     * <p>
     * This method is safe to call from any thread, as it only reads from concurrent sets: {@link #localPlayers} and
     * {@link #localNpcs}.
     * </p>
     *
     * @param mob The mob to check.
     * @return {@code true} if the mob is currently visible; otherwise {@code false}.
     */
    public boolean inLocalView(Mob mob) {
        if (mob instanceof Player) {
            return localPlayers.contains(mob.asPlr());
        } else if (mob instanceof Npc) {
            return localNpcs.contains(mob.asNpc());
        }
        return false;
    }

    /**
     * Determines if the given mob is currently part of the updating view.
     * <p>
     * This method checks {@link #updatingPlayers} or {@link #updatingNpcs}, depending on the mob type, and must
     * only be called from an updating thread. It is mainly useful inside the update pipeline to verify whether a mob
     * will be processed during the current update cycle.
     * </p>
     *
     * @param mob The mob to check.
     * @return {@code true} if the mob is present in the updating view; otherwise {@code false}.
     */
    public boolean inUpdatingView(Mob mob) {
        assertUpdatingThread();
        if (mob instanceof Player) {
            return updatingPlayers.contains(mob.asPlr());
        } else if (mob instanceof Npc) {
            return updatingNpcs.contains(mob.asNpc());
        }
        return false;
    }

    /**
     * Applies the given action to all players in the local view.
     * <p>
     * This method iterates over {@link #localPlayers}, which is thread-safe for concurrent reads and writes.
     * </p>
     *
     * @param action The operation to perform on each local player.
     */
    public void forLocalPlayers(Consumer<Player> action) {
        localPlayers.forEach(action);
    }

    /**
     * Applies the given action to all NPCs in the local view.
     * <p>
     * This method iterates over {@link #localNpcs}, which is thread-safe for concurrent reads and writes.
     * </p>
     *
     * @param action The operation to perform on each local NPC.
     */
    public void forLocalNpcs(Consumer<Npc> action) {
        localNpcs.forEach(action);
    }

    /**
     * Iterates over all players in the updating view and removes any for which the supplied predicate returns
     * {@code true}.
     * <p>
     * This method is a thin wrapper around {@link Set#removeIf(Predicate)} for {@link #updatingPlayers}. It must only
     * be invoked from an updating thread. The predicate:
     * </p>
     * <ul>
     *     <li><b>must not</b> directly modify {@link #updatingPlayers} or {@link #updatingNpcs},</li>
     *     <li><b>may</b> safely call {@link #removeLocal(Mob)} to update the local view.</li>
     * </ul>
     *
     * @param action A predicate that returns {@code true} for players that should be removed from the updating view.
     */
    public void forUpdatingPlayers(Predicate<Player> action) {
        assertUpdatingThread();
        updatingPlayers.removeIf(action);
    }

    /**
     * Iterates over all NPCs in the updating view and removes any for which
     * the supplied predicate returns {@code true}.
     * <p>
     * This method is a thin wrapper around {@link Set#removeIf(Predicate)} for {@link #updatingNpcs}. It must only be
     * invoked from an updating thread. The predicate should follow the same rules as documented in
     * {@link #forUpdatingPlayers(Predicate)}.
     * </p>
     *
     * @param action A predicate that returns {@code true} for NPCs that should be removed from the updating view.
     */
    public void forUpdatingNpcs(Predicate<Npc> action) {
        assertUpdatingThread();
        updatingNpcs.removeIf(action);
    }

    /**
     * Returns a {@link Stream} view of all players in the local view.
     *
     * @return A stream of local {@link Player} instances.
     */
    public Stream<Player> localPlayers() {
        return localPlayers.stream();
    }

    /**
     * Returns the number of players currently in the local view.
     *
     * @return The size of the {@link #localPlayers} set.
     */
    public int localPlayersCount() {
        return localPlayers.size();
    }

    /**
     * Returns a {@link Stream} view of all NPCs in the local view.
     *
     * @return A stream of local {@link Npc} instances.
     */
    public Stream<Npc> localNpcs() {
        return localNpcs.stream();
    }

    /**
     * Returns the number of NPCs currently in the local view.
     *
     * @return The size of the {@link #localNpcs} set.
     */
    public int localNpcsCount() {
        return localNpcs.size();
    }

    /**
     * Returns the number of players currently in the updating view.
     *
     * @return The size of the {@link #updatingPlayers} set.
     */
    public int updatingPlayersCount() {
        assertUpdatingThread();
        return updatingPlayers.size();
    }

    /**
     * Returns the number of NPCs currently in the updating view.
     *
     * @return The size of the {@link #updatingNpcs} set.
     */
    public int updatingNpcsCount() {
        assertUpdatingThread();
        return updatingNpcs.size();
    }

    /**
     * Ensures that the current thread is an updating thread.
     * <p>
     * All mutation methods in this repository must be called only from updating threads. If this condition is
     * violated, an {@link IllegalStateException} is thrown to highlight incorrect usage early.
     * </p>
     */
    private void assertUpdatingThread() {
        if (!World.isUpdatingThread()) {
            throw new IllegalStateException("LocalMobRepository mutation from non-updating thread.");
        }
    }
}
