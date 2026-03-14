package io.luna.game.model.mob.combat;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.def.WeaponSpecialBarDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.varp.Varp;
import io.luna.net.msg.out.WidgetPositionMessageWriter;

/**
 * Handles special attack energy state and special attack bar updates for a player.
 * <p>
 * This class is responsible for toggling special attack activation, draining and restoring
 * special attack energy, and synchronizing the client-side special attack meter widget.
 *
 * @author lare96
 */
public final class CombatSpecialBar {

    /**
     * The total number of segments in the special attack meter.
     */
    private static final int SPECIAL_BAR_SEGMENTS = 10;

    /**
     * The widget position used for a filled special attack segment.
     */
    private static final int SPECIAL_BAR_FILLED = 500;

    /**
     * The widget position used for an empty special attack segment.
     */
    private static final int SPECIAL_BAR_EMPTY = 0;

    /**
     * An {@link Action} that periodically restores special attack energy until it reaches full.
     */
    private final class SpecialBarRestorationAction extends Action<Player> {

        /**
         * Creates a new {@link SpecialBarRestorationAction}.
         */
        public SpecialBarRestorationAction() {
            super(player, ActionType.SOFT, false, 50);
        }

        @Override
        public boolean run() {
            restore(10);
            return energy >= 100;
        }
    }

    /**
     * The player this special attack bar belongs to.
     */
    private final Player player;

    /**
     * The combat context owning this special attack bar.
     */
    private final PlayerCombatContext combat;

    /**
     * The current special attack energy, from {@code 0} to {@code 100}.
     */
    private int energy;

    /**
     * {@code true} if special attack mode is currently enabled.
     */
    private boolean activated;

    /**
     * Creates a new {@link CombatSpecialBar}.
     *
     * @param player the player this special bar belongs to.
     * @param combat the owning combat context.
     */
    public CombatSpecialBar(Player player, PlayerCombatContext combat) {
        this.player = player;
        this.combat = combat;
    }

    /**
     * Toggles the current special attack activation state.
     */
    public void toggle() {
        if (activated) {
            toggleOff();
        } else {
            toggleOn();
        }
    }

    /**
     * Enables special attack mode.
     *
     * @return {@code true} if the special attack mode changed state.
     */
    public boolean toggleOn() {
        if (energy == 0) {
            // TODO Does it only short-circuit here on 0 special energy? Or if you don't have enough for the weapon
            //  regardless?
            // TODO Instant special attacks sent from here? Or do they just ignore the attack delay in the combat loop?
            //  test which feels better.
            player.sendMessage("You do not have enough special energy left.");
            player.sendVarp(new Varp(301, 0));
            return false;
        }
        if (!activated) {
            player.sendVarp(new Varp(301, 1));
            activated = true;
            return true;
        }
        return false;
    }

    /**
     * Disables special attack mode.
     *
     * @return {@code true} if the special attack mode changed state.
     */
    public boolean toggleOff() {
        if (activated) {
            player.sendVarp(new Varp(301, 0));
            activated = false;
            return true;
        }
        return false;
    }

    /**
     * Updates the visual special attack meter for the currently equipped weapon.
     *
     * @return {@code true} if a special attack bar was present and updated.
     */
    public boolean update() {
        WeaponSpecialBarDefinition specialDef = combat.getWeapon().getTypeDef().getSpecial();
        if (specialDef == null) {
            return false;
        }

        int segment = SPECIAL_BAR_SEGMENTS;
        int segmentId = specialDef.getMeter();
        int fillSegments = energy / SPECIAL_BAR_SEGMENTS;

        for (int i = 0; i < SPECIAL_BAR_SEGMENTS; i++) {
            player.queue(new WidgetPositionMessageWriter(--segmentId, fillSegments >= segment ?
                    SPECIAL_BAR_FILLED : SPECIAL_BAR_EMPTY, 0));
            segment--;
        }
        return true;
    }

    /**
     * Drains special attack energy if enough energy is available.
     *
     * @param amount the amount of special energy to drain.
     * @param toggleOff {@code true} to disable special attack mode after draining.
     * @return {@code true} if the drain succeeded.
     */
    public boolean drain(int amount, boolean toggleOff) {
        if (energy >= amount) {
            energy -= amount;
            update();
            if (toggleOff) {
                toggleOff();
            }
            startRestoration();
            return true;
        }
        return false;
    }

    /**
     * Restores special attack energy, clamping the value to the maximum of {@code 100}.
     *
     * @param amount the amount of special energy to restore.
     */
    public void restore(int amount) {
        energy += amount;
        if (energy > 100) {
            energy = 100;
        }
        update();
    }

    /**
     * Starts periodic special attack energy restoration if the bar is not already full and no
     * restoration action is currently active.
     */
    public void startRestoration() {
        if (energy < 100) {
            player.getActions().submitIfAbsent(new SpecialBarRestorationAction());
        }
    }

    /**
     * Returns the current special attack energy.
     *
     * @return the special attack energy, from {@code 0} to {@code 100}.
     */
    public int getEnergy() {
        return energy;
    }

    /**
     * Sets the current special attack energy, clamping the value to the range {@code 0..100}. <strong>This only changes
     * the internal value. The bar still needs to be visually updated using {@link #update()}.</strong>
     *
     * @param specialEnergy The special attack energy to set.
     */
    public void setEnergy(int specialEnergy) {
        if (specialEnergy > 100) {
            specialEnergy = 100;
        } else if (specialEnergy < 0) {
            specialEnergy = 0;
        }
        energy = specialEnergy;
    }

    /**
     * @return {@code true} if special attack mode is currently enabled.
     */
    public boolean isActivated() {
        return activated;
    }
}