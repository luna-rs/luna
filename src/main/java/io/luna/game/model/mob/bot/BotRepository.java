package io.luna.game.model.mob.bot;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import io.luna.game.model.World;
import io.luna.game.model.mob.persistence.PlayerSerializerManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Set;

/**
 * Tracks all {@link Bot} types that need to be remembered by the server. For efficient tracking, bots need to be
 * able to be distinguished from regular players within a database or filesystem. This also helps to ensure uniqueness
 * when generating usernames for bots.
 *
 * @author lare96
 */
public final class BotRepository implements Iterable<String> {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The world.
     */
    private final World world;

    /**
     * A set of persistent bot names that have already been occupied. Includes online and offline bots.
     */
    private final Set<String> persistentNames = Sets.newConcurrentHashSet();

    /**
     * A set of temporary bot names that have already been occupied.
     */
    private final Set<String> temporaryNames = Sets.newConcurrentHashSet();

    /**
     * The session scheduler.
     */
    private final BotScheduleService sessionScheduler;

    /**
     * Creates a new {@link BotRepository}.
     *
     * @param world The world.
     */
    public BotRepository(World world) {
        this.world = world;
        sessionScheduler = new BotScheduleService(world);
    }

    /**
     * Adds a new bot to be tracked by this repository.
     *
     * @param bot The bot to track.
     * @return If this bot wasn't already contained.
     */
    public boolean add(Bot bot) {
        if (bot.isTemporary()) {
            return temporaryNames.add(bot.getUsername());
        } else {
            return persistentNames.add(bot.getUsername());
        }
    }

    /**
     * Removes a temporary bot from this repository. To remove persistent bots, simply delete their save record.
     *
     * @param bot The bot to remove.
     * @return If the bot is temporary, and if it was removed.
     */
    public boolean remove(Bot bot) {
        if (bot.isTemporary()) {
            return temporaryNames.remove(bot.getUsername());
        }
        return false;
    }

    /**
     * Determines if {@code username} is being tracked by any of the backing sets.
     *
     * @param name The username to check.
     * @return {@code true} if this username is being tracked.
     */
    public boolean contains(String name) {
        return containsPersistent(name) || temporaryNames.contains(name);
    }

    /**
     * Determines if {@code username} is being tracked by the persistent set.
     *
     * @param name The username to check.
     * @return {@code true} if this username is being tracked.
     */
    public boolean containsPersistent(String name) {
        return persistentNames.contains(name);
    }

    /**
     * Determines if {@code username} is being tracked by the temporary set.
     *
     * @param name The username to check.
     * @return {@code true} if this username is being tracked.
     */
    public boolean containsTemporary(String name) {
        return temporaryNames.contains(name);
    }

    /**
     * @return The amount of temporary bots.
     */
    public int getTemporaryCount() {
        return temporaryNames.size();
    }

    /**
     * @return The amount of persistent bots.
     */
    public int getPersistentCount() {
        return persistentNames.size();
    }

    /**
     * Populates this repository with all persisted bot usernames and logs in the bots.
     */
    public void load() {
        if (persistentNames.isEmpty()) {
            try {
                PlayerSerializerManager serializerManager = world.getSerializerManager();
                Set<String> loadedNames = serializerManager.getSerializer().loadBotUsernames(world);
                if (!loadedNames.isEmpty()) {
                    persistentNames.addAll(loadedNames);
                }
            } catch (Exception e) {
                logger.catching(e);
            }
        }
    }

    /**
     * Returns an {@link Iterator} that iterates only over persistent {@link Bot} types.
     */
    public Iterator<String> persistentIterator() {
        return persistentNames.iterator();
    }

    @NotNull
    @Override
    public Iterator<String> iterator() {
        return Iterators.concat(persistentNames.iterator(), temporaryNames.iterator());
    }

    /**
     * @return The session scheduler.
     */
    public BotScheduleService getSessionScheduler() {
        return sessionScheduler;
    }
}
