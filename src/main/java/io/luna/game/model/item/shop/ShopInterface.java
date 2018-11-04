package io.luna.game.model.item.shop;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.InventoryOverlayInterface;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * An {@link InventoryOverlayInterface} implementation that opens a shop.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ShopInterface extends InventoryOverlayInterface {

    /**
     * A global map of registered shops.
     */
    private static Map<String, Shop> shops = new HashMap<>();

    /**
     * Retrieves a registered shop.
     *
     * @param name The shop name.
     * @return The shop instance.
     */
    public static Shop get(String name) {
        Shop shop = shops.get(name);
        if (shop == null) {
            throw new NoSuchElementException("Shop with name '" + name + "' does not exist.");
        }
        return shop;
    }

    /**
     * Registers a new shop instance. Will throw an {@link IllegalStateException} if a shop with the same
     * name is already registered.
     *
     * @param shop The shop.
     */
    public static void register(Shop shop) {
        Shop existing = shops.putIfAbsent(shop.getName(), shop);
        if (existing != null) {
            throw new IllegalStateException("Shop with name '" + existing.getName() + "' already exists.");
        }
    }

    /**
     * The shop.
     */
    private final Shop shop;

    /**
     * Creates a new {@link ShopInterface}.
     *
     * @param shop The shop.
     */
    public ShopInterface(Shop shop) {
        super(3824, 3822);
        this.shop = shop;
    }

    /**
     * Creates a new {@link ShopInterface}.
     *
     * @param shopName The shop name.
     */
    public ShopInterface(String shopName) {
        this(get(shopName));
    }

    @Override
    public void onOpen(Player player) {
        // Refresh inventory onto shop.
        Inventory inventory = player.getInventory();
        inventory.setSecondaryRefresh(3823);
        inventory.refreshSecondary(player);

        shop.getContainer().refreshPrimary(player); // Refresh shop.
        shop.getViewing().add(player);
        player.queue(new WidgetTextMessageWriter(shop.getName(), 3901));
    }

    @Override
    public void onClose(Player player) {
        shop.getViewing().remove(player);
        player.getInventory().resetSecondaryRefresh();
    }

    /**
     * @return The shop.
     */
    public Shop getShop() {
        return shop;
    }
}