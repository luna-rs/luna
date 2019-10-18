package io.luna.game.model.item.shop;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * A model that manages global shop instances. Shops are mapped by their names and interned for speedier
 * lookups (some of the time).
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ShopManager {

    /**
     * The asynchronous logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A map of shops.
     */
    private final Map<String, Shop> shops = new HashMap<>();

    /**
     * Register {@code shop} in the backing map.
     *
     * @param shop The shop.
     */
    public void register(Shop shop) {
        String name = shop.getName().intern();
        if (shops.put(name, shop) != null) {
            logger.warn("Shop with name '" + name + "' was overwritten because it already exists.");
        }
    }

    /**
     * The equivalent to {@link #lookup(String)}, but throws an {@link NoSuchElementException} if the
     * requested shop doesn't exist.
     *
     * @param name The shop name.
     * @return {@code }
     */
    public Shop get(String name) {
        return lookup(name).orElseThrow(() ->
                new NoSuchElementException("Shop with name '" + name + "' does not exist."));
    }

    /**
     * Performs a lookup in the backing map for {@code name}.
     *
     * @param name The shop name.
     * @return The shop instance, wrapped in an optional.
     */
    public Optional<Shop> lookup(String name) {
        return Optional.ofNullable(shops.get(name));
    }

    /**
     * Determines if the backing map contains an entry for {@code name}.
     *
     * @param name The shop name.
     * @return {@code true} if the backing map contains the name.
     */
    public boolean contains(String name) {
        return shops.containsKey(name);
    }
}