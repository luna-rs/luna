package io.luna.game.model.mob.controller;

import engine.controllers.Controllers;
import game.skill.magic.teleportSpells.TeleportAction;
import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the active controller state for a {@link Player}.
 * <p>
 * Each player has a single primary {@link PlayerController} that governs high-level interaction rules such as
 * movement, combat, logout, teleporting, and event handling. In addition to the primary controller, this manager also
 * tracks {@link PlayerAreaListener} instances that respond to the player's presence within specific areas.
 *
 * @author lare96
 */
public final class ControllerManager {

    /**
     * The player that owns this controller manager.
     */
    private final Player player;

    /**
     * The registered area listeners for this player.
     */
    private final Set<PlayerAreaListener> areaListeners = new HashSet<>();

    /**
     * The primary controller currently governing this player.
     */
    private PlayerController primary;

    /**
     * Creates a new {@link ControllerManager} for {@code player}.
     * <p>
     * A default {@link PlayerController} is created immediately so the player always has a valid primary controller.
     *
     * @param player the player that owns this manager
     */
    public ControllerManager(Player player) {
        this.player = player;
        primary = new PlayerController(player);
    }

    /**
     * Processes the primary controller and all active area listeners.
     */
    public void process() {
        primary.process();
        for (PlayerAreaListener controller : areaListeners) {
            controller.process(player);
        }
    }

    /**
     * Registers a new primary controller for this player.
     * <p>
     * If a primary controller is already active, it is first unregistered before the new controller is assigned
     * and registered.
     *
     * @param controller The controller to register as primary.
     */
    public void register(PlayerController controller) {
        if (primary != null) {
            primary.unregister();
        }
        primary = controller;
        primary.register();
    }

    /**
     * Unregisters the current primary controller and restores the default {@link PlayerController}.
     */
    public void unregister() {
        primary.unregister();
        primary = new PlayerController(player);
        primary.register();
    }

    /**
     * Checks the player's current position against all tracked area listeners.
     * <p>
     * Listeners that the player has entered will receive an enter callback, and listeners that the player has left
     * will receive an exit callback. After listener checks are completed, the primary controller movement hook is
     * invoked.
     */
    public void checkPosition() {
        for (PlayerAreaListener listener : Controllers.INSTANCE.getGLOBAL_LOCATABLE_CONTROLLERS()) {
            boolean inside = listener.inside(player.getPosition());
            boolean registered = areaListeners.contains(listener);
            if (!registered && inside) {
                listener.enter(player);
                areaListeners.add(listener);
            } else if (registered && !inside) {
                listener.exit(player);
                areaListeners.remove(listener);
            }
        }
        primary.move();
    }

    /**
     * Checks whether the active primary controller allows {@code event}.
     *
     * @param event The event being checked.
     * @return {@code true} if the event is permitted, otherwise {@code false}.
     */
    public boolean checkEvent(ControllableEvent event) {
        return primary.event(event);
    }

    /**
     * Checks whether the player is allowed to log out.
     *
     * @return {@code true} if logout is allowed, otherwise {@code false}.
     */
    public boolean checkLogout() {
        return primary.logout();
    }

    /**
     * Checks whether the player is allowed to perform a teleport action.
     *
     * @param action The teleport action being attempted.
     * @return {@code true} if teleporting is allowed, otherwise {@code false}.
     */
    public boolean checkTeleport(TeleportAction action) {
        return primary.teleport(action);
    }

    /**
     * Checks whether the player is allowed to fight {@code other}.
     *
     * @param other The other mob involved in the combat interaction.
     * @return {@code true} if combat is allowed, otherwise {@code false}.
     */
    public boolean checkCombat(Mob other) {
        return primary.combat(other);
    }

    /**
     * Checks whether {@code controller} is currently tracked in this manager's area listener set.
     *
     * @param controller The controller to check.
     * @return {@code true} if the listener is present, otherwise {@code false}.
     */
    public boolean contains(PlayerAreaListener controller) {
        return areaListeners.contains(controller);
    }

    /**
     * @return The primary controller.
     */
    public PlayerController getPrimary() {
        return primary;
    }
}