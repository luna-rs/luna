package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.InterfaceMessageWriter;

/**
 * An {@link AbstractInterface} implementation representing the most common standard interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class StandardInterface extends AbstractInterface {

    /**
     * Creates a new {@link StandardInterface}.
     *
     * @param id The interface identifier.
     */
    public StandardInterface(int id) {
        super(id, InterfaceType.STANDARD);
    }

    @Override
    public boolean isAutoClose(Player player) {
        return true;
    }

    @Override
    public void open(Player player) {
        int id = unsafeGetId();
        player.queue(new InterfaceMessageWriter(id));
    }
}