package io.luna.net.msg;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.Entity;
import io.luna.game.model.mob.Player;
import io.luna.net.client.Client;
import io.luna.net.codec.ByteMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * An abstraction model that decodes, validates, and handles incoming messages from the {@link Client}.
 *
 * @param <E> The type of event to decode and verify.
 * @author lare96
 */
public abstract class GameMessageReader<E extends Event> {

    /**
     * The asynchronous logger.
     */
    protected static final Logger logger = LogManager.getLogger();

    /**
     * The opcode.
     */
    protected final int opcode;

    /**
     * The size.
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
     * Decodes raw buffer data into an {@link Event} type. Use {@link NullEvent} for incoming messages that have
     * no event data to create.
     *
     * @param player The player.
     * @param msg The raw message data.
     */
    public abstract E decode(Player player, GameMessage msg);

    /**
     * Validates the decoded {@link Event} before its handled and posted to plugins.
     *
     * @param player The player.
     * @param event The event to validate.
     * @return {@code true} if the event is valid.
     */
    public boolean validate(Player player, E event) {
        return true;
    }

    /**
     * Handles a decoded and validated event before its posted to plugins.
     *
     * @param player The player.
     * @param event The event to handle.
     */
    public void handle(Player player, E event) {

    }

    /**
     * Submits a raw {@link GameMessage} to be decoded and handled.
     *
     * @param player The player.
     * @param msg The game message.
     */
    public final void submitMessage(Player player, GameMessage msg) {
        try {
            player.getTimeout().reset();

            // Decode event object from raw client data.
            E event = decode(player, msg);

            // Validate the event with the decoder and the current controller if needed.
            if (event != NullEvent.INSTANCE && validate(player, event)) {

                if (event instanceof ControllableEvent) {
                    if (player.isLocked() || !player.getControllers().checkEvent((ControllableEvent) event)) {
                        return;
                    }
                }

                // Handle it and post to plugins.
                if (event instanceof InteractableEvent) {
                    if (player.isLocked()) {
                        return;
                    }
                    InteractableEvent interactableEvent = (InteractableEvent) event;
                    Entity interactingWith = interactableEvent.target();
                    player.submitAction(new InteractionAction(player, interactingWith, interactableEvent.distance()) {
                        @Override
                        public void execute() {
                            postEvent(player, event);
                        }
                    });
                } else {
                    postEvent(player, event);
                }
            }
        } catch (Exception e) {

            // Disconnect on exception.
            logger.error(new ParameterizedMessage("{} failed in reading game message.", player, e));
            player.logout();
        } finally {

            // Release pooled buffer.
            ByteMessage payload = msg.getPayload();
            if (!payload.release()) {
                // Ensure that all pooled Netty buffers are deallocated here, to avoid leaks. Entering this
                // section of the code means that a buffer was not released (or retained) when it was supposed to
                // be, so we log a warning.
                logger.warn("Buffer reference count too high [opcode: {}, ref_count: {}]",
                        box(msg.getOpcode()), box(payload.refCnt()));
                payload.releaseAll();
            }
        }
    }

    /**
     * Handles the event and posts it to plugins.
     *
     * @param player The player to handle and post for.
     * @param event The event to handle and post.
     */
    private void postEvent(Player player, E event) {
        handle(player, event);
        player.getPlugins().post(event);
    }

    /**
     * @return The opcode.
     */
    public final int getOpcode() {
        return opcode;
    }

    /**
     * @return The size.
     */
    public final int getSize() {
        return size;
    }
}
