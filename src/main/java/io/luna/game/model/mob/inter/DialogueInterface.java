package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.DialogueInterfaceMessageWriter;

import java.util.function.Consumer;

/**
 * An {@link AbstractInterface} implementation that opens a dialogue interface.
 *
 * @author lare96
 */
public class DialogueInterface extends StandardInterface {

    /**
     * The consumer to apply when this dialogue opens.
     */
    private Consumer<Player> openAction;

    /**
     * The consumer to apply when this dialogue closes.
     */
    private Consumer<Player> closeAction;

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
            if (openAction != null) {
                openAction.accept(player);
            }
        } else {
            player.getInterfaces().close();
        }
    }

    @Override
    public final void onClose(Player player) {
        if (closeAction != null) {
            closeAction.accept(player);
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
     * Sets the open action consumer.
     *
     * @param openAction The new value.
     */
    public final void setOpenAction(Consumer<Player> openAction) {
        this.openAction = openAction;
    }

    /**
     * Sets the close action consumer.
     *
     * @param closeAction The new value.
     */
    public void setCloseAction(Consumer<Player> closeAction) {
        this.closeAction = closeAction;
    }

    public boolean hasCloseAction() {
        return closeAction != null;
    }
}