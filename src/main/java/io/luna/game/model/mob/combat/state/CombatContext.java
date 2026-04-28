package io.luna.game.model.mob.combat.state;

import com.google.common.base.Stopwatch;
import engine.combat.status.StatusEffectType;
import engine.controllers.MultiCombatAreaListener;
import game.combat.npcHooks.skeletalWyvern.WyvernIcyBreathStatusEffect;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatAction;
import io.luna.game.model.mob.combat.attack.CombatAttack;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.damage.CombatDamageStack;
import io.luna.game.model.mob.combat.damage.CombatDamageType;

import java.util.Objects;

/**
 * Encapsulates combat state and shared combat behavior for a single {@link Mob}.
 *
 * @param <T> The mob type that owns this combat context.
 * @author lare96
 */
public abstract class CombatContext<T extends Mob> {

    /**
     * The number of seconds a mob remains flagged as in combat after its most recent qualifying combat interaction.
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

    /**
     * The mob that should be attacked automatically if auto-retaliate is triggered.
     */
    protected Mob autoRetaliateTarget;

    /**
     * The last mob this mob exchanged combat with.
     * <p>
     * This is used for single-combat validation and combat ownership checks.
     */
    protected Mob lastCombatWith;

    /**
     * The number of game ticks remaining before this mob may perform another attack.
     * <p>
     * A value of {@code 0} or less means the mob is currently able to attack.
     */
    private int attackDelay;

    /**
     * Indicates whether this mob is fully disabled.
     * <p>
     * A disabled mob is prevented from performing combat actions until the disabled state is cleared.
     */
    private boolean disabled;

    /**
     * The most recent combat attack that successfully applied to this mob.
     * <p>
     * This can be used by combat scripts and post-hit logic to inspect the attack that produced the latest hit.
     */
    private CombatAttack<?> lastAttackReceived;

    /**
     * The most recent combat damage received by this mob.
     * <p>
     * This is updated when damage is applied and can be used by combat scripts, prayers, effects, or listeners
     * that need to inspect the previous incoming hit.
     */
    private CombatDamage lastDamageReceived;

    /**
     * Creates a new combat context for the supplied mob.
     *
     * @param mob The mob that owns this combat context.
     */
    public CombatContext(T mob) {
        this.mob = mob;
        damageStack = new CombatDamageStack(mob);
    }

    /**
     * Computes the default maximum hit for the supplied combat damage type.
     *
     * @param type The damage type being evaluated.
     * @return The default maximum hit for that damage type.
     */
    public abstract int getDefaultMaxHit(CombatDamageType type);

    /**
     * Resolves the next attack that should be performed against the supplied victim.
     *
     * @param victim The current combat target.
     * @return The next combat attack to execute.
     */
    public abstract CombatAttack<?> getNextAttack(Mob victim);

    /**
     * Applies subclass-specific defence logic and damage modifications before this mob receives damage.
     *
     * @param attacker The mob dealing the damage.
     * @param action The action that launched the damage application.
     */
    public abstract void onNextDefence(Mob attacker, CombatDamageAction action);

    /**
     * Gets the attack-style equipment bonus currently used for accuracy calculations.
     *
     * @return The active attack-style equipment bonus.
     */
    public abstract EquipmentBonus getAttackStyleBonus();

    /**
     * Determines whether the owning mob should automatically retaliate when attacked.
     *
     * @return {@code true} if auto-retaliate is enabled, otherwise {@code false}.
     */
    public abstract boolean isAutoRetaliate();

    /**
     * Creates the default attack for this mob against the specified victim.
     * <p>
     * The default attack uses the mob's configured attack animation, range, speed, and damage behavior.
     *
     * @param victim The current combat target.
     * @return The default combat attack.
     */
    public abstract CombatAttack<?> getDefaultAttack(Mob victim);

    /**
     * Determines whether this mob is currently valid and able to enter combat.
     *
     * @return {@code true} if this mob can be attacked, otherwise {@code false}.
     */
    public abstract boolean isAttackable();

    /**
     * Gets the attack animation used for the supplied damage type.
     *
     * @param type The damage type being performed.
     * @return The attack animation id.
     */
    public abstract int getAttackAnimation(CombatDamageType type);

    /**
     * Gets the defence animation used when receiving the supplied damage type.
     *
     * @param type The damage type being received.
     * @return The defence animation id.
     */
    public abstract int getDefenceAnimation(CombatDamageType type);

    /**
     * Starts attacking the supplied enemy.
     * <p>
     * If the owning mob is a {@link Player}, combat initiation is first validated by the player's active controllers.
     * If combat is allowed, the target is set and a {@link CombatAction} is submitted if one is not already active.
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
        target = enemy;
        mob.getActions().submitIfAbsent(new CombatAction(mob));
    }

    /**
     * Validates whether this mob can initiate combat with the specified victim under single-combat rules.
     * <p>
     * If both mobs are inside a multi-combat area, combat is allowed. Otherwise, standard single-combat ownership
     * checks are enforced:
     * <ul>
     *     <li>If this mob is already fighting a different target, the attack is prevented.</li>
     *     <li>If the victim is already fighting someone else, the attack is prevented.</li>
     * </ul>
     *
     * @param victim The mob this mob is attempting to attack.
     * @return {@code true} if combat is allowed, otherwise {@code false}.
     */
    public boolean checkMultiCombat(Mob victim) {
        boolean attackerInMulti = MultiCombatAreaListener.INSTANCE.inside(mob.getPosition());
        boolean victimInMulti = MultiCombatAreaListener.INSTANCE.inside(victim.getPosition());

        if (attackerInMulti && victimInMulti) {
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
     * This hook runs after target reachability has been evaluated, but before attack readiness is checked and before
     * the attack is launched. Subclasses may use it to block combat, trigger movement corrections, or prioritize other
     * behavior such as retreating.
     *
     * @param reached {@code true} if the current target has been reached by the active interaction policy.
     * @return {@code true} if combat processing should continue, otherwise {@code false}.
     */
    public boolean onCombatProcess(boolean reached) {
        return true;
    }

    /**
     * Invoked when combat processing finishes for the owning mob.
     * <p>
     * Subclasses may override this to clear temporary state or perform post-combat cleanup.
     */
    public void onCombatFinished() {

    }

    /**
     * Gets the stopwatch used to track how long the owning mob has remained in combat.
     *
     * @return The combat timer stopwatch.
     */
    public Stopwatch getTimer() {
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
     * A mob remains in combat while the combat timer is running and no more than {@link #COMBAT_TIMER_DURATION}
     * seconds have elapsed since the timer was last reset.
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
     * @return The owning mob.
     */
    public T getMob() {
        return mob;
    }

    /**
     * @return The mob to retaliate against, or {@code null} if none is set.
     */
    public Mob getAutoRetaliateTarget() {
        return autoRetaliateTarget;
    }

    /**
     * Sets the pending auto-retaliate target.
     *
     * @param autoRetaliateTarget The mob to retaliate against, or {@code null} to clear it.
     */
    public void setAutoRetaliateTarget(Mob autoRetaliateTarget) {
        this.autoRetaliateTarget = autoRetaliateTarget;
    }

    /**
     * @return The last tracked combat opponent, or {@code null} if none is set.
     */
    public Mob getLastCombatWith() {
        return lastCombatWith;
    }

    /**
     * Sets the last mob this mob exchanged combat with.
     *
     * @param lastAttackedBy The last tracked combat opponent.
     */
    public void setLastCombatWith(Mob lastAttackedBy) {
        this.lastCombatWith = lastAttackedBy;
    }

    /**
     * @return The current attack delay in game ticks.
     */
    public int getAttackDelay() {
        return attackDelay;
    }

    /**
     * Processes one tick of attack delay.
     * <p>
     * This decrements the remaining delay so the mob moves closer to being able to attack again.
     */
    public void processAttackDelay() {
        if (attackDelay > 0) {
            attackDelay--;
        }
    }

    /**
     * Sets the number of ticks that must elapse before this mob may attack again.
     *
     * @param attackDelay The new attack delay in game ticks.
     */
    public void setAttackDelay(int attackDelay) {
        this.attackDelay = attackDelay;
    }

    /**
     * Extends the current attack delay after food is eaten.
     * <p>
     * This only applies an additional delay if an attack cooldown is already active. If the current attack delay is
     * zero or less, no delay is added.
     *
     * @param delay The additional delay to add.
     */
    public void addFoodDelay(int delay) {
        if (attackDelay > 0) {
            attackDelay += delay;
        }
    }

    /**
     * Determines whether this mob may perform an attack immediately.
     *
     * @return {@code true} if the attack delay has fully elapsed, otherwise {@code false}.
     */
    public boolean isAttackReady() {
        return attackDelay <= 0;
    }

    /**
     * Determines whether this mob is currently combat-disabled.
     *
     * @return {@code true} if this mob is combat-disabled, otherwise {@code false}.
     */
    public boolean isDisabled() {
        return disabled || mob.isLocked() || mob.getStatus().isStunned() ||
                mob.getStatus().has(StatusEffectType.IMMOBILIZED, WyvernIcyBreathStatusEffect.class);
    }

    /**
     * Sets whether this mob's combat is disabled.
     * <p>
     * When the disabled state is enabled, the mob's current interaction is cleared.
     *
     * @param disabled {@code true} to disable this mob, otherwise {@code false}.
     */
    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            this.disabled = disabled;
            if (this.disabled) {
                mob.interact(null);
            }
        }
    }

    /**
     * Gets the most recent combat attack that applied to this mob.
     *
     * @return The last applied combat attack, or {@code null} if none has been tracked.
     */
    public CombatAttack<?> getLastAttackReceived() {
        return lastAttackReceived;
    }

    /**
     * Sets the most recent combat attack that applied to this mob.
     *
     * @param lastAttackReceived The last applied combat attack.
     */
    public void setLastAttackReceived(CombatAttack<?> lastAttackReceived) {
        this.lastAttackReceived = lastAttackReceived;
    }

    /**
     * Gets the most recent combat damage received by this mob.
     *
     * @return The last received combat damage, or {@code null} if none has been tracked.
     */
    public CombatDamage getLastDamageReceived() {
        return lastDamageReceived;
    }

    /**
     * Sets the most recent combat damage received by this mob.
     *
     * @param lastDamageReceived The last received combat damage.
     */
    public void setLastDamageReceived(CombatDamage lastDamageReceived) {
        this.lastDamageReceived = lastDamageReceived;
    }
}