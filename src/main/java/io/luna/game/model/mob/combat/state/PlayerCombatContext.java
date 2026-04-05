package io.luna.game.model.mob.combat.state;

import api.combat.magic.TeleBlockAction;
import engine.combat.prayer.CombatPrayerSet;
import game.item.degradable.DegradableItems;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.combat.CombatFormula;
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.PoisonAction;
import io.luna.game.model.mob.combat.Weapon;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.attack.PlayerMagicCombatAttack;
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack;
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
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
     * {@link CombatContext#getNextAttack(Mob, boolean)}.
     */
    private CombatAttack<Player> firstAttack;


    private CombatAttack<Player> specialAttack;

    private CombatAttack<Player> instantSpecialAttack;

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
        // Handle Barrows degradation and check controllers to make sure we can attack.
        DegradableItems.INSTANCE.handleBarrowsEquipment(player);
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
    public CombatAttack<Player> getNextAttack(Mob victim, boolean attackReady) {

        // Handle cached initial attack from interaction code.
        if (firstAttack != null) {
            CombatAttack<Player> nextAttack = firstAttack;
            firstAttack = null;
            return nextAttack;
        }

        // TODO cached instant special attack


        // TODO: Special attacks.
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
    public boolean isAutoRetaliate() {
        return player.getVarpManager().getValue(PersistentVarp.AUTO_RETALIATE) == 0;
    }

    @Override
    public void onCombatFinished() {
        firstAttack = null;
        specialAttack = null;
        instantSpecialAttack = null;
    }

    @Override
    public EquipmentBonus getAttackStyleBonus() {
        return weapon.getStyleDef().getBonus();
    }

    @Override
    public void onNextDefence(Mob attacker, CombatDamage damage) {
        int animationId = 410;
        if (player.getEquipment().occupied(Equipment.SHIELD)) {
            animationId = 1156;
        } else if (weapon.getType() == Weapon.STAFF) {
            animationId = 420;
        } else if (weapon.getType() == Weapon.DAGGER) {
            animationId = 403;
        }
        player.animation(new Animation(animationId));
    }

    /**
     * Restores persistent combat status actions after the player logs in.
     * <p>
     * If the player still has active status effects stored on login, the corresponding action is resubmitted so the
     * effect continues processing normally.
     */
    public void onLogin() {
        if (getPoisonSeverity() > 0) {
            player.getActions().submitIfAbsent(new PoisonAction(player, false));
        }
        if (magic.getTeleBlock() > 0) {
            player.getActions().submitIfAbsent(new TeleBlockAction(player));
        }
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

    public CombatAttack<Player> getSpecialAttack() {
        return specialAttack;
    }

    public void setSpecialAttack(CombatAttack<Player> specialAttack) {
        this.specialAttack = specialAttack;
    }

    public CombatAttack<Player> getInstantSpecialAttack() {
        return instantSpecialAttack;
    }

    public void setInstantSpecialAttack(CombatAttack<Player> instantSpecialAttack) {
        this.instantSpecialAttack = instantSpecialAttack;
    }
}