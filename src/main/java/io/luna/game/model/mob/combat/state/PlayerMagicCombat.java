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

public class PlayerMagicCombat {
    private final Player player;
    /**
     * The remaining Tele Block duration in ticks.
     */
    private int teleBlock;
    private boolean autocasting;
    private CombatSpellDefinition selectedSpell = NONE;
    private CombatSpellDefinition autocastSpell = NONE;

    public PlayerMagicCombat(Player player) {
        this.player = player;
    }

    public boolean removeRunes(CombatSpellDefinition spell) {
        if (spell == null || spell == NONE) {
            throw new IllegalStateException("Combat spell is null or NONE during magic combat state.");
        }
        List<Item> required = Magic.INSTANCE.checkRequirements(player, spell, false);
        if (required == null) {
            if (spell == autocastSpell) {
                // If the failed spell is our autocasted spell, clear it.
                setAutocastSpell(NONE);
            }
            // We failed the casting requirements.
            return false;
        }
        // We passed, remove items.
        player.getInventory().removeAll(required);
        return true;
    }

    /**
     * Determines whether the player is currently tele blocked.
     *
     * @return {@code true} if Tele Block is active, otherwise {@code false}.
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
     * @return The remaining Tele Block duration.
     */
    public int getTeleBlock() {
        return teleBlock;
    }

    /**
     * Sets the Tele Block duration and schedules Tele Block processing.
     *
     * @param teleBlock The new Tele Block duration.
     * @return {@code true} if the value was updated, otherwise {@code false}.
     */
    public boolean setTeleBlock(int teleBlock) {
        return setTeleBlock(teleBlock, true);
    }

    /**
     * Sets the Tele Block duration, optionally scheduling Tele Block processing.
     * <p>
     * A positive Tele Block cannot be applied if one is already active.
     *
     * @param newTeleBlock The new Tele Block duration.
     * @param timer {@code true} to submit a {@link TeleBlockAction} when the effect is
     * active, otherwise {@code false}.
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

    public boolean isCasting() {
        PlayerCombatContext combat = player.getCombat();
        return selectedSpell != NONE || (autocastSpell != NONE && autocasting && combat.getWeapon().getType() == Weapon.STAFF);
    }

    public boolean isAutocasting() {
        return autocasting;
    }

    public void setAutocasting(boolean autocasting) {
        this.autocasting = autocasting;
    }

    public CombatSpellDefinition getSelectedSpell() {
        return selectedSpell;
    }

    public void setSelectedSpell(CombatSpell spell) {
        setSelectedSpell(spell.getDef());
    }

    public void setSelectedSpell(CombatSpellDefinition spell) {
        selectedSpell = requireNonNullElse(spell, NONE);
    }

    public void refreshAutocast() {
        PlayerCombatWeapon weapon = player.getCombat().getWeapon();
        if (weapon.getType() != Weapon.STAFF) {
            // Not currently wielding a weapon, no autocast interface to refresh.
            return;
        }
        boolean spellSelected = autocastSpell != NONE;
        String name = !spellSelected ? "none set" : autocastSpell.getSpell().getFormattedName();
        if (!autocasting && !spellSelected) {
            // Varp state 0: Auto-cast off, no spell selected.
            player.sendVarp(new Varp(108, 0));
        } else if (autocasting && !spellSelected) {
            // Varp state 1: Auto-cast is selected, no spell selected.
            player.sendVarp(new Varp(108, 1));
        } else if (!autocasting) {
            // Varp state 2: Auto-cast off, a spell is selected.
            player.sendVarp(new Varp(108, 2));
            player.queue(new WidgetTextMessageWriter(name, 352));
        } else {
            // Varp state 3: Auto-cast is selected, a spell is selected.
            player.sendVarp(new Varp(108, 3));
            player.queue(new WidgetTextMessageWriter(name, 352));
        }
    }

    public CombatSpellDefinition getAutocastSpell() {
        return autocastSpell;
    }

    public void setAutocastSpell(CombatSpell spell, boolean update) {
        if (spell != null) {
            setAutocastSpell(spell.getDef(), update);
        }
    }

    public void setAutocastSpell(CombatSpell spell) {
        setAutocastSpell(spell, true);
    }

    public void setAutocastSpell(CombatSpellDefinition spell, boolean update) {
        autocastSpell = requireNonNullElse(spell, NONE);
        if (update) {
            refreshAutocast();
        }
    }

    public void setAutocastSpell(CombatSpellDefinition spell) {
        setAutocastSpell(spell, true);
    }

    public Player getPlayer() {
        return player;
    }
}
