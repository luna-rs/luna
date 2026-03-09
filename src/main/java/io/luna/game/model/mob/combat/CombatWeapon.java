package io.luna.game.model.mob.combat;

import io.luna.game.model.def.WeaponDefinition;
import io.luna.game.model.def.WeaponStyleDefinition;
import io.luna.game.model.def.WeaponTypeDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.varp.Varp;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Holds a player's currently resolved combat weapon state.
 * <p>
 * This type acts as a cached view over the player's equipped weapon and exposes the active {@link WeaponDefinition},
 * {@link WeaponTypeDefinition}, and selected {@link WeaponStyleDefinition} used by the combat system.
 * <p>
 * When no weapon is equipped, this instance falls back to the unarmed definitions.
 *
 * @author lare96
 */
public final class CombatWeapon {

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
    private WeaponStyleDefinition styleDef;

    /**
     * Creates a new {@link CombatWeapon} for {@code player}.
     * <p>
     * New instances begin in the unarmed state with the default unarmed punch style selected.
     *
     * @param player The player that owns this combat weapon state.
     */
    public CombatWeapon(Player player) {
        this.player = player;
        def = WeaponDefinition.getUnarmed();
        typeDef = WeaponTypeDefinition.getUnarmed();
        styleDef = requireNonNull(WeaponStyleDefinition.ALL.get(CombatStyle.UNARMED_PUNCH));
    }

    /**
     * Re-resolves the equipped weapon state and attempts to preserve the previous combat stance.
     * <p>
     * The weapon item is read from the player's {@link Equipment#WEAPON} slot. If no item is equipped, or if the
     * equipped item has no corresponding {@link WeaponDefinition}, this method falls back to the unarmed definitions.
     * <p>
     * After the new weapon type is resolved, this method selects a new {@link #styleDef} by searching the available
     * styles for one whose {@link WeaponStyleDefinition#getStance()} matches {@code lastStance}. If no matching stance
     * is found, the first style in the weapon type's style list is selected as a fallback.
     *
     * @param lastStance The previously selected combat stance to preserve if possible.
     */
    public void changeWeapon(CombatStance lastStance) {
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

        //  TODO What combat style is selected if a matching stance can't be found? For now, we default to slot 0, which
        //   is usually the accurate style.
        WeaponStyleDefinition newStyle = typeDef.getStyles().get(0);
        for (WeaponStyleDefinition style : typeDef.getStyles()) {
            if (style.getStance() == lastStance) {
                newStyle = style;
                break;
            }
        }
        styleDef = newStyle;
    }

    /**
     * Changes the selected combat style based on an interface button click.
     * <p>
     * This method searches the current weapon type's available styles for one whose
     * {@link WeaponStyleDefinition#getButton()} matches {@code buttonIdClicked}. If a match is found, {@link #styleDef}
     * is updated to that style. Otherwise, the current style remains unchanged.
     *
     * @param buttonIdClicked The button identifier that was clicked on the combat interface.
     */
    public void changeStyle(int buttonIdClicked) {
        for (WeaponStyleDefinition style : typeDef.getStyles()) {
            if (style.getButton() == buttonIdClicked) {
                styleDef = style;
                break;
            }
        }
        int config = styleDef.getConfig();
        player.sendVarp(new Varp(43, config));
    }

    /**
     * Returns the range of the currently equipped weapon and combat style in use.
     *
     * @return The current attack range.
     */
    public int getRange() {
        return styleDef.getRange();
    }

    /**
     * Returns the resolved weapon definition for the currently equipped weapon.
     *
     * @return The current weapon definition.
     */
    public WeaponDefinition getDef() {
        return def;
    }

    /**
     * Returns the resolved weapon type definition for the currently equipped weapon.
     *
     * @return The current weapon type definition.
     */
    public WeaponTypeDefinition getTypeDef() {
        return typeDef;
    }

    /**
     * Returns the currently selected combat style definition.
     *
     * @return The current combat style definition.
     */
    public WeaponStyleDefinition getStyleDef() {
        return requireNonNull(styleDef, "Combat style definition should never be null.");
    }
}