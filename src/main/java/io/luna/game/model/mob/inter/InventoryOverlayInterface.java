package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.InventoryOverlayMessageWriter;

/**
 * A {@link StandardInterface} implementation that opens standard and inventory overlay interfaces.
 *
 * @author lare96
 */
public class InventoryOverlayInterface extends StandardInterface {

    /**
     * The overlay identifier.
     */
    private final int overlayId;

    /**
     * Creates a new {@link InventoryOverlayInterface}
     * @param id The interface identifier.
     * @param overlayId The overlay identifier.
     */
    public InventoryOverlayInterface(int id, int overlayId) {
        super(id);
        this.overlayId = overlayId;
    }

    @Override
    public final void open(Player player) {
        player.queue(new InventoryOverlayMessageWriter(unsafeGetId(), overlayId));
    }

    /**
     * @return The overlay identifier.
     */
    public final int getOverlayId() {
        return overlayId;
    }
}