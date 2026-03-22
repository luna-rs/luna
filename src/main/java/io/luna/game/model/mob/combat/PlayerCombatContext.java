package io.luna.game.model.mob.combat;

import api.combat.magic.TeleBlockAction;
import engine.combat.prayer.CombatPrayerSet;
import engine.controllers.MultiCombatAreaListener;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.item.Equipment;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;
import io.luna.game.model.mob.varp.PersistentVarp;

import java.util.Objects;

/**
 * A {@link CombatContext} implementation for {@link Player}s.
 * <p>
 * This class provides player-specific combat rules.
 *
 * @author lare96
 */
public final class PlayerCombatContext extends CombatContext {

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
    private final CombatWeapon weapon;

    /**
     * Manages the player's special attack bar and related behavior.
     */
    private final CombatSpecialBar specialBar;

    /**
     * The remaining Tele Block duration in ticks.
     */
    private int teleBlock;

    /**
     * The ammo this player is currently using.
     */
    private AmmoDefinition ammo;

    /**
     * Creates a new {@link PlayerCombatContext}.
     *
     * @param player The owning player.
     */
    public PlayerCombatContext(Player player) {
        super(player);
        this.player = player;
        prayers = new CombatPrayerSet(player);
        weapon = new CombatWeapon(player);
        specialBar = new CombatSpecialBar(player, this);
    }

    @Override
    public int computeMaxHit(CombatDamageType type) {
        if (type == CombatDamageType.MAGIC) {
            // TODO Roll for currently selected spell, if not available than currently autocasted spell, if not
            //  available than throw exception.
            return CombatFormula.computeMagicalMaxHit(player);
        } else {
            return CombatFormula.computePhysicalMaxHit(player, type == CombatDamageType.RANGED ?
                    PhysicalType.RANGED : PhysicalType.MELEE);
        }
    }

    @Override
    public InteractionPolicy computeInteractionPolicy() {
        int range = weapon.getRange();
        return range < 2 ? new InteractionPolicy(InteractionType.SIZE, 1) :
                new InteractionPolicy(InteractionType.LINE_OF_SIGHT, range);
    }

    @Override
    public int computeAttackSpeed() {
        return weapon.getStyleDef().getSpeed();
    }

    @Override
    public Animation getAttackAnimation() {
        return new Animation(weapon.getStyleDef().getAnimation(), AnimationPriority.HIGH);
    }

    @Override
    public Animation getDefenceAnimation() {
        if (player.getEquipment().occupied(Equipment.SHIELD)) {
            return new Animation(1156);
        } else if (weapon.getType() == Weapon.STAFF) {
            return new Animation(420);
        } else if (weapon.getType() == Weapon.DAGGER) {
            return new Animation(403);
        }
        return new Animation(410);
    }

    @Override
    public boolean isAutoRetaliate() {
        return player.getVarpManager().getValue(PersistentVarp.AUTO_RETALIATE) == 0;
    }

    @Override
    public boolean onCombatHook(boolean reached) {
        Mob target = getTarget();
        if (target == null || (target.getPosition().equals(player.getPosition()) && reached)) {
            return false;
        }
        return true;
    }

    /**
     * Restores persistent combat status actions after the player logs in.
     * <p>
     * If the player still has active poison or Tele Block state stored on login, the corresponding action is
     * resubmitted so the effect continues processing normally.
     */
    public void onLogin() {
        if (getPoisonSeverity() > 0) {
            player.getActions().submitIfAbsent(new PoisonAction(player, false));
        }
        if (getTeleBlock() > 0) {
            player.getActions().submitIfAbsent(new TeleBlockAction(player));
        }
    }

    /**
     * Validates whether the player can initiate combat with the specified {@link Mob} under single-combat rules.
     * <p>
     * If the player isn't already in a multi-combat area, this method enforces standard 317-style single-combat
     * restrictions:
     * <ul>
     *     <li>If the player is already fighting a different target, they cannot attack another.</li>
     *     <li>If the target is already fighting someone else, the attack is prevented.</li>
     * </ul>
     * <p>
     * If either condition fails, a message is sent to the player explaining why the attack cannot proceed.
     *
     * @param other The {@link Mob} the player is attempting to attack.
     * @return {@code true} if combat is allowed; {@code false} otherwise.
     */
    public boolean checkMultiCombat(Mob other) {
        Mob target = player.getCombat().getTarget();
        if (target != null && !player.getControllers().contains(MultiCombatAreaListener.INSTANCE)) {
            if (player.getCombat().inCombat() && !Objects.equals(target, other)) {
                player.sendMessage("You are already in combat.");
                return false;
            } else if (other.getCombat().inCombat() && !Objects.equals(target, player)) {
                player.sendMessage("That player is already in combat.");
                return false;
            }
        }
        return true;
    }

    /**
     * Ensures the target {@link Mob} is a valid non-player combat target.
     * <p>
     * This method prevents attacking {@link Player} instances in areas where player-vs-player combat is disallowed.
     * It is typically used in NPC-only combat zones or safe regions.
     * <p>
     * If the target is a player, the attack is blocked and the attacker receives a message explaining the restriction.
     *
     * @param other The {@link Mob} the player is attempting to attack.
     * @return {@code true} if the target is a valid NPC combat target; {@code false} otherwise.
     */
    public boolean checkCombatMob(Mob other) {
        if (other instanceof Player) {
            player.sendMessage("You cannot attack players here.");
            return false;
        }
        return true;
    }

    /**
     * @return The active attack-style bonus.
     */
    public EquipmentBonus getAttackStyleBonus() {
        return weapon.getStyleDef().getBonus();
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
    public CombatWeapon getWeapon() {
        return weapon;
    }

    /**
     * @return The special attack bar helper.
     */
    public CombatSpecialBar getSpecialBar() {
        return specialBar;
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
     * @return The ammo this player is currently using.
     */
    public AmmoDefinition getAmmo() {
        return ammo;
    }

    // ammo of null indicates incorrect bow/ammo pair used or no ammo equipped
    public void setAmmo() {
        int id = ammo != null ? ammo.getId() : -1;
/*
        int slot = player.getWeapon() == WeaponInterface.SHORTBOW || player.getWeapon() == WeaponInterface.LONGBOW || player.getWeapon() == WeaponInterface.CROSSBOW
            ? Equipment.ARROWS_SLOT : Equipment.WEAPON_SLOT;
        if (Combat.isCrystalBow(player))
            return Optional.of(CombatRangedAmmo.CRYSTAL_ARROW);
        if (player.getEquipment().get(slot) == null)
            return Optional.empty();
        return Arrays.stream(CombatRangedAmmo.values()).filter(
            c -> player.getEquipment().get(slot).getDefinition().getName().toLowerCase().contains(c.toString())).findFirst();
    }
 */
        // Check if current arrows can be fired with this bow
    }
}