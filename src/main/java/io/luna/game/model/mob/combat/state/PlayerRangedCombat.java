package io.luna.game.model.mob.combat.state;

import game.item.degradable.DegradableItems;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.AmmoType;

import static io.luna.game.model.def.AmmoDefinition.UNEQUIPPED;
import static java.util.Objects.requireNonNullElse;

/**
 * Tracks and manages the ranged-ammunition state for a {@link Player}.
 * <p>
 * This class caches the player's currently active {@link AmmoDefinition} so ranged combat logic can efficiently
 * determine whether ammunition is available, whether the equipped weapon is compatible with that ammunition, and
 * which equipped item should be consumed after each attack.
 * <p>
 * The cached ammo state should be refreshed whenever the player's weapon or ammunition slot changes.
 *
 * @author lare96
 */
public class PlayerRangedCombat {

    /**
     * The player whose ranged combat state is being tracked.
     */
    private final Player player;

    /**
     * The ammo this player is currently using.
     * <p>
     * This may represent explicit ammunition equipped in the ammunition slot, an ammoless ranged weapon such as
     * thrown weapons, or {@link AmmoDefinition#UNEQUIPPED} when no valid ranged ammo state is currently available.
     */
    private AmmoDefinition ammo = UNEQUIPPED;

    /**
     * Creates a new ranged combat state container for a player.
     *
     * @param player The player this state belongs to.
     */
    public PlayerRangedCombat(Player player) {
        this.player = player;
    }

    /**
     * Consumes or processes ammunition for the player's current ranged attack.
     * <p>
     * This method validates the currently equipped weapon against the cached ammo state and then applies the
     * appropriate consumption behavior:
     * <ul>
     *     <li>If no valid ammo is cached, the attack cannot proceed.</li>
     *     <li>If explicit ammunition is required, one item is removed from the ammunition slot.</li>
     *     <li>If the weapon is ammoless and stackable, one weapon item is removed from the weapon slot.</li>
     *     <li>If the weapon is a crystal bow, its degradable state is processed instead of removing an item stack
     *     directly.</li>
     *     <li>If the weapon is ammoless and neither stackable nor degradable, no item is consumed.</li>
     * </ul>
     *
     * @return {@code true} if ammo handling succeeded and the ranged attack may continue, {@code false} if the player
     * lacks valid ammo or is using incompatible ammunition.
     * @throws IllegalStateException If no ranged weapon is equipped or the cached ranged state is invalid for the current
     * equipment setup.
     */
    public boolean removeAmmo() {
        Item rangedWeapon = player.getEquipment().get(Equipment.WEAPON);
        if (rangedWeapon == null) {
            // With ranged there should always be a weapon equipped.
            throw new IllegalStateException("No weapon is currently equipped while in ranging combat state.");
        }

        int weaponId = rangedWeapon.getId();
        if (ammo == UNEQUIPPED) {
            // We have a ranged weapon equipped but no ammo.
            player.sendMessage("You do not have enough ammo in your quiver.");
            return false;
        } else if (ammo.isNeedsWeapon()) {
            // We have ammo and are using a weapon that requires explicit ammo.
            if (!ammo.getWeapons().contains(weaponId)) {
                // Weapon and ammo are incompatible.
                player.sendMessage("You can't use that type of ammunition with this weapon.");
                return false;
            }
            // Decrement the ammo from the ammunition slot.
            Item ammoItem = player.getEquipment().get(Equipment.AMMUNITION);
            player.getEquipment().set(Equipment.AMMUNITION, decrementAmmo(ammoItem));
            return true;
        } else if (ammo.isAmmoless()) {
            // We are using a ranged weapon that doesn't require ammo.
            if (rangedWeapon.getItemDef().isStackable()) {
                // We are using a stackable weapon like knives or throwing axes.
                player.getEquipment().set(Equipment.WEAPON, decrementAmmo(rangedWeapon));
                return true;
            } else if (ammo.getType() == AmmoType.CRYSTAL_ARROW) {
                // We are using a crystal bow.
                return DegradableItems.INSTANCE.handleCrystalBow(player);
            } else {
                // Not stackable, doesn't degrade, so it doesn't use ammo.
                return true;
            }
        }
        throw new IllegalStateException("No valid ranged ammo/weapon is equipped while in ranging combat state.");
    }

    /**
     * Decrements an equipped ammo-bearing item by one.
     * <p>
     * If the resulting stack size reaches zero, {@code null} is returned so the equipment slot can be cleared.
     *
     * @param oldItem The item stack to decrement.
     * @return The decremented item stack, or {@code null} if the stack was exhausted.
     */
    private Item decrementAmmo(Item oldItem) {
        oldItem = oldItem.addAmount(-1);
        if (oldItem.getAmount() == 0) {
            // We've reached the last of the item.
            oldItem = null;
        }
        return oldItem;
    }

    /**
     * Refreshes the cached {@link AmmoDefinition} after a weapon or ammunition-slot equipment change.
     * <p>
     * Only changes to {@link Equipment#WEAPON} and {@link Equipment#AMMUNITION} are handled. Ammoless ranged weapons
     * take priority over the ammunition slot. Otherwise, the cached state falls back to the currently equipped ammo
     * item, or {@link AmmoDefinition#UNEQUIPPED} if no registered ammo is equipped.
     *
     * @param index The equipment slot that changed.
     */
    public void refreshAmmo(int index) {
        if (index == Equipment.WEAPON || index == Equipment.AMMUNITION) {
            int weaponId = player.getEquipment().computeIdForIndex(Equipment.WEAPON);
            int ammoId = player.getEquipment().computeIdForIndex(Equipment.AMMUNITION);
            AmmoDefinition ammoDef = AmmoDefinition.NO_AMMO_WEAPONS.getOrDefault(weaponId, UNEQUIPPED);
            if (ammoDef != UNEQUIPPED) {
                // New weapon is ammoless, takes priority.
                ammo = ammoDef;
                return;
            }
            // New weapon is not ammoless, fallback to ammunition slot.
            ammo = AmmoDefinition.AMMO_REQUIRING_WEAPONS.getOrDefault(ammoId, UNEQUIPPED);
        }
    }

    /**
     * Sets the cached ammo definition for this player.
     * <p>
     * This should usually only be used by combat or equipment systems that are synchronizing the cached ranged state
     * with the player's current equipment.
     *
     * @param ammo The ammo definition to cache.
     */
    public void setAmmo(AmmoDefinition ammo) {
        this.ammo = requireNonNullElse(ammo, UNEQUIPPED);
    }

    /**
     * @return The cached ammo definition.
     */
    public AmmoDefinition getAmmo() {
        return ammo;
    }
}