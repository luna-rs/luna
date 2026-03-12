package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.StandardInterface;
import io.luna.net.msg.out.DialogueInterfaceMessageWriter;

import java.util.function.Consumer;

/**
 * A {@link StandardInterface} that displays a dialogue interface in the chatbox area.
 * <p>
 * When opened, this interface sends a {@link DialogueInterfaceMessageWriter} for its interface id. Subclasses may
 * override {@link #init(Player)} to perform per-open setup or abort opening entirely.
 * <p>
 * Optional lifecycle callbacks may also be configured:
 * <ul>
 *     <li>{@link #setOpenAction(Consumer)} runs immediately after the dialogue is opened</li>
 *     <li>{@link #setContinueAction(Consumer)} runs when the dialogue is closed after the player has clicked continue</li>
 * </ul>
 *
 * @author lare96
 */
public class DialogueInterface extends StandardInterface {

    /**
     * Optional action invoked immediately after this dialogue is opened.
     */
    private Consumer<Player> openAction;

    /**
     * Optional action invoked when this dialogue is closed after a continue click.
     */
    private Consumer<Player> continueAction;

    /**
     * Whether the player clicked continue on this dialogue.
     * <p>
     * This flag is used to distinguish normal window closing from continue-driven dialogue advancement.
     */
    private boolean continueClicked;

    /**
     * Creates a new {@link DialogueInterface}.
     *
     * @param id The interface id.
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
        if (continueAction != null && continueClicked) {
            continueAction.accept(player);
            continueClicked = false;
        }
    }

    /**
     * Initializes this dialogue before it is displayed.
     * <p>
     * Subclasses may override this to perform setup work or prevent the dialogue from opening.
     *
     * @param player The player opening this dialogue.
     * @return {@code true} to continue opening the dialogue, otherwise {@code false}.
     */
    public boolean init(Player player) {
        return true;
    }

    /**
     * Sets the action invoked immediately after this dialogue is opened.
     *
     * @param openAction The open action.
     */
    public final void setOpenAction(Consumer<Player> openAction) {
        this.openAction = openAction;
    }

    /**
     * Sets the action invoked when this dialogue is closed after a continue click.
     * <p>
     * This action is not invoked for normal interface closing. It only runs when
     * {@link #setContinueClicked(boolean)} has been set to {@code true} before the dialogue closes.
     *
     * @param continueAction The continue action.
     */
    public void setContinueAction(Consumer<Player> continueAction) {
        this.continueAction = continueAction;
    }

    /**
     * Sets whether this dialogue has been continued by the player.
     *
     * @param continueClicked {@code true} if continue was clicked.
     */
    public void setContinueClicked(boolean continueClicked) {
        this.continueClicked = continueClicked;
    }

    /**
     * Returns whether continue was clicked for this dialogue.
     *
     * @return {@code true} if continue was clicked.
     */
    public boolean isContinueClicked() {
        return continueClicked;
    }
}