package io.luna.game.model.mob.combat.attack;

import io.luna.game.action.Action;
import io.luna.game.action.ActionState;
import io.luna.game.action.ActionType;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.AmmoDefinition;
import io.luna.game.model.def.CombatStyleDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Animation.AnimationPriority;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.AmmoType;
import io.luna.game.model.mob.combat.CombatStyle;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.damage.CombatDamageRequest;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;

import java.util.function.BiFunction;

/**
 * A {@link CombatAttack} implementation for ranged combat.
 * <p>
 * This attack plays the attacker's firing animation, applies an optional start graphic, launches an optional
 * projectile, and resolves the hit when the projectile reaches the victim.
 * Both timing paths report projectile completion or interruption. Impact graphics are displayed only after the
 * damage pipeline accepts the hit, including misses and zero hits, so discarded or cancelled hits show no impact.
 * <p>
 * The base damage for the primary target is calculated as {@link CombatDamageType#RANGED}.
 *
 * @param <T> The concrete {@link Mob} type performing the ranged attack.
 * @author lare96
 */
public class RangedCombatAttack<T extends Mob> extends CombatAttack<T> {

    /**
     * The firing animation used for this ranged attack.
     */
    private final Animation attackAnimation;

    /**
     * The graphic displayed on the attacker when the attack begins, if any.
     */
    private final Graphic start;

    /**
     * Creates the projectile to display from attacker to victim, if any.
     */
    private final BiFunction<Mob, Mob, LocalProjectile> projectileFunction;

    /**
     * The graphic displayed on the victim when the projectile reaches them, if any.
     */
    private final Graphic end;

    /**
     * The arrival delay captured from the projectile sent during this attack.
     */
    private int projectileHitDelay = 2;

    /**
     * Creates a new {@link RangedCombatAttack}.
     *
     * @param attacker The mob performing the ranged attack.
     * @param victim The mob receiving the ranged attack.
     * @param animationId The firing animation identifier.
     * @param start The graphic displayed on the attacker when the attack begins, or {@code null} if none.
     * @param projectileFunction Creates the projectile to display from attacker to victim, or {@code null} if none.
     * @param end The graphic displayed on the victim when the hit lands, or {@code null} if none.
     * @param speed The attack delay, in ticks, applied after execution.
     * @param range The attack range.
     */
    public RangedCombatAttack(T attacker, Mob victim, int animationId, Graphic start,
                              BiFunction<Mob, Mob, LocalProjectile> projectileFunction, Graphic end, int speed, int range) {
        super(attacker, victim, new InteractionPolicy(InteractionType.LINE_OF_SIGHT, range), speed);

        this.start = start;
        this.projectileFunction = projectileFunction;
        this.end = end;
        attackAnimation = new Animation(animationId, AnimationPriority.HIGH);
    }

    /**
     * Creates a new {@link PlayerRangedCombatAttack} from the supplied combat style and ammo type enums.
     *
     * @param attacker The player performing the ranged attack.
     * @param victim The mob receiving the ranged attack.
     * @param styleType The combat style enum to resolve into a {@link CombatStyleDefinition}.
     * @param ammoType The ammo type enum to resolve into an {@link AmmoDefinition}.
     */
    public RangedCombatAttack(T attacker, Mob victim, CombatStyle styleType, AmmoType ammoType) {
        this(attacker, victim, attacker.getCombat().getAttackAnimation(CombatDamageType.RANGED),
                ammoType.getDef().getStartGraphic(), ammoType.getDef().getProjectile(),
                ammoType.getDef().getEndGraphic(), styleType.getDef().getSpeed(),
                styleType.getDef().getRange());
    }

    @Override
    public void attack() {
        // Apply firing effects.
        attacker.animation(attackAnimation);
        if (start != null) {
            attacker.graphic(start);
        }
        if (usesProjectileTiming()) {
            onProjectileLaunched();
            attacker.submitAction(new Action<>(attacker, ActionType.SOFT, false, projectileHitDelay) {
                @Override
                public boolean run() {
                    onProjectileReached();
                    return true;
                }

                @Override
                public void onFinished() {
                    onProjectileFinished(getState() == ActionState.INTERRUPTED);
                }
            });
        } else {
            launchProjectile(0, 2);
        }
    }

    /**
     * @return Whether to send the projectile with the firing animation and derive hit timing from its arrival cycle.
     */
    protected boolean usesProjectileTiming() {
        return false;
    }

    @Override
    public CombatDamage calculateDamage(Mob other) {
        return new CombatDamageRequest.Builder(attacker, other, CombatDamageType.RANGED).build().resolve();
    }

    @Override
    public void onProjectileLaunched() {

        // Launch the ranged projectile.
        if (projectileFunction != null) {
            LocalProjectile projectile = projectileFunction.apply(attacker, victim);
            // Thirty client cycles make one server tick. Deliver on the following tick, including exact boundaries.
            projectileHitDelay = projectile.getEndCycle() / 30 + 1;
            projectile.display();
        }
    }

    @Override
    public void onProjectileReached() {
        victim.submitAction(new CombatDamageAction(nextDamage, this, true));
    }

    @Override
    public void onDamageApplied(CombatDamage damage) {
        if (end != null) {
            damage.getVictim().graphic(end);
        }
    }
}
