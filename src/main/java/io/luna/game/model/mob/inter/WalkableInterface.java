package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WalkableInterfaceMessageWriter;

/**
 * An {@link AbstractInterface} implementation that opens a walkable interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class WalkableInterface extends AbstractInterface {

    /**
     * Creates a new {@link WalkableInterface}.
     *
     * @param id The interface identifier.
     */
    public WalkableInterface(int id) {
        super(id);
    }

    @Override
    public void open(Player player) {
        int id = unsafeGetId();
        player.queue(new WalkableInterfaceMessageWriter(id));
    }

    @Override
    public final boolean isCloseOnAction(Player player) {
        return false;
    }
}
