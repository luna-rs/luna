package io.luna.game.model.mob.controller;

import game.skill.magic.teleportSpells.TeleportAction;
import io.luna.game.LogoutService;
import io.luna.game.event.impl.ControllableEvent;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.PlayerCombatContext;

/**
 * A base controller that can intercept and regulate core {@link Player} behaviour.
 * <p>
 * Controllers act as lifecycle hooks that allow systems such as the Wilderness, minigames, dungeons, or special
 * regions to modify or restrict player actions. Only one controller may be active at a time, and it is evaluated
 * before actions such as movement, teleporting, combat, logout, or event execution occur.
 * <p>
 * Implementations override specific methods to selectively block or alter player behaviour while the controller is
 * registered.
 *
 * @author lare96
 */
public class PlayerController {

    /**
     * The player this controller is attached to.
     */
    private final Player player;

    /**
     * Creates a new {@link PlayerController}.
     *
     * @param player The player this controller controls.
     */
    public PlayerController(Player player) {
        this.player = player;
    }

    /**
     * Called when this controller is registered to the player.
     * <p>
     * Override to initialize controller-specific state, interfaces, or flags.
     */
    public void register() {

    }

    /**
     * Called when this controller is unregistered from the player.
     * <p>
     * Override to clean up any controller-specific state or UI changes.
     */
    public void unregister() {

    }

    /**
     * Called once every game tick (~600ms) before the player update cycle.
     * <p>
     * Override to apply periodic logic.
     */
    public void process() {

    }

    /**
     * Called whenever the player changes position.
     * <p>
     * Useful for region-based logic such as updating wilderness level, triggering area transitions, or enforcing
     * boundary restrictions.
     */
    public void move() {

    }

    /**
     * Called when {@link LogoutService} attempts to finalize logout.
     * <p>
     * This applies to all logout sources, including manual logout, disconnects, and forced logout conditions.
     *
     * @return {@code false} to prevent logout, otherwise {@code true}.
     */
    public boolean logout() {
        return true;
    }

    /**
     * Called when a {@link TeleportAction} attempts to move the player.
     * <p>
     * Controllers may block teleports in restricted areas such as the Wilderness or minigames.
     *
     * @param action The teleport action being attempted.
     * @return {@code true} if teleporting is allowed, otherwise {@code false}.
     */
    public boolean teleport(TeleportAction action) {
        return true;
    }

    /**
     * Called when the player attempts to enter combat with another {@link Mob}.
     * <p>
     * By default, this validates standard combat eligibility via {@link PlayerCombatContext#checkCombatMob(Mob)} and
     * {@link PlayerCombatContext#checkMultiCombat(Mob)}.
     * <p>
     * Controllers may override this to enforce region-specific combat rules such as wilderness level checks,
     * safe zones, or minigame combat restrictions.
     *
     * @param other The target mob the player is attempting to attack.
     * @return {@code true} if combat may proceed, otherwise {@code false}.
     */
    public boolean combat(Mob other) {
        return player.getCombat().checkCombatMob(other) && player.getCombat().checkMultiCombat(other);
    }

    /**
     * Called before a generated {@link ControllableEvent} is processed or posted
     * to plugins.
     * <p>
     * Controllers may terminate or transform events before they execute. This is
     * useful for restricting commands, blocking interactions, or enforcing area
     * behaviour rules.
     * <p>
     * Example usage:
     *
     * <pre>{@code
     * @Override
     * public boolean event(ControllableEvent event) {
     *     if(event instanceof CommandEvent) {
     *         String cmdName = ((CommandEvent) event).getName();
     *         if(!cmdName.equals("getscore")) {
     *             player.sendMessage("Only the ::getscore command can be used here!");
     *             return false;
     *         }
     *     }
     *     return true;
     * }
     * }</pre>
     *
     * @param event The event that may be transformed or cancelled.
     * @return {@code false} to terminate the event, otherwise {@code true}.
     */
    public boolean event(ControllableEvent event) {
        return true;
    }

}