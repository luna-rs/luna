package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.DialogueInterfaceMessageWriter;

/**
 * An {@link AbstractInterface} implementation that opens a dialogue interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class DialogueInterface extends StandardInterface {

    /**
     * Creates a new {@link DialogueInterface}.
     *
     * @param id The interface identifier.
     */
    public DialogueInterface(int id) {
        super(id);
    }

    @Override
    public final void open(Player player) {
        int id = unsafeGetId();
        player.queue(new DialogueInterfaceMessageWriter(id));
    }
}