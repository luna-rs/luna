package io.luna.game.model.mob.combat.state;

import api.combat.magic.ImmobilizationAction;
import com.google.common.base.Stopwatch;
import engine.controllers.MultiCombatAreaListener;
import game.item.consumable.potion.PotionEffect;
import io.luna.game.action.ActionState;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.combat.CombatAction;
import io.luna.game.model.mob.combat.CombatDelayAction;
import io.luna.game.model.mob.combat.PoisonAction;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamageStack;
import io.luna.game.model.mob.combat.damage.CombatDamageType;

import java.util.Objects;

/**
 * Encapsulates combat state and shared combat behavior for a single {@link Mob}.
 *
 * @author lare96
 */
public abstract class CombatContext<T extends Mob> {

    /**
     * The amount of time a mob will remain in combat after attacking or being attacked.
     */
    public static final int COMBAT_TIMER_DURATION = 10;

    /**
     * The mob that owns this combat context.
     */
    protected final T mob;

    /**
     * Tracks pending and active combat damage for the owning mob.
     */
    private final CombatDamageStack damageStack;

    /**
     * Measures how long the mob has remained in combat since its last qualifying combat interaction.
     */
    private final Stopwatch combatTimer = Stopwatch.createUnstarted();

    /**
     * The mob currently being attacked, or {@code null} if no combat target is set.
     */
    protected Mob target;

    protected Mob autoRetaliateTarget;
    protected Mob lastCombatWith;

    /**
     * The number of ticks remaining before another attack may be performed.
     */
    private CombatDelayAction attackDelay;

    /**
     * The current poison severity.
     * <p>
     * A value greater than {@code 0} indicates that the mob is poisoned.
     */
    private int poisonSeverity;

    /**
     * Creates a new {@link CombatContext} for the supplied mob.
     *
     * @param mob The mob that owns this combat context.
     */
    public CombatContext(T mob) {
        this.mob = mob;
        damageStack = new CombatDamageStack(mob);
        attackDelay = new CombatDelayAction(mob);
    }

    /**
     * Computes the maximum hit for the supplied combat damage type.
     *
     * @param type The damage type being evaluated.
     * @return The maximum hit that can be dealt.
     */
    public abstract int getMaxHit(CombatDamageType type);

    // todo combat attack
    public abstract CombatAttack<?> getNextAttack(Mob victim);

    public abstract EquipmentBonus getAttackStyleBonus();

    /**
     * Gets the animation played when the owning mob defends against an incoming hit.
     *
     * @return The defence animation.
     */
    public abstract Animation getDefenceAnimation();

    /**
     * Determines whether the owning mob should automatically retaliate when attacked.
     *
     * @return {@code true} if auto-retaliate is enabled, otherwise {@code false}.
     */
    public abstract boolean isAutoRetaliate();

    /**
     * Starts attacking the supplied enemy.
     * <p>
     * If the owning mob is a {@link Player}, combat initiation is first validated by the player's active controllers.
     * If combat is allowed, the target is set, an interaction is established, and a {@link CombatAction} is submitted
     * if one is not already active.
     *
     * @param enemy The mob to attack.
     */
    public final void attack(Mob enemy) {
        if (mob instanceof Player) {
            Player player = (Player) mob;
            if (!player.getControllers().checkCombat(enemy)) {
                return;
            }
        }
        // todo differentiate
        target = enemy;
        mob.getActions().submitIfAbsent(new CombatAction(mob));
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
     * @param victim The {@link Mob} the player is attempting to attack.
     * @return {@code true} if combat is allowed; {@code false} otherwise.
     */
    public boolean checkMultiCombat(Mob victim) {
        // todo when target dies, if its equal to last combat, clear last combat with
        boolean attackerInMulti = MultiCombatAreaListener.INSTANCE.inside(mob.getPosition());
        boolean victimInMulti = MultiCombatAreaListener.INSTANCE.inside(victim.getPosition());
        if (attackerInMulti && victimInMulti) {
            // Both are in multi-area, proceed as normal.
            return true;
        }
        if (inCombat() && lastCombatWith != null && !Objects.equals(lastCombatWith, victim)) {
            mob.ifPlayer(player -> player.sendMessage("You are already in combat."));
            return false;
        }

        CombatContext<?> victimCombat = victim.getCombat();
        if (victimCombat.inCombat() && victimCombat.lastCombatWith != null && !Objects.equals(victimCombat.lastCombatWith, mob)) {
            mob.ifPlayer(player -> player.sendMessage("They are already in combat."));
            return false;
        }
        return true;
    }


    /**
     * Applies subclass-specific combat logic before attack execution proceeds.
     * <p>
     * This hook runs after target reachability has been evaluated, but before attack readiness is checked and
     * before the attack is launched. Subclasses may use it to block combat, trigger movement corrections, or prioritize
     * other behavior such as retreating.
     *
     * @param reached {@code true} if the current target has been reached according to the active interaction policy,
     * otherwise {@code false}.
     * @return {@code true} if combat processing should continue, otherwise {@code false}.
     */
    public boolean onCombatProcess(boolean reached) {
        return true;
    }

    // called when combataction ends
    public void onCombatFinished() {

    }

    /**
     * Gets the active {@link CombatDelayAction}, creating and submitting one if needed.
     *
     * @return The active combat delay action.
     */
    public CombatDelayAction getDelay() {
        if (attackDelay == null || attackDelay.getState() == ActionState.COMPLETED ||
                attackDelay.getState() == ActionState.INTERRUPTED) {
            attackDelay = new CombatDelayAction(mob);
        }
        if (attackDelay.getState() == ActionState.NEW) {
            mob.getActions().submitIfAbsent(attackDelay);
        }
        return attackDelay;
    }

    public Stopwatch stopCombatTimer() {
        return combatTimer;
    }

    /**
     * Resets and starts the combat timer.
     * <p>
     * This should be called whenever the owning mob performs or receives a combat interaction that should refresh
     * combat status.
     */
    public final void resetCombatTimer() {
        combatTimer.reset().start();
    }

    /**
     * Determines whether the owning mob is currently considered in combat.
     * <p>
     * A mob remains in combat while the combat timer is running and no more than {@link #COMBAT_TIMER_DURATION} seconds
     * have elapsed since the timer was last reset.
     *
     * @return {@code true} if the mob is still considered in combat, otherwise {@code false}.
     */
    public final boolean inCombat() {
        return combatTimer.isRunning() && combatTimer.elapsed().toSeconds() <= COMBAT_TIMER_DURATION;
    }


    /**
     * @return The combat damage stack.
     */
    public final CombatDamageStack getDamageStack() {
        return damageStack;
    }

    /**
     * Sets the current combat target.
     *
     * @param target The new target, or {@code null} if no target should be tracked.
     */
    public final void setTarget(Mob target) {
        this.target = target;
    }

    /**
     * @return The current target, or {@code null} if no target is active.
     */
    public final Mob getTarget() {
        return target;
    }

    /**
     * @return The poison severity.
     */
    public final int getPoisonSeverity() {
        return poisonSeverity;
    }

    /**
     * @return The poison severity before decrementing.
     */
    public final int decrementPoisonSeverity() {
        return poisonSeverity--;
    }

    /**
     * Sets the poison severity and starts poison processing if appropriate.
     *
     * @param poisonSeverity The new poison severity.
     */
    public final void setPoisonSeverity(int poisonSeverity) {
        setPoisonSeverity(poisonSeverity, true);
    }

    /**
     * Sets the poison severity, optionally scheduling poison processing.
     * <p>
     * If poison is successfully applied and {@code timer} is {@code true}, a {@link PoisonAction} is submitted if one
     * is not already active.
     *
     * @param newPoisonSeverity The new poison severity.
     * @param timer {@code true} to start poison processing when poison is applied, otherwise {@code false}.
     * @return {@code true} if the poison severity was updated, otherwise {@code false}.
     */
    public final boolean setPoisonSeverity(int newPoisonSeverity, boolean timer) {
        if (newPoisonSeverity > 0) {

            // Check if we're already poisoned.
            if (poisonSeverity > 0) {
                return false;
            }

            // Check if we have immunity to poison.
            if (mob instanceof Player && PotionEffect.Companion.hasAntiPoison((Player) mob)) {
                return false;
            }
        }
        poisonSeverity = newPoisonSeverity;
        if (poisonSeverity > 0 && timer) {
            mob.getActions().submitIfAbsent(new PoisonAction(mob, true));
        }
        return true;
    }

    /**
     * Determines whether the owning mob is currently poisoned.
     *
     * @return {@code true} if poison severity is above zero, otherwise {@code false}.
     */
    public final boolean isPoisoned() {
        return poisonSeverity > 0;
    }

    /**
     * Determines whether the owning mob is currently immobilized.
     * <p>
     * Immobilization is represented by the presence of an active {@link ImmobilizationAction}.
     *
     * @return {@code true} if the mob is immobilized, otherwise {@code false}.
     */
    public final boolean isImmobilized() {
        return mob.getActions().contains(ImmobilizationAction.class);
    }

    public T getMob() {
        return mob;
    }

    public Mob getAutoRetaliateTarget() {
        return autoRetaliateTarget;
    }

    public void setAutoRetaliateTarget(Mob autoRetaliateTarget) {
        this.autoRetaliateTarget = autoRetaliateTarget;
    }

    public Mob getLastCombatWith() {
        return lastCombatWith;
    }

    public void setLastCombatWith(Mob lastAttackedBy) {
        this.lastCombatWith = lastAttackedBy;
    }
}