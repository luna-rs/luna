package io.luna.game.model.mob.combat.attack;

import api.combat.magic.CombatSpellHandler;
import game.player.Sound;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.LocalSound;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageAction;
import io.luna.game.model.mob.combat.damage.CombatDamageRequest;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.interact.InteractionPolicy;
import io.luna.game.model.mob.interact.InteractionType;

import java.util.List;
import java.util.function.BiFunction;

/**
 * A {@link CombatAttack} implementation for magic attacks driven by a {@link CombatSpellDefinition}.
 * <p>
 * Area targets are selected and rolled at impact without applying effects. Each hit passes through its recipient's
 * defensive hooks and health cap before its applied-damage callback displays the impact and runs the spell effect.
 * Cancelled hits have no effect callback; secondary hits cannot recursively select further targets.
 * Each living secondary recipient enters combat before delivery, even when it splashes or auto-retaliate is off.
 * Custom attacks without a spell definition use the attacker's default magic maximum hit. Negative spell maximum
 * hits denote effect-only spells that still check accuracy.
 * Spellbook projectiles are sent with the cast animation and deliver damage on the server tick after their arrival
 * cycle. Spells without a projectile resolve on the following tick. Custom attacks can opt into projectile timing
 * through {@link #usesProjectileTiming()}, using the arrival cycle's server tick without the spellbook offset.
 * Other custom attacks retain their existing timing.
 *
 * @param <T> The concrete {@link Mob} type performing the magic attack.
 * @author lare96
 */
public class MagicCombatAttack<T extends Mob> extends CombatAttack<T> {

    /**
     * The cast animation played by the attacker.
     */
    private final Animation cast;

    /**
     * The starting graphic displayed on the attacker, if any.
     */
    private final Graphic start;

    /**
     * Produces the projectile to launch for this spell, if any.
     */
    private final BiFunction<Mob, Mob, LocalProjectile> projectileFunction;

    /**
     * The ending graphic displayed on a successfully hit victim, if any.
     */
    private final Graphic end;

    /**
     * The sound played when the spell impacts successfully, if any.
     */
    private final Sound impactSound;

    /**
     * The spell definition used to resolve spell-specific secondary effects.
     */
    protected final CombatSpellDefinition spellEffect;

    /**
     * Impact delay captured from the displayed projectile, or one tick for a spell without a projectile.
     */
    private int projectileHitDelay = 1;

    /**
     * Creates a new {@link MagicCombatAttack} using explicit visual and effect data.
     *
     * @param attacker The mob casting the spell.
     * @param victim The target being attacked.
     * @param cast The cast animation to play.
     * @param start The starting graphic to display on the attacker, or {@code null} if none.
     * @param projectileFunction Produces the projectile to launch, or {@code null} if the spell has no projectile.
     * @param end The ending graphic to display on a successful impact, or {@code null} if none.
     * @param impactSound The sound to play on a successful impact, or {@code null} if none.
     * @param spellEffect The spell definition used to resolve spell-specific effects.
     * @param speed The attack delay, in ticks, applied after execution.
     * @param distance The distance required for interaction.
     */
    public MagicCombatAttack(T attacker, Mob victim, Animation cast, Graphic start,
                             BiFunction<Mob, Mob, LocalProjectile> projectileFunction, Graphic end, Sound impactSound,
                             CombatSpell spellEffect, int speed, int distance) {
        super(attacker, victim, new InteractionPolicy(InteractionType.LINE_OF_SIGHT, distance), speed);
        this.cast = cast;
        this.start = start;
        this.projectileFunction = projectileFunction;
        this.end = end;
        this.impactSound = impactSound;
        this.spellEffect = spellEffect == null ? null : spellEffect.getDef();
    }

    /**
     * Creates a new {@link MagicCombatAttack} from a {@link CombatSpellDefinition}.
     *
     * @param attacker The mob casting the spell.
     * @param victim The target being attacked.
     * @param spell The spell definition to execute.
     * @param speed The attack delay, in ticks, applied after execution.
     */
    public MagicCombatAttack(T attacker, Mob victim, CombatSpellDefinition spell, int speed) {
        this(attacker, victim, spell.getCastAnimation(), spell.getStartGraphic(), spell.getProjectile(),
                spell.getEndGraphic(), spell.getEndSound(), spell.getSpell(), speed, 10);
    }

    /**
     * Creates a new {@link MagicCombatAttack} from a {@link CombatSpell} wrapper.
     *
     * @param attacker The mob casting the spell.
     * @param victim The target being attacked.
     * @param spell The combat spell to execute.
     * @param speed The attack delay, in ticks, applied after execution.
     */
    public MagicCombatAttack(T attacker, Mob victim, CombatSpell spell, int speed) {
        this(attacker, victim, spell.getDef(), speed);
    }

    @Override
    public void attack() {
        attacker.animation(cast);
        if (start != null) {
            attacker.graphic(start);
        }
        scheduleHit();
    }

    /**
     * Schedules damage after the cast visuals have been displayed. Spellbook and opted-in custom projectiles use
     * their flight timing; other custom attacks retain the legacy launch scheduler. Custom attacks with immediate
     * damage can override this hook and deliver through {@link #onProjectileReached()} without a dummy projectile.
     * Such overrides must also notify {@link #onProjectileFinished(boolean)} when delivery completes.
     */
    protected void scheduleHit() {
        if (usesProjectileTiming()) {
            onProjectileLaunched();
            scheduleProjectileArrival(projectileHitDelay);
        } else {
            launchProjectile(interactionPolicy.getDistance() == 1 ? 0 : 1, interactionPolicy.getDistance() == 1 ? 1 : 3);
        }
    }

    /**
     * Selects immediate projectile display and an arrival delay derived from its client cycle.
     * Spellbook attacks use this by default. Custom attacks may opt in after their projectile profile has been
     * configured relative to the cast, rather than the legacy delayed launch callback.
     *
     * @return Whether this attack uses its displayed projectile to schedule damage.
     */
    protected boolean usesProjectileTiming() {
        return spellEffect != null;
    }

    @Override
    public CombatDamage calculateDamage(Mob other) {
        // Use spell effect max hit.
        CombatDamageRequest.Builder request = new CombatDamageRequest.Builder(attacker, other, CombatDamageType.MAGIC);
        if (spellEffect != null) {
            request.setBaseMaxHit(spellEffect.getMaxHit());
            if (spellEffect.getMaxHit() < 0) {
                request.disableDamage();
            }
        }
        return request.build().resolve();
    }

    @Override
    public final void onProjectileLaunched() {
        if (projectileFunction != null) {
            LocalProjectile projectile = projectileFunction.apply(attacker, victim);
            // Spellbook queues add one tick; custom NPC projectiles use duration / 30.
            // The action scheduler requires a positive delay for very short flights.
            projectileHitDelay = Math.max(1, projectile.getEndCycle() / 30 + (spellEffect == null ? 0 : 1));
            projectile.display();
        }
    }

    @Override
    public final void onProjectileReached() {
        if (!victim.isAlive()) {
            return;
        }
        List<CombatDamage> secondaryHits = CombatSpellHandler.INSTANCE.secondaryHits(this);
        if (spellEffect == null && nextDamage.getAmount().isEmpty()) {
            nextDamage = CombatDamageRequest.zero(attacker, victim, CombatDamageType.MAGIC).resolve();
        }
        victim.submitAction(new CombatDamageAction(nextDamage, this, true));
        for (CombatDamage hit : secondaryHits) {
            if (!hit.getVictim().isAlive()) {
                continue;
            }
            hit.getVictim().getCombat().markAttackedBy(attacker);
            hit.getVictim().submitAction(new CombatDamageAction(hit, this, true));
        }
    }

    @Override
    public void onDamageApplied(CombatDamage damage) {
        Mob recipient = damage.getVictim();
        if (damage.isAccurate() || spellEffect == null) {
            if (end != null) {
                recipient.graphic(end);
            }
            if (impactSound != null) {
                LocalSound.of(recipient, impactSound).display();
            }
            if (spellEffect != null && damage.isAccurate()) {
                CombatSpellHandler.INSTANCE.effect(spellEffect.getSpell(), damage);
            }
        } else {
            recipient.graphic(new Graphic(85, 100));
            LocalSound.of(recipient, Sound.SPELLFAIL).display();
        }
    }

    /**
     * @return The current spell effect definition, or {@code null} if none is set.
     */
    public CombatSpellDefinition getSpellEffect() {
        return spellEffect;
    }
}
