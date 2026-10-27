package io.luna.game.model.mob.combat.state;

import game.combat.spells.GodSpells;
import game.content.duelArena.DuelController;
import game.content.duelArena.DuelRules;
import game.skill.magic.Magic;
import game.skill.magic.Staff;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.item.Item;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.Spellbook;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.Weapon;
import io.luna.game.model.mob.varp.Varp;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.List;
import java.util.Locale;

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
 * It also provides helpers for rune consumption, spell maximum hits, and autocast interface updates.
 */
public class PlayerMagicCombat {

    /**
     * The player that owns this magic combat state.
     */
    private final Player player;

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
     * Resolves the player's spell maximum before protection prayers and the random damage roll.
     * Magic Dart uses the current Magic level, including boosts and drains. Chaos gauntlets add three to the
     * four elemental bolt spells only while worn in the hands slot. Other spells retain their configured
     * maximum, with the existing equipment and Charge requirements applied to god spells.
     * Both spellbook attacks and default magic damage requests use this calculation when preparing damage;
     * later equipment or level changes do not alter an already prepared hit.
     *
     * @param spell The combat spell being resolved.
     * @return The spell's maximum hit, or its negative effect-only sentinel.
     */
    public int getMaxHit(CombatSpellDefinition spell) {
        return switch (spell.getSpell()) {
            case MAGIC_DART -> 10 + player.skill(Skill.MAGIC).getLevel() / 10;
            case WIND_BOLT, WATER_BOLT, EARTH_BOLT, FIRE_BOLT -> spell.getMaxHit() +
                    (player.getEquipment().computeIdForIndex(Equipment.HANDS) == 777 ? 3 : 0);
            default -> GodSpells.INSTANCE.maxHit(player, spell);
        };
    }

    /**
     * Checks Bind, Snare, and Entangle target restrictions before rune consumption and cast experience.
     * Held targets, the five-tick post-freeze immunity window, Castle Wars barricades, and no-movement duels reject
     * these effect-only spells. Leaving the caster's range releases movement but retains the original freeze window.
     * Damaging ice spells remain castable against frozen targets because their damage is still useful.
     *
     * @param spell The spell about to be cast.
     * @param target The intended recipient.
     * @return Whether these hold-spell restrictions permit the cast.
     */
    public boolean canHoldTarget(CombatSpellDefinition spell, Mob target) {
        if (spell == null || spell == NONE) {
            return true;
        }
        CombatSpell type = spell.getSpell();
        if (type != CombatSpell.BIND && type != CombatSpell.SNARE && type != CombatSpell.ENTANGLE) {
            return true;
        }
        if (target instanceof Npc npc && npc.getId() >= 1532 && npc.getId() <= 1535) {
            player.sendMessage("You can't " + type.name().toLowerCase(Locale.ROOT) + " that.");
            return false;
        }
        if (target.getStatus().isHeldBySpell()) {
            player.sendMessage("Your target is already held by a magical force.");
            return false;
        }
        if (target.getStatus().isFreezeImmune()) {
            player.sendMessage("Your target is currently immune to that spell.");
            return false;
        }
        if (player.getControllers().getPrimary() instanceof DuelController duel &&
                DuelRules.NO_MOVEMENT.enabled(duel.getDuel().getRules())) {
            player.sendMessage("You can not use a hold spell in a no movement duel.");
            return false;
        }
        return true;
    }

    /**
     * Checks target eligibility for the six standard stat-draining curses before consuming runes or awarding
     * cast experience. A stat must have recovered to at least its base level before another curse can be cast,
     * even if a stronger curse would lower it further. Castle Wars barricades cannot be weakened.
     * Other spells, including damaging spells with secondary drains, retain their own effect rules.
     *
     * @param spell The spell about to be cast.
     * @param target The intended recipient.
     * @return Whether this target permits the cast.
     */
    public boolean canCurseTarget(CombatSpellDefinition spell, Mob target) {
        if (spell == null || spell == NONE) {
            return true;
        }
        int skillId = switch (spell.getSpell()) {
            case CONFUSE, STUN -> Skill.ATTACK;
            case WEAKEN, ENFEEBLE -> Skill.STRENGTH;
            case CURSE, VULNERABILITY -> Skill.DEFENCE;
            default -> -1;
        };
        if (skillId == -1) {
            return true;
        }
        if (target instanceof Npc && ((Npc) target).getId() >= 1532 && ((Npc) target).getId() <= 1535) {
            player.sendMessage("You can't weaken that.");
            return false;
        }
        Skill skill = target.skill(skillId);
        if (skill.getLevel() < skill.getStaticLevel()) {
            player.sendMessage("Your foe's " + skill.getName().toLowerCase(Locale.ROOT) +
                    " has already been weakened.");
            return false;
        }
        return true;
    }

    /**
     * Checks whether the player can cast the specified combat spell and, if so, removes the required runes from their
     * inventory.
     * <p>
     * Spells from another spellbook are rejected before rune consumption, and matching selected or autocast spells
     * are cleared.
     * Autocasts also recheck staff eligibility before consuming runes; manually selected spells do not require an
     * autocast-compatible staff.
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

        if (spell.getSpellbook() != player.getSpellbook()) {
            player.sendMessage("That spell is not in your current spellbook.");
            if (spell == selectedSpell) {
                setSelectedSpell(NONE);
            }
            if (spell == autocastSpell) {
                setAutocastSpell(NONE);
            }
            return false;
        }

        if (selectedSpell == NONE && !canAutocast(spell)) {
            setAutocastSpell(NONE);
            player.sendMessage("You cannot auto-cast that spell with your current equipment.");
            return false;
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
     * Determines whether the player currently has an active combat spell setup.
     * <p>
     * This returns {@code true} when either:
     * <ul>
     *     <li>A manual combat spell has been selected.</li>
     *     <li>An autocast spell is selected, autocasting is enabled, and the
     *     spellbook and equipped staff permit that spell.</li>
     * </ul>
     *
     * @return {@code true} if the player is considered to be casting, otherwise
     * {@code false}.
     */
    public boolean isCasting() {
        return selectedSpell != NONE
                || (autocasting && canAutocast(autocastSpell));
    }

    /**
     * Checks whether the equipped staff supports autocasting on the current spellbook.
     *
     * @return Whether a staff is equipped and, for Ancient Magicks, belongs to the supported staff set.
     */
    public boolean canUseAutocastStaff() {
        int weaponId = player.getEquipment().computeIdForIndex(Equipment.WEAPON);
        return weaponId >= 0 && player.getCombat().getWeapon().getType() == Weapon.STAFF &&
                (player.getSpellbook() != Spellbook.ANCIENT ||
                        Staff.Companion.getAUTOCAST_ANCIENTS().contains(weaponId));
    }

    /**
     * Checks spellbook, autocast availability, and current staff eligibility without consuming supplies.
     *
     * This predicate is shared by selection buttons and execution. Level and rune requirements remain separate.
     *
     * @param spell The proposed autocast spell.
     * @return Whether the spell is selectable for autocasting with the current spellbook and equipment.
     */
    public boolean canAutocast(CombatSpellDefinition spell) {
        return spell != null && spell != NONE && spell.getButton() != -1 &&
                spell.getSpellbook() == player.getSpellbook() && canUseAutocastStaff();
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
