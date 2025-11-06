package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.AbstractOverlay;
import io.luna.game.model.mob.overlay.StandardInterface;
import io.luna.net.msg.out.DialogueInterfaceMessageWriter;

import java.util.function.Consumer;

/**
 * An {@link AbstractOverlay} that displays a dialogue interface over the chatbox area.
 * <p>
 * Implementations send the appropriate client packet from {@link #open(Player)}. Optional lifecycle hooks are
 * exposed via {@link #setOpenAction(Consumer)} and {@link #setCloseAction(Consumer)} to configure server-side behavior
 * when the dialogue is shown or hidden.
 *
 * @author lare96
 */
public class DialogueInterface extends StandardInterface {

    /**
     * Optional callback invoked after the dialogue is opened (post-packet).
     */
    private Consumer<Player> openAction;

    /**
     * Optional callback invoked when the dialogue is closed.
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
            int id = getId();
            player.queue(new DialogueInterfaceMessageWriter(id));
            if (openAction != null) {
                openAction.accept(player);
            }
        } else {
            player.getOverlays().closeWindows();
        }
    }

    @Override
    public final void onClose(Player player) {
        if (closeAction != null) {
            closeAction.accept(player);
        }
    }

    /**
     * Initializes this dialogue before it is displayed to the player.
     * <p>
     * This method is invoked immediately prior to opening the dialogue interface. Subclasses can override it to
     * perform setup work.
     *
     * @param player The player for whom this dialogue is being initialized.
     * @return {@code true} if the dialogue should be opened; {@code false} to abort.
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
}