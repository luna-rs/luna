package io.luna.game.model.mob.combat.attack;

import api.bot.action.BotCombatActionHandler;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.interact.InteractionPolicy;

import java.time.Instant;

/**
 * Represents a prepared combat attack from one {@link Mob} onto another. It essentially models a single combat turn,
 * swing, or attack attempt.
 * <p>
 * Subclasses are responsible for calculating a base damage result in {@link #calculateDamage(Mob)} and for
 * implementing the concrete attack behavior in {@link #attack()}. This base class handles the shared combat pipeline,
 * including damage preparation, optional cancellation through {@link #onAttack(CombatDamage)}, combat timer refreshes,
 * attack delay application, and simple projectile timing hooks.
 *
 * @author lare96
 */
public abstract class CombatAttack<T extends Mob> {

    // TODO@0.5.0 Correct weapon sounds. Think this can be done with other combat weapons though through JSON.
    //  Or it might be randomized, some tests need to be done.

    /**
     * The mob performing the attack.
     */
    protected final T attacker;

    /**
     * The mob being attacked.
     * <p>
     * This target is captured from the attacker's combat state when this attack instance is created.
     */
    protected final Mob victim;

    /**
     * The interaction policy that must be satisfied in order for this attack to proceed.
     */
    protected final InteractionPolicy interactionPolicy;

    /**
     * The number of ticks that must pass before the attacker can attack again.
     */
    protected int delay;

    /**
     * The final prepared damage for this attack.
     * <p>
     * This value is assigned during {@link #apply()} after the result from {@link #calculateDamage(Mob)} has been
     * passed through {@link #onAttack(CombatDamage)}.
     */
    protected CombatDamage nextDamage;

    /**
     * If this attack should ignore the attack delay (attack instantly, do not reset attack delay after).
     */
    private boolean ignoreAttackDelay;

    /**
     * A timestamp set when this attack is applied using {@link #apply()}.
     */
    private Instant timestamp;

    /**
     * Creates a new {@link CombatAttack}.
     *
     * @param attacker The mob performing the attack.
     * @param victim The mob receiving the attack.
     * @param interactionPolicy The interaction policy that must be satisfied in order for this attack to proceed.
     * @param delay The attack delay, in ticks, applied after execution.
     */
    public CombatAttack(T attacker, Mob victim, InteractionPolicy interactionPolicy, int delay) {
        this.attacker = attacker;
        this.victim = victim;
        this.interactionPolicy = interactionPolicy;
        this.delay = delay;
    }

    /**
     * Executes the attack-specific behavior.
     * <p>
     * Implementations should perform the concrete combat actions here, such as playing animations, launching
     * projectiles, queuing hitsplats, displaying graphics, or triggering special effects.
     * <p>
     * When invoked through {@link #apply()}, {@link #nextDamage} is expected to already be prepared and non-null and
     * is typically queued for application within a {@link CombatDamageAction}.
     */
    public abstract void attack();

    /**
     * Calculates the base damage for this attack against the specified target.
     * <p>
     * This method is called during {@link #apply()} before {@link #onAttack(CombatDamage)} is invoked.
     *
     * @param other The target being attacked.
     * @return The calculated damage result for the attack.
     */
    public abstract CombatDamage calculateDamage(Mob other);

    /**
     * Applies the shared combat state updates and executes the attack.
     * <p>
     * This method calculates the base damage, passes it through {@link #onAttack(CombatDamage)} to allow subclasses
     * to modify or cancel the result, then applies the standard combat delay and timer updates before invoking {@link #attack()}.
     * Attacks that ignore the attack delay preserve the existing cooldown instead of replacing it.
     * <p>
     * If {@link #onAttack(CombatDamage)} returns {@code null}, the attack is treated as cancelled and {@link #attack()}
     * will not be executed.
     */
    public final void apply() {
        // todo onAttack is redundant, do anything within calculate damage?
        // todo maybe just only pass the builder until attack is ready to run?
        nextDamage = onAttack(calculateDamage(victim));
        if (nextDamage == null) {
            attacker.getCombat().setTarget(null);
            return;
        }
        if (attacker instanceof Player && victim instanceof Player) {
            BotCombatActionHandler.observePvpAttack((Player) attacker, (Player) victim);
        }
        if (!ignoreAttackDelay) {
            attacker.getCombat().setAttackDelay(delay);
        }
        attacker.getCombat().setLastCombatWith(victim);
        attacker.getCombat().resetCombatTimer();
        victim.getCombat().markAttackedBy(attacker);
        timestamp = Instant.now();
        attack();
    }

    /**
     * Intercepts the calculated damage before the attack executes.
     * <p>
     * Subclasses may override this to modify, replace, or cancel the pending damage result.
     *
     * @param damage The calculated damage result.
     * @return The final damage to use, or {@code null} to cancel the attack.
     */
    public CombatDamage onAttack(CombatDamage damage) {
        return damage;
    }

    /**
     * Called when the damage from this attack turn is applied to the {@link #victim}.
     *
     * @param damage The damage that was applied to the victim.
     */
    public void onDamageApplied(CombatDamage damage) {

    }

    /**
     * Called when a projectile-based attack is launched.
     * <p>
     * Subclasses may override this to display a projectile or perform other logic that should occur at launch time.
     */
    public void onProjectileLaunched() {

    }

    /**
     * Called when a projectile-based attack is considered to have reached its target.
     * <p>
     * Subclasses may override this to apply impact graphics, queue damage, or perform other logic that should occur
     * on projectile arrival.
     */
    public void onProjectileReached() {

    }

    /**
     * Called when the projectile timing action finishes, including interruption before arrival.
     * Normal completion follows the arrival callback and does not imply that damage was delivered.
     *
     * @param interrupted Whether the timing action was interrupted rather than completed normally.
     */
    public void onProjectileFinished(boolean interrupted) {

    }

    /**
     * Schedules arrival after an already displayed projectile's flight delay. Both normal completion and interruption
     * notify the attack through the same lifecycle hook used by the legacy launch scheduler.
     *
     * @param arrivalDelay The positive server-tick delay until impact.
     */
    protected final void scheduleProjectileArrival(int arrivalDelay) {
        victim.getCombat().getImpacts().schedule(arrivalDelay, this::onProjectileReached, this::onProjectileFinished);
    }

    /**
     * Retains the legacy launch and arrival offsets while giving both callbacks to the recipient's tick queue.
     * Execution counter zero corresponds to the tick after the attack, so offsets receive one additional tick.
     * Recipient removal discards the flight and invokes the finish hook once, even if both callbacks are pending.
     *
     * @param launchTicks The legacy execution counter on which the projectile is displayed.
     * @param arrivalTicks The later legacy execution counter on which it arrives.
     */
    protected final void launchProjectile(int launchTicks, int arrivalTicks) {
        class Flight {
            private boolean finished;

            void finish(boolean interrupted) {
                if (!finished) {
                    finished = true;
                    onProjectileFinished(interrupted);
                }
            }
        }
        Flight flight = new Flight();
        var impacts = victim.getCombat().getImpacts();
        impacts.schedule(launchTicks + 1, () -> {
            if (!flight.finished) {
                onProjectileLaunched();
            }
        }, interrupted -> {
            if (interrupted) {
                flight.finish(true);
            }
        });
        impacts.schedule(arrivalTicks + 1, () -> {
            if (!flight.finished) {
                onProjectileReached();
            }
        }, flight::finish);
    }

    /**
     * @return The attacking mob.
     */
    public T getAttacker() {
        return attacker;
    }

    /**
     * @return The victim.
     */
    public Mob getVictim() {
        return victim;
    }

    /**
     * @return The interaction policy that must be satisfied.
     */
    public InteractionPolicy getInteractionPolicy() {
        return interactionPolicy;
    }

    /**
     * @return The attack delay, in ticks.
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Sets the attack delay, in ticks.
     *
     * @param delay The new delay.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    /**
     * @return The prepared damage, or {@code null} if not yet assigned or if the attack was cancelled.
     */
    public CombatDamage getNextDamage() {
        return nextDamage;
    }

    /**
     * @return {@code true} if this attack should ignore the attack delay.
     */
    public boolean isIgnoreAttackDelay() {
        return ignoreAttackDelay;
    }

    /**
     * Sets if this attack should ignore the attack delay.
     *
     * @param ignoreAttackDelay The new value.
     */
    public void setIgnoreAttackDelay(boolean ignoreAttackDelay) {
        this.ignoreAttackDelay = ignoreAttackDelay;
    }

    /**
     * @return The timestamp set when this attack is applied, or {@code null} if this attack hasn't been applied yet.
     */
    public Instant getTimestamp() {
        return timestamp;
    }
}
