package io.luna.game.model.mob.combat.attack;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.interact.InteractionPolicy;

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
    protected final int delay;

    /**
     * The final prepared damage for this attack.
     * <p>
     * This value is assigned during {@link #apply()} after the result from {@link #calculateDamage(Mob)} has been
     * passed through {@link #onAttack(CombatDamage)}.
     */
    protected CombatDamage nextDamage;

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
     * <p>
     * If {@link #onAttack(CombatDamage)} returns {@code null}, the attack is treated as cancelled and {@link #attack()}
     * will not be executed.
     */
    public final void apply() {
        nextDamage = onAttack(calculateDamage(victim));
        if (nextDamage == null) {
            attacker.getCombat().setTarget(null);
            return;
        }
        attacker.getCombat().getDelay().reset(delay);
        attacker.getCombat().setLastCombatWith(victim);
        attacker.getCombat().resetCombatTimer();
        victim.getCombat().setLastCombatWith(attacker);
        victim.getCombat().resetCombatTimer(); // Once targeted and engaged, cannot safely log out or attack anyone else.
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
     * Starts a simple projectile timing action for this attack.
     * <p>
     * This helper invokes {@link #onProjectileLaunched()} on the first action execution and {@link #onProjectileReached()}
     * on the third execution, then completes.
     */
    protected final void launchProjectile() {
        attacker.submitAction(new Action<>(attacker, ActionType.SOFT, false, 1) {
            @Override
            public boolean run() {
                if (getExecutions() == 0) {
                    onProjectileLaunched();
                } else if (getExecutions() == 2) {
                    onProjectileReached();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * @return The attacking mob.
     */
    public Mob getAttacker() {
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
     * @return The prepared damage, or {@code null} if not yet assigned or if the attack was cancelled.
     */
    public CombatDamage getNextDamage() {
        return nextDamage;
    }
}