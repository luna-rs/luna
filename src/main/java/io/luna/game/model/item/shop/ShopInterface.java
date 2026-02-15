package io.luna.game.model.item.shop;

import io.luna.game.model.World;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.InventoryOverlayInterface;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link InventoryOverlayInterface} implementation that opens and manages the shop overlay for a player.
 * <p>
 * This interface binds a {@link Shop} instance to the standard shop widget layout and handles the lifecycle of viewing:
 * <ul>
 *     <li>Refreshing the player's inventory into the shop overlay inventory container.</li>
 *     <li>Refreshing the shop stock container into the shop item list.</li>
 *     <li>Tracking which players are currently viewing the shop.</li>
 *     <li>Updating the shop title text widget.</li>
 * </ul>
 * <p>
 * The interface assumes the shop's backing {@link ItemContainer} has already been initialized (typically through the
 * shop bootstrapping process). Attempting to open an uninitialized shop results in an exception.
 *
 * @author lare96
 */
public final class ShopInterface extends InventoryOverlayInterface {

    /**
     * The shop instance displayed by this overlay.
     */
    private final Shop shop;

    /**
     * Creates a new {@link ShopInterface} for an already resolved {@link Shop}.
     *
     * @param shop The shop instance to display.
     */
    public ShopInterface(Shop shop) {
        super(3824, 3822);
        this.shop = shop;
    }

    /**
     * Creates a new {@link ShopInterface} by resolving a shop by name from the {@link World}.
     *
     * @param world The world containing the {@link ShopManager}.
     * @param name The name of the shop to open.
     * @throws NoSuchElementException If a shop with the given name does not exist.
     */
    public ShopInterface(World world, String name) {
        this(world.getShops().get(name));
    }

    @Override
    public void onOpen(Player player) {
        ItemContainer container = shop.getItems();
        checkState(container.isInitialized(), "This shop has not been initialized.");

        // Refresh inventory onto shop overlay.
        Inventory inventory = player.getInventory();
        inventory.setSecondaryRefresh(3823);
        inventory.refreshSecondary(player);

        // Refresh shop stock and register the viewer for future updates.
        container.refreshPrimary(player);
        shop.getViewing().add(player);

        // Write shop title text.
        player.queue(new WidgetTextMessageWriter(shop.getName(), 3901));
    }

    @Override
    public void onClose(Player player) {
        shop.getViewing().remove(player);
        player.getInventory().resetSecondaryRefresh();
    }

    /**
     * Retrieves the {@link Shop} displayed by this interface.
     *
     * @return The underlying shop instance.
     */
    public Shop getShop() {
        return shop;
    }
}
