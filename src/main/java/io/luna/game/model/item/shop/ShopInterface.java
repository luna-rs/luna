package io.luna.game.model.item.shop;

import io.luna.game.model.World;
import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.ItemContainer;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.InventoryOverlayInterface;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link InventoryOverlayInterface} implementation that opens a shop.
 *
 * @author lare96 
 */
public final class ShopInterface extends InventoryOverlayInterface {

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
     * @param world The world.
     * @param name The shop name.
     */
    public ShopInterface(World world, String name) {
        this(world.getShops().get(name));
    }

    @Override
    public void onOpen(Player player) {
        ItemContainer container = shop.getContainer();
        checkState(container.isInitialized(), "This shop has not been initialized.");

        // Refresh inventory onto shop.
        Inventory inventory = player.getInventory();
        inventory.setSecondaryRefresh(3823);
        inventory.refreshSecondary(player);

        container.refreshPrimary(player); // Refresh shop.
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