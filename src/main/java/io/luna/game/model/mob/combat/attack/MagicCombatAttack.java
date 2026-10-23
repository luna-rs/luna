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
 * Custom attacks without a spell definition use the attacker's default magic maximum hit. Negative spell maximum
 * hits denote effect-only spells that still check accuracy.
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
        launchProjectile(interactionPolicy.getDistance() == 1 ? 0 : 1, interactionPolicy.getDistance() == 1 ? 1 : 3);
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
