package io.luna.game.model.item.shop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * A manager responsible for maintaining and providing access to all globally registered {@link Shop} instances.
 * <p>
 * Shops are created dynamically at runtime and registered into this manager using {@link #register(Shop)}.
 * Once registered, shops may be retrieved by name via {@link #get(String)} or {@link #lookup(String)}.
 * <p>
 * Shop names are treated as unique identifiers. If a shop is registered with a name that already exists in the
 * manager, the existing shop will be overwritten and a warning will be logged.
 *
 * @author lare96
 */
public final class ShopManager {

    /**
     * Logger used for reporting registration conflicts and other shop-related warnings.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * Backing map containing all registered shops, keyed by {@link Shop#getName()}.
     */
    private final Map<String, Shop> shops = new HashMap<>();

    /**
     * Registers a {@link Shop} instance into this manager.
     * <p>
     * If a shop with the same name already exists, it will be replaced and a warning will be logged to indicate the
     * overwrite.
     *
     * @param shop The shop to register.
     */
    public void register(Shop shop) {
        String name = shop.getName();
        if (shops.put(name, shop) != null) {
            logger.warn("Shop with name '{}' was overwritten because it already exists.", name);
        }
    }

    /**
     * Retrieves a registered {@link Shop} by name.
     * <p>
     * This method is equivalent to {@link #lookup(String)}, but will throw an exception if the requested shop does
     * not exist.
     *
     * @param name The name of the shop.
     * @return The registered shop instance.
     * @throws NoSuchElementException If no shop exists with the given name.
     */
    public Shop get(String name) {
        return lookup(name).orElseThrow(() ->
                new NoSuchElementException("Shop '" + name + "' does not exist."));
    }

    /**
     * Performs a lookup for a {@link Shop} by name.
     * <p>
     * If no shop exists with the specified name, an empty {@link Optional} is returned.
     *
     * @param name The name of the shop.
     * @return An {@link Optional} containing the shop if present, otherwise empty.
     */
    public Optional<Shop> lookup(String name) {
        return Optional.ofNullable(shops.get(name));
    }

    /**
     * Determines whether a shop with the specified name is registered.
     *
     * @param name The name of the shop.
     * @return {@code true} if a shop with the given name exists, otherwise {@code false}.
     */
    public boolean contains(String name) {
        return shops.containsKey(name);
    }
}