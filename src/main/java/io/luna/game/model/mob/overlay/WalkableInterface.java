package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WalkableInterfaceMessageWriter;

/**
 * A {@link WidgetOverlay} that displays a walkable interface above the 3D game scene.
 * <p>
 * Walkable interfaces are overlays that remain visible while the player moves and
 * coexist with all other interface types.
 *
 * @author lare96
 */
public class WalkableInterface extends WidgetOverlay {

    /**
     * Creates a new walkable interface overlay.
     *
     * @param id The client interface identifier to display as a walkable overlay.
     */
    public WalkableInterface(int id) {
        super(id, true);
    }

    @Override
    public final void open(Player player) {
        player.queue(new WalkableInterfaceMessageWriter(id));
    }
}
