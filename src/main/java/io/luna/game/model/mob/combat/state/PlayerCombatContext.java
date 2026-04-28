package io.luna.game.model.mob.combat.state;

import api.combat.player.PlayerCombatHandler;
import api.combat.specialAttack.SpecialAttackHandler;
import api.combat.specialAttack.dsl.SpecialAttackBuilderReceiver;
import api.combat.specialAttack.dsl.SpecialAttackDataReceiver;
import engine.combat.prayer.CombatPrayerSet;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.def.WeaponAnimationDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerContextMenuOption;
import io.luna.game.model.mob.combat.AmmoType;
import io.luna.game.model.mob.combat.CombatFormula;
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.attack.PlayerMagicCombatAttack;
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack;
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.varp.PersistentVarp;

import static io.luna.game.model.def.CombatSpellDefinition.NONE;

/**
 * A {@link CombatContext} implementation for {@link Player}s.
 * <p>
 * This class provides player-specific combat state and behavior, including prayer handling, weapon state, special
 * attacks, ranged ammunition state, magic state, and attack construction.
 *
 * @author lare96
 */
public final class PlayerCombatContext extends CombatContext<Player> {

    /**
     * The owning player.
     */
    private final Player player;

    /**
     * The player's active combat prayer set.
     */
    private final CombatPrayerSet prayers;

    /**
     * Provides weapon-derived combat properties for the player.
     */
    private final PlayerCombatWeapon weapon;

    /**
     * Manages the player's special attack bar state and related behavior.
     */
    private final PlayerWeaponSpecialBar specialBar;

    /**
     * Tracks the player's ranged weapon and ammunition state.
     */
    private final PlayerRangedCombat ranged;

    /**
     * Tracks the player's magic combat state, including selected spells, autocast state, and Tele Block status.
     */
    private final PlayerMagicCombat magic;

    /**
     * A cached first attack supplied externally, usually by interaction code.
     * <p>
     * When present, this attack is consumed before normal attack selection logic in
     * {@link CombatContext#getNextAttack(Mob)}.
     */
    private CombatAttack<Player> firstAttack;

    /**
     * Creates a new {@link PlayerCombatContext}.
     *
     * @param player The owning player.
     */
    public PlayerCombatContext(Player player) {
        super(player);
        this.player = player;
        prayers = new CombatPrayerSet(player);
        weapon = new PlayerCombatWeapon(player);
        specialBar = new PlayerWeaponSpecialBar(player, this);
        ranged = new PlayerRangedCombat(player);
        magic = new PlayerMagicCombat(player);
    }

    @Override
    public boolean onCombatProcess(boolean reached) {
        // Handle combat hooks and check controllers to make sure we can attack.
        PlayerCombatHandler.INSTANCE.consumeCombat(player);
        return target != null && player.getControllers().checkCombat(target);
    }

    @Override
    public int getDefaultMaxHit(CombatDamageType type) {
        if (type == CombatDamageType.MAGIC) {
            if (magic.getSelectedSpell() != NONE) {
                return magic.getSelectedSpell().getMaxHit();
            } else if (magic.getAutocastSpell() != NONE) {
                return magic.getAutocastSpell().getMaxHit();
            } else {
                throw new IllegalStateException("No selected or autocasted combat spell to get the max hit from.");
            }
        } else {
            return CombatFormula.calculatePhysicalMaxHit(player, type == CombatDamageType.RANGED ?
                    PhysicalType.RANGED : PhysicalType.MELEE);
        }
    }

    @Override
    public CombatAttack<Player> getNextAttack(Mob victim) {

        // Handle cached initial attack from interaction code.
        if (firstAttack != null) {
            CombatAttack<Player> nextAttack = firstAttack;
            firstAttack = null;
            return nextAttack;
        }

        // Combat hooks from scripts always take first priority.
        CombatAttack<Player> hook = PlayerCombatHandler.INSTANCE.supplyAttack(player, victim);
        if (hook != null) {
            return hook;
        }

        // Handle a special attack if applicable.
        if (specialBar.isActivated() && !magic.isCasting()) {
            SpecialAttackDataReceiver receiver = SpecialAttackHandler.INSTANCE.specialAttackData(this);
            CombatAttack<Player> attack =
                    receiver.getAttackTransformer().invoke(new SpecialAttackBuilderReceiver(mob, victim, receiver));
            if (attack != null) {
                return attack;
            }
        }
        return getDefaultAttack(victim);
    }

    @Override
    public boolean isAutoRetaliate() {
        return player.getVarpManager().getValue(PersistentVarp.AUTO_RETALIATE) == 0;
    }

    @Override
    public void onCombatFinished() {
        firstAttack = null;
    }

    @Override
    public EquipmentBonus getAttackStyleBonus() {
        return weapon.getStyleDef().getBonus();
    }

    @Override
    public void onNextDefence(Mob attacker, CombatDamageAction action) {
        PlayerCombatHandler.INSTANCE.consumeDefence(mob, attacker, action);
    }

    @Override
    public CombatAttack<Player> getDefaultAttack(Mob victim) {
        if (magic.isCasting()) {
            // Prepare a magic attack if we're auto-casting or have a spell selected.
            boolean selected = magic.getSelectedSpell() != NONE;
            CombatSpellDefinition castSpell = selected ? magic.getSelectedSpell() : magic.getAutocastSpell();
            return new PlayerMagicCombatAttack(player, victim, castSpell, selected);
        } else if (weapon.isRanged()) {
            return new PlayerRangedCombatAttack(player, victim);
        } else {
            return new PlayerMeleeCombatAttack(player, victim, weapon.getStyleDef());
        }
    }

    @Override
    public boolean isAttackable() {
        return player.isAlive() && player.getContextMenu().contains(PlayerContextMenuOption.ATTACK);
    }

    @Override
    public int getAttackAnimation(CombatDamageType type) {
        if (type == CombatDamageType.RANGED && getAmmoDef().getType() == AmmoType.BOLT_RACK) {
            // Karils x-bow exception.
            return 2075;
        }
        int weaponId = player.getEquipment().computeIdForIndex(Equipment.WEAPON);
        if (weaponId > 0) {
            WeaponAnimationDefinition weaponAnimationDef = WeaponAnimationDefinition.ALL.get(weaponId);
            if (weaponAnimationDef != null) {
                int animation = weaponAnimationDef.getAttackAnimation(weapon.getStyleDef().getType());
                if(animation != -1) {
                    return animation;
                }
            }
        }
        // Otherwise, use the default attack animation from 'weapon_type_data.jsonc'.
        return weapon.getStyleDef().getAttackAnimation();
    }

    @Override
    public int getDefenceAnimation(CombatDamageType type) {
        int shieldId = player.getEquipment().computeIdForIndex(Equipment.SHIELD);
        if (shieldId > 0 && type != CombatDamageType.MAGIC) {
            // Block animation for non-magic based attacks when a shield is equipped.
            return 1156;
        }

        int weaponId = player.getEquipment().computeIdForIndex(Equipment.WEAPON);
        if (weaponId > 0) {
            WeaponAnimationDefinition weaponAnimationDef = WeaponAnimationDefinition.ALL.get(weaponId);
            if (weaponAnimationDef != null) {
                int animation = weaponAnimationDef.getDefenceAnimation(weapon.getStyleDef().getType());
                if (animation != -1) {
                    return animation;
                }
            }
        }
        // Otherwise, use the default defence animation from 'weapon_type_data.jsonc'.
        return weapon.getStyleDef().getDefenceAnimation();
    }

    /**
     * @return The combat prayer set.
     */
    public CombatPrayerSet getPrayers() {
        return prayers;
    }

    /**
     * @return The combat weapon helper.
     */
    public PlayerCombatWeapon getWeapon() {
        return weapon;
    }

    /**
     * @return The special attack bar helper.
     */
    public PlayerWeaponSpecialBar getSpecialBar() {
        return specialBar;
    }

    /**
     * @return The active combat style definition.
     */
    public CombatStyleDefinition getStyleDef() {
        return weapon.getStyleDef();
    }

    /**
     * @return The current ammo definition.
     */
    public AmmoDefinition getAmmoDef() {
        return ranged.getAmmo();
    }

    /**
     * @return The ranged combat helper.
     */
    public PlayerRangedCombat getRanged() {
        return ranged;
    }

    /**
     * @return The magic combat helper.
     */
    public PlayerMagicCombat getMagic() {
        return magic;
    }

    /**
     * @return The active combat stance.
     */
    public CombatStance getStance() {
        return weapon.getStyleDef().getStance();
    }

    /**
     * @return The cached first attack, or {@code null} if none is set.
     */
    public CombatAttack<Player> getFirstAttack() {
        return firstAttack;
    }

    /**
     * Sets the cached first attack.
     *
     * @param firstAttack The first attack to cache, or {@code null} to clear it.
     */
    public void setFirstAttack(CombatAttack<Player> firstAttack) {
        this.firstAttack = firstAttack;
    }
}