package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.InterfaceMessageWriter;

/**
 * A {@link WidgetOverlay} that opens a standard (non-walkable) interface.
 * <p>
 * This is the most common interface type in the 317/377 client: it replaces the primary UI window and typically
 * blocks weak actions while visible. The concrete packet emission occurs in {@link #open(Player)} via
 * {@link InterfaceMessageWriter}.
 *
 * @author lare96
 */
public class StandardInterface extends WidgetOverlay {

    /**
     * Creates a new {@link StandardInterface}.
     *
     * @param id The interface identifier.
     */
    public StandardInterface(int id) {
        super(id, false);
    }

    @Override
    public void open(Player player) {
        player.queue(new InterfaceMessageWriter(id));
    }
}