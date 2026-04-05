package io.luna.game.model.mob.combat.state;

import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.def.WeaponDefinition;
import io.luna.game.model.def.WeaponTypeDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.game.model.mob.combat.SpecialAttackType;
import io.luna.game.model.mob.combat.Weapon;
import io.luna.game.model.mob.varp.Varp;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Holds a player's currently resolved combat weapon state.
 * <p>
 * This type acts as a cached view over the player's equipped weapon and exposes the active {@link WeaponDefinition},
 * {@link WeaponTypeDefinition}, and selected {@link CombatStyleDefinition} used by the combat system.
 * <p>
 * When no weapon is equipped, this instance falls back to the unarmed definitions.
 *
 * @author lare96
 */
public final class PlayerCombatWeapon {

    /**
     * The player that owns this combat weapon state.
     */
    private final Player player;

    /**
     * The resolved weapon definition for the currently equipped weapon.
     * <p>
     * This defaults to {@link WeaponDefinition#getUnarmed()} when no weapon is equipped or when the equipped item has
     * no matching definition.
     */
    private WeaponDefinition def;

    /**
     * The resolved weapon type definition for the currently equipped weapon.
     * <p>
     * This defaults to {@link WeaponTypeDefinition#getUnarmed()} when no weapon is equipped or when the equipped item
     * has no matching definition.
     */
    private WeaponTypeDefinition typeDef;

    /**
     * The currently selected combat style definition for the equipped weapon type.
     */
    private CombatStyleDefinition styleDef;

    /**
     * The resolved special attack type for the currently equipped weapon.
     * <p>
     * This is looked up from the equipped weapon's item identifier during {@link #refreshWeapon(CombatStance)} and
     * will be {@code null} when the player is unarmed or when the weapon has no registered special attack type.
     */
    private SpecialAttackType specialAttackType;

    /**
     * Creates a new {@link PlayerCombatWeapon} for {@code player}.
     * <p>
     * New instances begin in the unarmed state with the default unarmed punch style selected.
     *
     * @param player The player that owns this combat weapon state.
     */
    public PlayerCombatWeapon(Player player) {
        this.player = player;
        def = WeaponDefinition.getUnarmed();
        typeDef = WeaponTypeDefinition.getUnarmed();
        styleDef = requireNonNull(CombatStyleDefinition.ALL.get(CombatStyle.UNARMED_PUNCH));
    }

    /**
     * Re-resolves the equipped weapon state and attempts to preserve the previous combat stance.
     * <p>
     * The weapon item is read from the player's {@link Equipment#WEAPON} slot. If no item is equipped, or if the
     * equipped item has no corresponding {@link WeaponDefinition}, this method falls back to the unarmed definitions.
     * <p>
     * After the new weapon type is resolved, this method selects a new {@link #styleDef} by searching the available
     * styles for one whose {@link CombatStyleDefinition#getStance()} matches {@code lastStance}. If no matching stance
     * is found, the first style in the weapon type's style list is selected as a fallback.
     *
     * @param lastStance The previously selected combat stance to preserve if possible.
     */
    public void refreshWeapon(CombatStance lastStance) {
        Item weaponItem = player.getEquipment().get(Equipment.WEAPON);
        if (weaponItem == null) {
            def = WeaponDefinition.getUnarmed();
            typeDef = WeaponTypeDefinition.getUnarmed();
        } else {
            Optional<WeaponDefinition> optionalWeaponDef = WeaponDefinition.ALL.get(weaponItem.getId());
            def = optionalWeaponDef.orElse(WeaponDefinition.getUnarmed());
            typeDef = optionalWeaponDef.map(WeaponDefinition::getTypeDef)
                    .orElse(WeaponTypeDefinition.getUnarmed());
        }

        CombatStyleDefinition newStyle = typeDef.getStyles().get(0);
        for (CombatStyleDefinition style : typeDef.getStyles()) {
            if (style.getStance() == lastStance) {
                newStyle = style;
                break;
            }
        }
        styleDef = newStyle;
        specialAttackType = weaponItem == null ? null : SpecialAttackType.IDS.get(weaponItem.getId());
    }

    /**
     * Changes the selected combat style based on an interface button click.
     * <p>
     * This method searches the current weapon type's available styles for one whose
     * {@link CombatStyleDefinition#getButton()} matches {@code buttonIdClicked}. If a match is found, {@link #styleDef}
     * is updated to that style. Otherwise, the current style remains unchanged.
     *
     * @param buttonIdClicked The button identifier that was clicked on the combat interface.
     */
    public void changeStyle(int buttonIdClicked) {
        for (CombatStyleDefinition style : typeDef.getStyles()) {
            if (style.getButton() == buttonIdClicked) {
                styleDef = style;
                break;
            }
        }
        refreshStyleButton();
    }

    /**
     * Refreshes the combat style selection varp on the client.
     * <p>
     * The config value is taken from the currently selected {@link #styleDef} and sent using the combat-style varp so
     * the weapon interface highlights the active attack style button.
     */
    public void refreshStyleButton() {
        int config = styleDef.getConfig();
        player.sendVarp(new Varp(43, config));
    }

    /**
     * @return The owning player.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return The current weapon id.
     */
    public int getId() {
        return def.getId();
    }

    /**
     * @return The current attack range.
     */
    public int getRange() {
        return styleDef.getRange();
    }

    /**
     * @return The attack speed in ticks for the active style.
     */
    public int getSpeed() {
        return styleDef.getSpeed();
    }

    /**
     * @return {@code true} if the active style attacks beyond standard melee distance, otherwise {@code false}.
     */
    public boolean isRanged() {
        return getRange() > 2;
    }

    /**
     * @return The current weapon type.
     */
    public Weapon getType() {
        return typeDef.getType();
    }

    /**
     * @return The current weapon definition.
     */
    public WeaponDefinition getDef() {
        return def;
    }

    /**
     * @return The current weapon type definition.
     */
    public WeaponTypeDefinition getTypeDef() {
        return typeDef;
    }

    /**
     * @return The current combat style definition.
     */
    public CombatStyleDefinition getStyleDef() {
        return requireNonNull(styleDef, "Combat style definition should never be null.");
    }

    /**
     * @return The current special attack type, or {@code null} if the equipped weapon has no special attack.
     */
    public SpecialAttackType getSpecialAttackType() {
        return specialAttackType;
    }

    /**
     * Sets the resolved special attack type for this combat weapon state.
     * <p>
     * This can be used to override or manually update the special attack mapping after the weapon state has been
     * refreshed.
     *
     * @param specialAttackType The new special attack type, or {@code null} if none should be active.
     */
    public void setSpecialAttackType(SpecialAttackType specialAttackType) {
        this.specialAttackType = specialAttackType;
    }
}