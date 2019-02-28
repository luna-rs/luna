package io.luna.game.model.mob.persistence;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.luna.LunaConstants;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.login.LoginResponse;
import io.luna.util.ExecutorUtils;
import io.luna.util.ReflectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerPersistence {

    /**
     * The asynchronous logger.
     */
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * The thread pool that will handle save and load requests.
     */
    private final ListeningExecutorService threadPool = ExecutorUtils.newCachedThreadPool();

    /**
     * The map that will track pending saves.
     */
    private final Map<String, Future<Boolean>> pendingSaves = new ConcurrentHashMap<>();

    /**
     * The default serializer.
     */
    private final PlayerSerializer serializer;

    /**
     * Creates a new {@link PlayerPersistence}.
     *
     * @throws ClassCastException If the serializer could not be instanced.
     */
    public PlayerPersistence() throws ClassCastException {
        serializer = newSerializer();
    }

    /**
     * Asynchronously saves persistent data for {@code player}.
     *
     * @param player The player.
     * @return A future returning {@code true} if the save was successful.
     */
    public Future<Boolean> save(Player player) {
        String key = player.getUsername();
        // TODO Needs testing!
        // Cancel any in-progress saves.
        Future<Boolean> pending = pendingSaves.get(key);
        if (pending != null && pending.cancel(true)) {
            LOGGER.warn(player + " an in-progress save was interrupted.");
        }

        // Submit new save task.
        ListenableFuture<Boolean> loadFuture = threadPool.submit(() -> {
            synchronized (player) {
                return serializer.save(player);
            }
        });

        if (!loadFuture.isDone()) {
            // Track the save if it doesn't complete quickly, so it isn't accidentally overwritten.
            pendingSaves.put(key, loadFuture);
            loadFuture.addListener(() -> pendingSaves.remove(key), threadPool);
        }
        return loadFuture;
    }

    /**
     * Loads persistent data for {@code player}.
     *
     * @param player The player.
     * @return A future returning the login response.
     */
    public ListenableFuture<LoginResponse> load(Player player) {
        // TODO Needs testing! and unit tests
        String enteredPassword = player.getPassword();
        if (serializer instanceof SqlPlayerSerializer) {
            // Loading players from a database needs to be done on another thread.
            return threadPool.submit(() -> {
                synchronized (player) {
                    return serializer.load(player, enteredPassword);
                }
            });
        } else {
            // Otherwise, it's fast enough to do right on the networking thread.
            synchronized (player) {
                LoginResponse response = serializer.load(player, enteredPassword);
                return Futures.immediateFuture(response);
            }
        }
    }

    /**
     * Determines if there is currently a save in progress for {@code player}.
     *
     * @param player The player.
     * @return {@code true} if there is a save in progress.
     */
    public boolean hasPendingSave(Player player) {
        return pendingSaves.containsKey(player.getUsername());
    }

    /**
     * Initializes a new serializer based on {@code luna.toml}.
     *
     * @return The serializer.
     * @throws ClassCastException If the argued serializer is the wrong type.
     */
    private PlayerSerializer newSerializer() throws ClassCastException {
        String name = LunaConstants.SERIALIZER;
        try {
            String fullName = "io.luna.game.model.mob.persistence." + name;
            return ReflectionUtils.newInstanceOf(fullName);
        } catch (ClassCastException e) {
            LOGGER.fatal(name + " not an instance of PlayerSerializer.");
            throw e;
        }
    }
}