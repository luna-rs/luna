package io.luna.game.model.mob.combat.state;

import engine.combat.status.StatusEffectType;
import engine.controllers.MultiCombatAreaListener;
import game.combat.npcHooks.skeletalWyvern.WyvernIcyBreathStatusEffect;
import io.luna.game.TickTimer;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.CombatAction;
import io.luna.game.model.mob.combat.CombatImpactQueue;
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
     * The nominal combat duration in seconds, used by player-facing logout messages.
     * <p>
     * Gameplay checks use the tick durations below rather than comparing elapsed wall-clock seconds.
     */
    public static final int COMBAT_TIMER_DURATION = 10;

    /**
     * The number of ticks for which a qualifying attack keeps general recent-combat status active.
     * Seventeen 600ms ticks round the nominal ten-second duration up to whole game ticks.
     */
    public static final int COMBAT_TIMER_TICKS = 17;

    /**
     * The number of ticks for which a qualifying attack prevents an ordinary logout.
     * Kept separate from recent-combat status so either policy can change independently.
     */
    public static final int LOGOUT_DELAY_TICKS = 17;

    /**
     * The number of ticks for which the last opponent retains ownership under single-combat rules.
     */
    public static final int SINGLE_COMBAT_TICKS = 8;

    /**
     * The mob that owns this combat context.
     */
    protected final T mob;

    /**
     * Tracks pending and active combat damage for the owning mob.
     */
    private final CombatDamageStack damageStack;

    /**
     * Incoming hits and projectile callbacks owned by this mob independently of the attacker's action queue.
     */
    private final CombatImpactQueue impacts;

    /**
     * Tracks game ticks since the last qualifying combat interaction, or remains stopped before any interaction.
     */
    private final TickTimer combatTimer;

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
     * The world tick at which the last assigned attack cooldown becomes ready, or {@code -1} before assignment.
     * Retained after the countdown reaches zero to distinguish newly ready attacks from expired flinch windows.
     */
    private long attackReadyTick = -1;

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
     * The last attack sent by this mob.
     */
    private CombatAttack<?> lastAttackSent;

    /**
     * The most recent combat damage received by this mob.
     * <p>
     * This is published after damage is applied and before its events or source callbacks run. Nested hit
     * notifications may replace it with a newer applied hit; event and callback arguments identify their own hit.
     */
    private CombatDamage lastDamageReceived;

    /**
     * Creates a new combat context for the supplied mob.
     *
     * @param mob The mob that owns this combat context.
     */
    public CombatContext(T mob) {
        this.mob = mob;
        combatTimer = new TickTimer(mob.getWorld());
        damageStack = new CombatDamageStack(mob);
        impacts = new CombatImpactQueue(mob);
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
     * @param damage The damage received.
     * @return The defence animation id.
     */
    public abstract int getDefenceAnimation(CombatDamageType type, int damage);

    /**
     * Starts attacking the supplied enemy.
     * <p>
     * Null targets, self-attacks, and engagements involving a dead mob are ignored without changing combat state.
     * <p>
     * If the owning mob is a {@link Player}, combat initiation is first validated by the player's active controllers.
     * If combat is allowed, the target is set and a {@link CombatAction} is submitted if one is not already active.
     *
     * @param enemy The mob to attack.
     */
    public final void attack(Mob enemy) {
        if (enemy == null || enemy == mob || !mob.isAlive() || !enemy.isAlive()) {
            return;
        }
        if (mob instanceof Npc npc && (npc.getCombat().isRetreating() ||
                !npc.getCombat().isWithinLeash(enemy))) {
            return;
        }
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
     * Starts retaliation against a living attacker when this mob is alive and combat is enabled.
     * <p>
     * An accepted combat action receives {@link #getRetaliationDelay()} only when the previous attack deadline
     * has already passed. A cooldown still pending, or becoming ready in this exact tick, is preserved. NPCs
     * override this behavior to apply their own flinch, retreat, and leash rules.
     *
     * @param enemy The attacker to retaliate against.
     */
    public void retaliate(Mob enemy) {
        if (enemy == null || !enemy.isAlive() || !mob.isAlive() || isDisabled()) {
            return;
        }
        attack(enemy);
        CombatAction action = mob.getActions().first(CombatAction.class);
        if (target == enemy && action != null && hasExpiredAttackDelay()) {
            setAttackDelay(getRetaliationDelay());
        }
    }

    /**
     * Gets the delay before an idle mob may perform its first retaliatory attack.
     * <p>
     * The default is half the four-tick unarmed interval. Player contexts derive the interval from their weapon.
     *
     * @return The retaliation delay in game ticks.
     */
    protected int getRetaliationDelay() {
        return 2;
    }

    /**
     * Determines whether the attack countdown is ready and its recorded deadline is strictly in the past.
     *
     * @return {@code true} if the cooldown has expired beyond its ready tick, otherwise {@code false}.
     */
    protected final boolean hasExpiredAttackDelay() {
        return isAttackReady() && mob.getWorld().getCurrentTick() > attackReadyTick;
    }

    /**
     * Determines whether a ready attack has remained idle for more than eight ticks beyond its deadline.
     * <p>
     * NPC retaliation uses this boundary to permit a new flinch during an otherwise active engagement.
     *
     * @return {@code true} if the idle flinch boundary has passed, otherwise {@code false}.
     */
    protected final boolean hasLongExpiredAttackDelay() {
        return isAttackReady() && mob.getWorld().getCurrentTick() > attackReadyTick + 8;
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

        if (isSingleCombatLocked() && lastCombatWith != null && !Objects.equals(lastCombatWith, victim)) {
            mob.ifPlayer(player -> player.sendMessage("You are already in combat."));
            return false;
        }

        CombatContext<?> victimCombat = victim.getCombat();
        if (victimCombat.isSingleCombatLocked() && victimCombat.lastCombatWith != null &&
                !Objects.equals(victimCombat.lastCombatWith, mob)) {
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
     * Gets the {@link TickTimer} shared by recent-combat, ownership, and logout checks.
     * <p>
     * The timer is stopped until the first qualifying interaction. Callers must check
     * {@link TickTimer#isRunning()} before reading its elapsed duration.
     *
     * @return The combat tick timer.
     */
    public TickTimer getTimer() {
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
     * Records an incoming attack for logout prevention and single-combat ownership, including secondary targets.
     * Call when a valid attack is committed, before damage delivery: misses and zero hits still count as engagement.
     * This does not select a retaliation target or change either participant's attack delay.
     *
     * @param attacker The mob responsible for the incoming attack.
     */
    public final void markAttackedBy(Mob attacker) {
        lastCombatWith = attacker;
        resetCombatTimer();
    }

    /**
     * Clears this context for a new mob life.
     * <p>
     * Incoming impacts are discarded, targets and combat history are cleared, attack readiness is reset, combat
     * is enabled, and both the combat timer and damage contribution stack are cleared.
     */
    public void reset() {
        impacts.clear();
        target = null;
        autoRetaliateTarget = null;
        lastCombatWith = null;
        attackDelay = 0;
        attackReadyTick = -1;
        disabled = false;
        lastAttackReceived = null;
        lastAttackSent = null;
        lastDamageReceived = null;
        combatTimer.reset();
        damageStack.clear();
    }

    /**
     * Determines whether the owning mob is currently considered in combat.
     * <p>
     * A mob remains in recent combat for {@link #COMBAT_TIMER_TICKS} ticks. Ownership and logout use their own
     * predicates so changing one policy does not silently change the others.
     *
     * @return {@code true} if the mob is still considered in combat, otherwise {@code false}.
     */
    public final boolean inCombat() {
        return withinCombatWindow(COMBAT_TIMER_TICKS);
    }

    /**
     * Determines whether the single-combat ownership window is still active.
     * <p>
     * This checks elapsed ticks only. {@link #checkMultiCombat(Mob)} also checks the last opponent and area rules.
     *
     * @return {@code true} if fewer than {@link #SINGLE_COMBAT_TICKS} ticks have elapsed, otherwise {@code false}.
     */
    public final boolean isSingleCombatLocked() {
        return withinCombatWindow(SINGLE_COMBAT_TICKS);
    }

    /**
     * Determines whether a qualifying combat interaction still prevents an ordinary logout.
     *
     * @return {@code true} if fewer than {@link #LOGOUT_DELAY_TICKS} ticks have elapsed, otherwise {@code false}.
     */
    public final boolean isLogoutBlocked() {
        return withinCombatWindow(LOGOUT_DELAY_TICKS);
    }

    /**
     * Checks an exclusive tick boundary against the current combat timer.
     *
     * @param ticks The number of ticks for which the window remains active.
     * @return {@code true} if the timer is running and its elapsed ticks are below the boundary.
     */
    private boolean withinCombatWindow(int ticks) {
        return combatTimer.isRunning() && combatTimer.getDurationTicks() < ticks;
    }

    /**
     * @return The combat damage stack.
     */
    public final CombatDamageStack getDamageStack() {
        return damageStack;
    }

    /**
     * @return The queue of incoming combat events for this mob's current life.
     */
    public final CombatImpactQueue getImpacts() {
        return impacts;
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
     * Advances the attack countdown by one game tick without allowing it to become negative.
     * <p>
     * The world calls this once before client input, independently of combat action execution and movement retries.
     */
    public void processAttackDelay() {
        if (attackDelay > 0) {
            attackDelay--;
        }
    }

    /**
     * Sets the number of ticks that must elapse before this mob may attack again.
     * <p>
     * Negative values are treated as zero. The corresponding world-tick deadline is retained for retaliation
     * checks after the countdown reaches zero.
     *
     * @param attackDelay The new attack delay in game ticks.
     */
    public void setAttackDelay(int attackDelay) {
        this.attackDelay = Math.max(0, attackDelay);
        attackReadyTick = mob.getWorld().getCurrentTick() + this.attackDelay;
    }

    /**
     * Extends the current attack delay after food is eaten.
     * <p>
     * Adds delay even when the mob is ready to attack. Both the current cooldown and supplied delay are clamped to
     * zero before addition, so negative values cannot shorten the cooldown.
     *
     * @param delay The additional delay in game ticks.
     */
    public void addFoodDelay(int delay) {
        setAttackDelay(Math.max(0, attackDelay) + Math.max(0, delay));
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
     * Gets the most recent attack sent by this mob.
     *
     * @return The last attack sent by this mob, or {@code null} if none has been tracked.
     */
    public CombatAttack<?> getLastAttackSent() {
        return lastAttackSent;
    }

    /**
     * Sets the last attack sent by this mob.
     *
     * @param lastAttackSent The last attack.
     */
    public void setLastAttackSent(CombatAttack<?> lastAttackSent) {
        this.lastAttackSent = lastAttackSent;
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
