package io.luna.game.model.mob.combat;

import com.google.common.base.Stopwatch;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.interact.InteractionPolicy;

/**
 * Encapsulates all combat-related state and behavior for a single {@link Mob}.
 * <p>
 * Concrete implementations supply the rules for how the owning mob fights, while this base class manages the shared
 * combat lifecycle and action integration.
 *
 * @author lare96
 */
public abstract class CombatContext {

    /**
     * The mob that owns this combat context.
     */
    private final Mob mob;

    /**
     * Tracks pending and recently applied combat damage for the owning mob.
     */
    private final CombatDamageStack damageStack;

    /**
     * Measures how long the mob has remained in combat since the last combat interaction.
     */
    private final Stopwatch combatTimer = Stopwatch.createUnstarted();

    /**
     * The mob currently being attacked by the owner, or {@code null} if no target is active.
     */
    private Mob target;

    /**
     * The number of ticks remaining until the next attack may be performed.
     */
    private int attackDelay;

    /**
     * Creates a new combat context for {@code mob}.
     *
     * @param mob The mob that owns this combat context.
     */
    public CombatContext(Mob mob) {
        this.mob = mob;
        damageStack = new CombatDamageStack(mob);
    }

    /**
     * Computes the maximum possible hit for the supplied combat damage type.
     *
     * @param type The damage type being calculated.
     * @return The maximum hit that can be dealt.
     */
    public abstract int computeMaxHit(CombatDamageType type);

    /**
     * Computes the interaction policy used to determine whether the owning mob has reached its current target
     * for combat.
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
     * Determines whether the owning mob will automatically retaliate when attacked.
     *
     * @return {@code true} if auto-retaliate is enabled, otherwise {@code false}.
     */
    public abstract boolean isAutoRetaliate();

    /**
     * Starts attacking {@code enemy}.
     * <p>
     * For players, combat initiation is first validated by the active controller chain.
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
     * Decrements the remaining attack delay by one tick if it is greater than zero.
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
     * Readiness is delegated to the active {@link CombatDelayAction}.
     *
     * @return {@code true} if an attack may be performed, otherwise {@code false}.
     */
    public boolean isAttackReady() {
        return getDelay().isReady();
    }

    /**
     * Resets the attack delay after an attack is launched.
     * <p>
     * The delay is set from {@link #computeAttackSpeed()} and the underlying {@link CombatDelayAction} is marked as
     * not ready until the delay expires.
     */
    public void resetAttackDelay() {
        attackDelay = computeAttackSpeed();
        getDelay().setReady(false);
    }

    /**
     * Gets the active combat delay action for the owning mob, creating and submitting one if necessary.
     *
     * @return The combat delay action.
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
     * its combat state.
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
     * A mob is treated as in combat while the combat timer is running and no more than 15 seconds have elapsed since
     * the timer was last reset.
     *
     * @return {@code true} if the mob is still in combat, otherwise {@code false}.
     */
    public boolean inCombat() {
        return combatTimer.isRunning() && combatTimer.elapsed().toSeconds() <= 15;
    }

    /**
     * Gets the remaining attack delay in ticks.
     *
     * @return The current attack delay.
     */
    public int getAttackDelay() {
        return attackDelay;
    }

    /**
     * Gets the damage stack associated with the owning mob.
     *
     * @return The combat damage stack.
     */
    public CombatDamageStack getDamageStack() {
        return damageStack;
    }

    /**
     * Sets the current combat target.
     *
     * @param target The new target, or {@code null} if combat should no longer track one.
     */
    void setTarget(Mob target) {
        this.target = target;
    }

    /**
     * Gets the current combat target.
     *
     * @return The current target, or {@code null} if no target is active.
     */
    public Mob getTarget() {
        return target;
    }
}