package io.luna.game.model.mob.combat.state;

import api.combat.magic.TeleBlockAction;
import game.skill.magic.Magic;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.Weapon;
import io.luna.game.model.mob.varp.Varp;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.List;

import static io.luna.game.model.def.CombatSpellDefinition.NONE;
import static java.util.Objects.requireNonNullElse;

/**
 * Holds combat-related magic state for a {@link Player}.
 * <p>
 * This state tracks:
 * <ul>
 *     <li>The remaining Tele Block duration.</li>
 *     <li>The manually selected combat spell.</li>
 *     <li>The configured autocast spell.</li>
 *     <li>Whether autocasting is currently enabled.</li>
 * </ul>
 * It also provides helpers for rune consumption and autocast interface updates.
 */
public class PlayerMagicCombat {

    /**
     * The player that owns this magic combat state.
     */
    private final Player player;

    /**
     * The remaining Tele Block duration in ticks.
     */
    private int teleBlock;

    /**
     * {@code true} if autocasting is currently enabled.
     */
    private boolean autocasting;

    /**
     * The currently selected combat spell.
     * <p>
     * This is the spell chosen manually for the next cast attempt.
     */
    private CombatSpellDefinition selectedSpell = NONE;

    /**
     * The spell configured for autocasting.
     */
    private CombatSpellDefinition autocastSpell = NONE;

    /**
     * Creates a new magic combat state container for the specified player.
     *
     * @param player The player that owns this state.
     */
    public PlayerMagicCombat(Player player) {
        this.player = player;
    }

    /**
     * Checks whether the player can cast the specified combat spell and, if so, removes the required runes from their
     * inventory.
     * <p>
     * If the spell fails its rune or equipment requirements and it is also the currently configured autocast spell,
     * the autocast spell is cleared.
     *
     * @param spell The spell to validate and consume runes for.
     * @return {@code true} if the spell requirements were met and the required runes were removed, otherwise
     * {@code false}.
     * @throws IllegalStateException If {@code spell} is {@code null} or equal to {@link CombatSpellDefinition#NONE}.
     */
    public boolean removeRunes(CombatSpellDefinition spell) {
        if (spell == null || spell == NONE) {
            throw new IllegalStateException("Combat spell is null or NONE during magic combat state.");
        }

        List<Item> required = Magic.INSTANCE.checkRequirements(player, spell, false);
        if (required == null) {
            if (spell == autocastSpell) {
                setAutocastSpell(NONE);
            }
            return false;
        }

        player.getInventory().removeAll(required);
        return true;
    }

    /**
     * Determines whether the player is currently Tele Blocked.
     *
     * @return {@code true} if the remaining Tele Block duration is greater than zero, otherwise {@code false}.
     */
    public boolean isTeleBlocked() {
        return teleBlock > 0;
    }

    /**
     * Decrements the remaining Tele Block duration and returns the previous value.
     *
     * @return The Tele Block duration before decrementing.
     */
    public int decrementTeleBlock() {
        return teleBlock--;
    }

    /**
     * Returns the remaining Tele Block duration.
     *
     * @return The remaining Tele Block duration in ticks.
     */
    public int getTeleBlock() {
        return teleBlock;
    }

    /**
     * Sets the Tele Block duration and schedules Tele Block processing.
     *
     * @param teleBlock The new Tele Block duration in ticks.
     * @return {@code true} if the value was updated, otherwise {@code false}.
     */
    public boolean setTeleBlock(int teleBlock) {
        return setTeleBlock(teleBlock, true);
    }

    /**
     * Sets the Tele Block duration and optionally schedules Tele Block processing.
     * <p>
     * A positive Tele Block duration cannot be applied while another positive Tele Block is already active.
     *
     * @param newTeleBlock The new Tele Block duration in ticks.
     * @param timer {@code true} to submit a {@link TeleBlockAction} when the new value is positive, otherwise {@code false}.
     * @return {@code true} if the value was updated, otherwise {@code false}.
     */
    public boolean setTeleBlock(int newTeleBlock, boolean timer) {
        if (teleBlock > 0 && newTeleBlock > 0) {
            return false;
        }

        teleBlock = newTeleBlock;
        if (teleBlock > 0 && timer) {
            player.getActions().submitIfAbsent(new TeleBlockAction(player));
        }
        return true;
    }

    /**
     * Determines whether the player currently has an active combat spell setup.
     * <p>
     * This returns {@code true} when either:
     * <ul>
     *     <li>A manual combat spell has been selected.</li>
     *     <li>An autocast spell is selected, autocasting is enabled, and the
     *     player is wielding a staff.</li>
     * </ul>
     *
     * @return {@code true} if the player is considered to be casting, otherwise
     * {@code false}.
     */
    public boolean isCasting() {
        PlayerCombatContext combat = player.getCombat();
        return selectedSpell != NONE
                || (autocastSpell != NONE && autocasting && combat.getWeapon().getType() == Weapon.STAFF);
    }

    /**
     * Determines whether autocasting is currently enabled.
     *
     * @return {@code true} if autocasting is enabled, otherwise {@code false}.
     */
    public boolean isAutocasting() {
        return autocasting;
    }

    /**
     * Sets whether autocasting is currently enabled.
     *
     * @param autocasting {@code true} to enable autocasting, otherwise
     * {@code false}.
     */
    public void setAutocasting(boolean autocasting) {
        this.autocasting = autocasting;
    }

    /**
     * Returns the currently selected manual combat spell.
     *
     * @return The selected combat spell definition, or {@link CombatSpellDefinition#NONE}
     * if no spell is selected.
     */
    public CombatSpellDefinition getSelectedSpell() {
        return selectedSpell;
    }

    /**
     * Sets the currently selected manual combat spell from a combat spell wrapper.
     *
     * @param spell The combat spell to select.
     */
    public void setSelectedSpell(CombatSpell spell) {
        setSelectedSpell(spell.getDef());
    }

    /**
     * Sets the currently selected manual combat spell.
     * <p>
     * A {@code null} value is normalized to {@link CombatSpellDefinition#NONE}.
     *
     * @param spell The combat spell definition to select.
     */
    public void setSelectedSpell(CombatSpellDefinition spell) {
        selectedSpell = requireNonNullElse(spell, NONE);
    }

    /**
     * Refreshes the autocast interface state for the player.
     * <p>
     * This updates varp {@code 108} and, when appropriate, updates widget
     * {@code 352} with the formatted name of the configured autocast spell.
     */
    public void refreshAutocast() {
        boolean spellSelected = autocastSpell != NONE;
        String name = !spellSelected ? "none set" : autocastSpell.getSpell().getFormattedName();

        if (!autocasting && !spellSelected) {
            player.sendVarp(new Varp(108, 0));
        } else if (autocasting && !spellSelected) {
            player.sendVarp(new Varp(108, 1));
        } else if (!autocasting) {
            player.sendVarp(new Varp(108, 2));
            player.queue(new WidgetTextMessageWriter(name, 352));
        } else {
            player.sendVarp(new Varp(108, 3));
            player.queue(new WidgetTextMessageWriter(name, 352));
        }
    }

    /**
     * Returns the currently configured autocast spell.
     *
     * @return The autocast spell definition, or {@link CombatSpellDefinition#NONE}
     * if no autocast spell is configured.
     */
    public CombatSpellDefinition getAutocastSpell() {
        return autocastSpell;
    }

    /**
     * Sets the configured autocast spell from a combat spell wrapper.
     *
     * @param spell The combat spell to use for autocasting.
     * @param update {@code true} to refresh the autocast interface immediately,
     * otherwise {@code false}.
     */
    public void setAutocastSpell(CombatSpell spell, boolean update) {
        if (spell != null) {
            setAutocastSpell(spell.getDef(), update);
        }
    }

    /**
     * Sets the configured autocast spell from a combat spell wrapper and
     * refreshes the autocast interface.
     *
     * @param spell The combat spell to use for autocasting.
     */
    public void setAutocastSpell(CombatSpell spell) {
        setAutocastSpell(spell, true);
    }

    /**
     * Sets the configured autocast spell.
     * <p>
     * A {@code null} value is normalized to {@link CombatSpellDefinition#NONE}.
     *
     * @param spell The combat spell definition to use for autocasting.
     * @param update {@code true} to refresh the autocast interface immediately,
     * otherwise {@code false}.
     */
    public void setAutocastSpell(CombatSpellDefinition spell, boolean update) {
        autocastSpell = requireNonNullElse(spell, NONE);
        if (update) {
            refreshAutocast();
        }
    }

    /**
     * Sets the configured autocast spell and refreshes the autocast interface.
     *
     * @param spell The combat spell definition to use for autocasting.
     */
    public void setAutocastSpell(CombatSpellDefinition spell) {
        setAutocastSpell(spell, true);
    }

    /**
     * Returns the player that owns this magic combat state.
     *
     * @return The owning player.
     */
    public Player getPlayer() {
        return player;
    }
}