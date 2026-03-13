package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.event.impl.VoidEvent;
import io.luna.game.model.Entity;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.interact.InteractionAction;
import io.luna.game.model.mob.interact.InteractionActionListener;
import io.luna.net.client.Client;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.List;

/**
 * Decodes, validates, and dispatches inbound {@link GameMessage}s from a {@link Client}.
 * <p>
 * Each reader is responsible for translating raw network data into an event of type {@code E}, optionally validating
 * and handling that event, and then routing it into the plugin or interaction system.
 *
 * @param <E> The event type produced by this reader.
 * @author lare96
 */
public abstract class GameMessageReader<E extends Event> {

    /**
     * The asynchronous logger.
     */
    protected static final Logger logger = LogManager.getLogger();

    /**
     * The client opcode handled by this reader.
     */
    protected final int opcode;

    /**
     * The encoded packet size handled by this reader.
     */
    protected final int size;

    /**
     * Creates a new {@link GameMessageReader}.
     */
    public GameMessageReader() {
        // These values are injected using reflection.
        opcode = 0;
        size = 0;
    }

    /**
     * Decodes raw network data into an event.
     * <p>
     * Readers that do not need to create a real event should return {@link VoidEvent#INSTANCE}.
     *
     * @param player The player that sent the message.
     * @param msg The raw message data.
     * @return The decoded event.
     */
    public abstract E decode(Player player, GameMessage msg);

    /**
     * Validates a decoded event before it is handled and dispatched.
     *
     * @param player The player that sent the message.
     * @param event The decoded event.
     * @return {@code true} if the event should continue processing, otherwise {@code false}.
     */
    public boolean validate(Player player, E event) {
        return true;
    }

    /**
     * Performs reader-specific handling before the event is dispatched.
     * <p>
     * Subclasses may override this to mutate player state, normalize event data, or perform lightweight side
     * effects before plugin processing.
     *
     * @param player The player that sent the message.
     * @param event The decoded and validated event.
     */
    public void handle(Player player, E event) {

    }

    /**
     * Submits a raw {@link GameMessage} for decoding and dispatch.
     * <p>
     * This method decodes the message, validates the produced event, checks controller restrictions for
     * {@link ControllableEvent}s, performs reader-specific handling, and then dispatches the event either through
     * the interaction system or directly to plugins.
     * <p>
     * Any exception during this flow is treated as fatal to the session and causes the player to be logged out.
     *
     * @param player The player that sent the message.
     * @param msg The raw game message.
     */
    public final void submitMessage(Player player, GameMessage msg) {
        try {
            // Decode event object from raw client data.
            E event = decode(player, msg);

            // Validate the event with the decoder and the current controller if needed.
            if (event != VoidEvent.INSTANCE && validate(player, event) && !player.isLocked()) {
                if (event instanceof ControllableEvent) {
                    if (!player.getControllers().checkEvent((ControllableEvent) event)) {
                        return;
                    }
                }

                // Handle it and post to plugins.
                handle(player, event);
                if (event instanceof InteractableEvent) {
                    handleInteractableEvent(player, event);
                } else {
                    player.getPlugins().post(event);
                }
            }
        } catch (Exception e) {

            // Disconnect on exception.
            logger.error("{} failed in reading game message.", player, e);
            player.forceLogout();
        }
    }

    /**
     * Routes an {@link InteractableEvent} through the interaction system.
     * <p>
     * Matching interaction listeners are resolved from the appropriate event pipeline. If the player does not already
     * have an active {@link InteractionAction}, a new one is submitted. Otherwise, the existing interaction action is
     * updated with the new listener set and target, and any duplicate interaction actions are removed.
     *
     * @param player The player that triggered the interaction.
     * @param event The interactable event to route.
     */
    private void handleInteractableEvent(Player player, E event) {
        List<InteractionActionListener> listeners = player.getPlugins().getPipelines().
                get((Class<E>) event.getClass()).getInteractionListeners(player, event);

        Iterator<InteractionAction> actions = player.getActions().getAll(InteractionAction.class).iterator();
        Entity target = ((InteractableEvent) event).target();

        if (!actions.hasNext()) {
            // We have no interaction action, submit a new one.
            player.submitAction(new InteractionAction(player, listeners, target, (InteractableEvent) event));
        } else {
            // We have an interaction action, we need to update it.
            InteractionAction first = actions.next();

            // Change listeners and target within active interaction action.
            first.setListeners(listeners);
            first.setTarget(target);

            // Remove any potential duplicate actions (shouldn't happen, but just in case).
            while (actions.hasNext()) {
                actions.next().interrupt();
                actions.remove();
            }
        }
    }

    /**
     * @return The message opcode.
     */
    public final int getOpcode() {
        return opcode;
    }

    /**
     * @return The message size.
     */
    public final int getSize() {
        return size;
    }
}