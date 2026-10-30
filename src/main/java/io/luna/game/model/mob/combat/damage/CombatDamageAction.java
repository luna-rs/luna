package io.luna.game.model.mob.combat.damage;

import io.luna.game.action.Action;
import io.luna.game.action.ActionPhase;
import io.luna.game.action.ActionType;
import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.combat.CombatAction;
import io.luna.game.model.mob.combat.CombatRestriction;
import io.luna.game.model.mob.combat.attack.CombatAttack;

/**
 * An {@link Action} that applies a resolved {@link CombatDamage} hit to its victim and then performs auto-retaliate
 * handling for that victim.
 * <p>
 * The owning mob for this action is the damage victim. When executed, the action:
 * <ul>
 *     <li>Applies the resolved hit if the victim is still alive.</li>
 *     <li>Retaliates against a living attacker when enabled and no active engagement with a living target remains.</li>
 *     <li>Checks NPC escape thresholds after each incoming hit, including during an existing engagement.</li>
 * </ul>
 * This action completes after a single execution. Defensive hooks may replace or cancel its incoming hit before
 * application. Its stored damage retains that incoming result; source callbacks, events, and combat history receive
 * the separate health-capped applied hit. A source attack can be shared by several recipients, so each delivery uses
 * its own damage participants rather than the source's primary victim.
 * <p>
 * Delayed instances execute in {@link ActionPhase#EFFECT}, before outgoing combat. Instant instances execute
 * during submission; recipient-owned impact callbacks use them to apply a hit at its scheduled arrival.
 *
 * @author lare96
 */
public final class CombatDamageAction extends Action<Mob> {

    /**
     * The mob that originally dealt the hit and may become the victim's retaliation target.
     */
    private final Mob attacker;

    /**
     * The mob receiving the hit.
     * <p>
     * This is also the owning mob for this action.
     */
    private final Mob victim;

    /**
     * The incoming hit, initially the launch result and subsequently the defensive override, or {@code null} when
     * cancelled. Applying its health cap does not replace this value.
     */
    private CombatDamage damage;

    /**
     * The source of the resolved damage.
     */
    private final CombatAttack<?> source;

    /**
     * Creates a new {@link CombatDamageAction}.
     *
     * @param damage The resolved combat damage to apply.
     * @param source The source of the resolved damage.
     * @param instant {@code true} if the action should execute instantly, otherwise {@code false}.
     */
    public CombatDamageAction(CombatDamage damage, CombatAttack<?> source, boolean instant) {
        this(damage, source, instant, 1);
    }

    /**
     * Creates a new {@link CombatDamageAction} with an explicit action delay.
     *
     * @param damage The resolved hit and its participants.
     * @param source The source attack passed to defensive and damage callbacks.
     * @param instant Whether to apply the hit during submission.
     * @param delay The positive action delay in game ticks, used when execution is deferred.
     * @throws IllegalArgumentException If {@code delay} is not positive.
     */
    public CombatDamageAction(CombatDamage damage, CombatAttack<?> source, boolean instant, int delay) {
        super(damage.getVictim(), ActionType.SOFT, instant, delay);
        this.damage = damage;
        this.source = source;
        attacker = damage.getAttacker();
        victim = damage.getVictim();
    }

    /**
     * @return {@link ActionPhase#EFFECT}, before this victim's outgoing combat.
     */
    @Override
    public ActionPhase getPhase() {
        return ActionPhase.EFFECT;
    }

    @Override
    public void onSubmit() {
        if (isInstant() && run()) {
            complete();
        }
    }

    @Override
    public boolean run() {
        if (victim.isAlive() && CombatRestriction.permits(attacker, victim)) {
            // Last defensive modifications/hooks to the damage before applying it.
            victim.getCombat().onNextDefence(attacker, this);

            // Apply the hit.
            if (damage != null) {
                damage.apply(source);
            }

            // Determine whether the victim should retaliate.
            if (victim.isAlive() && attacker.isAlive() && attacker.getState() == EntityState.ACTIVE &&
                    victim.getCombat().isAutoRetaliate() &&
                    (source == null || !source.suppressesCombatEngagement(damage))) {
                if (victim instanceof Npc || victim.getCombat().getTarget() == null ||
                        !victim.getCombat().getTarget().isAlive() ||
                        !victim.getActions().contains(CombatAction.class)) {
                    victim.getCombat().retaliate(attacker);
                }
            }
        }
        return true;
    }

    /**
     * Gets the incoming damage after any defensive override. This value is not the health-capped applied result.
     *
     * @return The incoming hit, or {@code null} if defensive hooks cancelled it.
     */
    public CombatDamage getDamage() {
        return damage;
    }

    /**
     * Replaces or cancels the hit before application. Replacements must retain this action's attacker and victim.
     *
     * @param damage The replacement incoming hit, or {@code null} to cancel delivery.
     */
    public void setDamage(CombatDamage damage) {
        this.damage = damage;
    }

    /**
     * Gets the source attack used for launch metadata and applied-hit callbacks. Secondary hits may have a different
     * recipient from the source's primary victim.
     *
     * @return The source attack for this delivery.
     */
    public CombatAttack<?> getSource() {
        return source;
    }
}
