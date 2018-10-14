package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.DialogueInterfaceMessageWriter;

import java.util.function.Consumer;

/**
 * An {@link AbstractInterface} implementation that opens a dialogue interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public class DialogueInterface extends StandardInterface {

    /**
     * The consumer to apply when this dialogue opens.
     */
    private Consumer<Player> openAction;

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
        boolean shouldOpen = init(player);
        if (shouldOpen) {
            int id = unsafeGetId();
            player.queue(new DialogueInterfaceMessageWriter(id));

            applyOpenAction(player);
        } else {
            player.getInterfaces().close();
        }
    }

    /**
     * Initializes this dialogue. Things like placing text and models on widgets can be done here. Returns
     * {@code false} if this dialogue shouldn't be opened.
     *
     * @param player The player.
     * @return {@code true} if this dialogue should open.
     */
    public boolean init(Player player) {
        return true;
    }

    /**
     * Applies the open action consumer.
     *
     * @param player The player.
     */
    private void applyOpenAction(Player player) {
        if (openAction != null) {
            openAction.accept(player);
        }
    }

    /**
     * Sets the open action consumer.
     *
     * @param openAction The new value.
     */
    public final void setOpenAction(Consumer<Player> openAction) {
        this.openAction = openAction;
    }
}