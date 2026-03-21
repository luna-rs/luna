package io.luna.game.model.mob.combat;

import api.combat.magic.ImmobilizationAction;
import com.google.common.base.Stopwatch;
import game.item.consumable.potion.PotionEffect;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.interact.InteractionPolicy;

/**
 * Encapsulates combat state and shared combat behavior for a single {@link Mob}.
 *
 * @author lare96
 */
public abstract class CombatContext {

    /**
     * The mob that owns this combat context.
     */
    private final Mob mob;

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
    private Mob target;

    /**
     * The number of ticks remaining before another attack may be performed.
     */
    private int attackDelay;

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
    public CombatContext(Mob mob) {
        this.mob = mob;
        damageStack = new CombatDamageStack(mob);
    }

    /**
     * Computes the maximum hit for the supplied combat damage type.
     *
     * @param type The damage type being evaluated.
     * @return The maximum hit that can be dealt.
     */
    public abstract int computeMaxHit(CombatDamageType type);

    /**
     * Computes the interaction policy used to determine whether the owning mob has reached its current target for'
     * combat purposes.
     *
     * @return The combat interaction policy.
     */
    public abstract InteractionPolicy computeInteractionPolicy();

    /**
     * Computes the attack speed of the owning mob in ticks.
     *
     * @return The number of ticks between attacks.
     */
    public abstract int computeAttackSpeed();

    /**
     * Gets the animation played when the owning mob attacks.
     *
     * @return The attack animation.
     */
    public abstract Animation getAttackAnimation();

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
    public abstract boolean onCombatHook(boolean reached);

    /**
     * Starts attacking the supplied enemy.
     * <p>
     * If the owning mob is a {@link Player}, combat initiation is first validated by the player's active controllers.
     * If combat is allowed, the target is set, an interaction is established, and a {@link CombatAction} is submitted
     * if one is not already active.
     *
     * @param enemy The mob to attack.
     */
    public void attack(Mob enemy) {
        if (mob instanceof Player) {
            Player player = (Player) mob;
            if (!player.getControllers().checkCanFight(enemy)) {
                return;
            }
        }

        target = enemy;
        mob.interact(target);
        mob.getActions().submitIfAbsent(new CombatAction(mob));
    }

    /**
     * Decrements the remaining attack delay by one tick if it is above zero.
     *
     * @return The updated attack delay.
     */
    public int decrementAttackDelay() {
        if (attackDelay > 0) {
            attackDelay--;
        }
        return attackDelay;
    }

    /**
     * Determines whether the owning mob is ready to perform its next attack.
     * <p>
     * Attack readiness is delegated to the active {@link CombatDelayAction}.
     *
     * @return {@code true} if an attack may be performed, otherwise {@code false}.
     */
    public boolean isAttackReady() {
        return getDelay().isReady();
    }

    /**
     * Resets the attack delay after an attack is launched.
     * <p>
     * The new delay is derived from {@link #computeAttackSpeed()}, and the active {@link CombatDelayAction} is
     * marked as not ready until the delay expires.
     */
    public void resetAttackDelay() {
        attackDelay = computeAttackSpeed();
        getDelay().setReady(false);
    }

    /**
     * Gets the active {@link CombatDelayAction}, creating and submitting one if needed.
     *
     * @return The active combat delay action.
     */
    private CombatDelayAction getDelay() {
        CombatDelayAction action = mob.getActions().first(CombatDelayAction.class);
        if (action == null) {
            action = new CombatDelayAction(mob);
            mob.getActions().submitIfAbsent(action);
        }
        return action;
    }

    /**
     * Clears the current attack delay immediately.
     */
    public void clearAttackDelay() {
        attackDelay = 0;
    }

    /**
     * Resets and starts the combat timer.
     * <p>
     * This should be called whenever the owning mob performs or receives a combat interaction that should refresh
     * combat status.
     */
    public void resetCombatTimer() {
        combatTimer.reset().start();
    }

    /**
     * Stops the combat timer.
     */
    public void stopCombatTimer() {
        combatTimer.stop();
    }

    /**
     * Determines whether the owning mob is currently considered in combat.
     * <p>
     * A mob remains in combat while the combat timer is running and no more than {@code 15} seconds have elapsed
     * since the timer was last reset.
     *
     * @return {@code true} if the mob is still considered in combat, otherwise
     * {@code false}.
     */
    public boolean inCombat() {
        return combatTimer.isRunning() && combatTimer.elapsed().toSeconds() <= 15;
    }

    /**
     * @return The current attack delay.
     */
    public int getAttackDelay() {
        return attackDelay;
    }

    /**
     * @return The combat damage stack.
     */
    public CombatDamageStack getDamageStack() {
        return damageStack;
    }

    /**
     * Sets the current combat target.
     *
     * @param target The new target, or {@code null} if no target should be tracked.
     */
    void setTarget(Mob target) {
        this.target = target;
    }

    /**
     * @return The current target, or {@code null} if no target is active.
     */
    public Mob getTarget() {
        return target;
    }

    /**
     * @return The poison severity.
     */
    public int getPoisonSeverity() {
        return poisonSeverity;
    }

    /**
     * @return The poison severity before decrementing.
     */
    int decrementPoisonSeverity() {
        return poisonSeverity--;
    }

    /**
     * Sets the poison severity and starts poison processing if appropriate.
     *
     * @param poisonSeverity The new poison severity.
     */
    public void setPoisonSeverity(int poisonSeverity) {
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
    public boolean setPoisonSeverity(int newPoisonSeverity, boolean timer) {
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
    public boolean isPoisoned() {
        return poisonSeverity > 0;
    }

    /**
     * Determines whether the owning mob is currently immobilized.
     * <p>
     * Immobilization is represented by the presence of an active {@link ImmobilizationAction}.
     *
     * @return {@code true} if the mob is immobilized, otherwise {@code false}.
     */
    public boolean isImmobilized() {
        return mob.getActions().contains(ImmobilizationAction.class);
    }
}