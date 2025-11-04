package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.InventoryOverlayMessageWriter;

/**
 * A {@link StandardInterface} that opens both a main interface and a corresponding inventory overlay.
 * <p>
 * This is used for interfaces that temporarily replace the player’s inventory container with a custom
 * layout (such as banking, trading, or shop screens), while a primary fixed interface is displayed.
 * <p>
 * The overlay is opened via {@link InventoryOverlayMessageWriter}, which takes both the main interface id
 * and the replacement inventory interface id.
 *
 * @author lare96
 */
public class InventoryOverlayInterface extends StandardInterface {

    /**
     * The identifier of the inventory overlay interface that replaces the standard inventory.
     */
    private final int inventoryId;

    /**
     * Creates a new {@link InventoryOverlayInterface}.
     *
     * @param id The main interface identifier to display.
     * @param inventoryId The overlay interface identifier that replaces the inventory.
     */
    public InventoryOverlayInterface(int id, int inventoryId) {
        super(id);
        this.inventoryId = inventoryId;
    }

    @Override
    public final void open(Player player) {
        player.queue(new InventoryOverlayMessageWriter(id, inventoryId));
    }

    /**
     * @return The overlay interface id.
     */
    public final int getInventoryId() {
        return inventoryId;
    }
}
