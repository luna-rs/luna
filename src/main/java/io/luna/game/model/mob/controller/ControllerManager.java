package io.luna.game.model.mob.controller;

import com.google.common.collect.Iterators;
import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import org.jetbrains.annotations.NotNull;
import world.player.skill.magic.teleportSpells.TeleportAction;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A model that manages listeners for {@link PlayerController} types.
 *
 * @author lare96
 */
public final class ControllerManager implements Iterable<PlayerController> {

    /**
     * The player.
     */
    private final Player player;

    /**
     * A map {@link PlayerController} types currently active for the player.
     */
    private final Map<ControllerKey<?>, PlayerController> registered = new LinkedHashMap<>();

    /**
     * Creates a new {@link ControllerManager}.
     *
     * @param player The player.
     */
    public ControllerManager(Player player) {
        this.player = player;
    }

    /**
     * Registers a new {@link PlayerController} so that its listeners will be tracked.
     *
     * @param key The key of the controller to register.
     */
    public void register(ControllerKey<?> key) {
        if (key.getController() instanceof PlayerLocationController) {
            throw new IllegalStateException("PlayerLocationController types are automatically tracked and cannot be registered.");
        }
        PlayerController existingController = registered.computeIfAbsent(key, ControllerKey::getController);
        if (existingController != null) {
            existingController.onRegister(player);
        } else {
            throw new IllegalStateException(key.getName() + " is already registered.");
        }
    }

    /**
     * Unregisters a {@link PlayerController} so that its listeners are no longer tracked.
     *
     * @param key The key of the controller to unregister.
     */
    public void unregister(ControllerKey<?> key) {
        if (key.getController() instanceof PlayerLocationController) {
            throw new IllegalStateException("PlayerLocationController types are automatically tracked and cannot be unregistered.");
        }
        PlayerController removed = registered.remove(key);
        if (removed == null) {
            throw new IllegalStateException(key.getName() + " was never registered.");
        }
        removed.onUnregister(player);
    }

    /**
     * Unregisters all non-{@link PlayerLocationController} types.
     */
    public void unregisterAll() {
        Iterator<PlayerController> it = registered.values().iterator();
        while (it.hasNext()) {
            PlayerController controller = it.next();
            if (controller instanceof PlayerLocationController) {
                continue;
            }
            controller.onUnregister(player);
            it.remove();
        }
    }

    /**
     * Determines what {@link PlayerLocationController} types need to be registered or unregistered, and if the player
     * can move based on the registered listeners.
     *
     * @param newPos The new position the player will be at.
     * @return {@code true} if the player can move, {@code false} otherwise.
     */
    public boolean checkMovement(Position newPos) {
        for (ControllerKey<? extends PlayerLocationController> key : ControllerKey.getLocationKeys()) {
            PlayerLocationController controller = key.getController();
            boolean inside = controller.inside(player);
            boolean alreadyRegistered = contains(key);
            if (!alreadyRegistered && inside) {
                if (controller.canEnter(player)) {
                    registered.put(key, controller);
                } else {
                    return false;
                }
            } else if (alreadyRegistered && !inside) {
                if (controller.canExit(player)) {
                    registered.remove(key);
                } else {

                    return false;
                }
            } else if (alreadyRegistered && !controller.canMove(player, newPos)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Determines if a {@link ControllableEvent} should be terminated or not. This can be used to do things like
     * prevent certain commands from being used, or to prevent certain items from being equipped.
     *
     * @param event The event to evaluate.
     * @return {@code true} if the event can be posted, {@code false} if it should be terminated.
     */
    public boolean checkEvent(ControllableEvent event) {
        for (PlayerController controller : registered.values()) {
            if (!controller.onPlayerEvent(player, event)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if the player is able to log out or not.
     *
     * @return {@code true} if the player is able to log out.
     */
    public boolean checkLogout() {
        for (PlayerController controller : registered.values()) {
            if (!controller.canLogout(player)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if the player is able to teleport or not.
     *
     * @param action The teleport action.
     *
     * @return {@code true} if the player is able to teleport.
     */
    public boolean checkTeleport(TeleportAction action) {
        for (PlayerController controller : registered.values()) {
            if (!controller.canTeleport(player, action)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if {@code key} is registered.
     *
     * @param key The key to check for.
     * @return {@code true} if {@code key} is registered, {@code false} otherwise.
     */
    public boolean contains(ControllerKey<?> key) {
        return registered.containsKey(key);
    }

    /**
     * {@inheritDoc}
     *
     * <strong>Warning: The returned iterator is unmodifiable!</strong>
     */
    @NotNull
    @Override
    public Iterator<PlayerController> iterator() {
        return Iterators.unmodifiableIterator(registered.values().iterator());
    }
}
