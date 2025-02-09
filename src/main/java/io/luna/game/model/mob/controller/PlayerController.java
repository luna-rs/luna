package io.luna.game.model.mob.controller;

import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.service.LogoutService;
import world.player.skill.magic.teleportSpells.TeleportAction;

/**
 * A model containing a set of listeners that can intercept and terminate basic {@link Player} actions before they
 * happen. Multiple controllers can be registered to a player at the same time.
 * <p>
 * Only one instance of a controller exists at a time, meaning anything declared is effectively global (static).
 *
 * @author lare96
 */
public abstract class PlayerController {

    /**
     * Called when this controller is registered.
     *
     * @param player The player.
     */
    public void onRegister(Player player) {

    }

    /**
     * Called when this controller is unregistered.
     *
     * @param player The player.
     */
    public void onUnregister(Player player) {

    }

    /**
     * Called when the player attempts to move.
     *
     * @param player The player.
     * @param newPos The new position.
     * @return {@code true} if the player can move to {@code newPos}.
     */
    public boolean canMove(Player player, Position newPos) {
        return true;
    }

    /**
     * Called when the {@link LogoutService} attempts to finalize {@code player} for logout. This affects logouts from
     * all sources: disconnects, x-logs, and trying to logout manually.
     *
     * @param player The player.
     * @return {@code false} to prevent the player from logging out.
     */
    public boolean canLogout(Player player) {
        return true;
    }

    /**
     * Called when a {@link TeleportAction} attempts to move a player.
     *
     * @param player The player.
     * @param action The teleport action.
     * @return {@code true} if the action can proceed, {@code false} otherwise.
     */
    public boolean canTeleport(Player player, TeleportAction action) {
        return true;
    }

    /**
     * Called every 600ms by the {@link ControllerProcessTask}.
     *
     * @param player The player.
     * @param executionCount The execution count of the task.
     */
    public void process(Player player, int executionCount) {

    }

    /**
     * Called before a generated {@link ControllableEvent} is handled or posted to plugins. You can terminate events
     * before they happen or transform them.
     *
     * <pre>
     * {@code
     * @Override
     * public boolean onPlayerEvent(Player player, ControllableEvent event) {
     *    if(event instanceof CommandEvent) {
     *        String cmdName = ((CommandEvent) event).getName();
     *        if(!cmdName.equals("getscore")) {
     *            player.sendMessage("Only the ::getscore command can be used here!");
     *            return false;
     *        }
     *    }
     *    return true;
     * }
     * }
     * </pre>
     *
     * @param player The player.
     * @param event The event that can be transformed or terminated.
     * @return {@code false} if the event should be terminated.
     */
    public boolean onPlayerEvent(Player player, ControllableEvent event) {
        return true;
    }
}
