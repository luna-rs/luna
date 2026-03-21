package io.luna.game.model.mob.combat;

import api.combat.magic.TeleBlockAction;
import engine.combat.prayer.CombatPrayerSet;
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
        if(target == null || (target.getPosition().equals(player.getPosition()) && reached)) {
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
}