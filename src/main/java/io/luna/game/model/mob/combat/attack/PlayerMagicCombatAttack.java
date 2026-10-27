package io.luna.game.model.mob.combat.attack;

import game.player.Sound;
import game.combat.BarrowsEquipmentSet;
import game.combat.spells.GodSpells;
import game.combat.spells.IbanStaff;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.state.PlayerMagicCombat;

import java.util.function.BiFunction;

/**
 * A {@link MagicCombatAttack} implementation for player-cast magic attacks.
 * <p>
 * This type adds player-specific behavior to the generic magic attack flow, including rune consumption, selected-spell
 * clearing, cast sound playback, and Magic and Hitpoints experience rewards.
 * <p>
 * Ordinary spellbook casts use the same five-tick attack delay for manual casting and autocasting.
 * Base spell experience is awarded when casting; damage experience is awarded after the hit is delivered and capped
 * to the victim's remaining health.
 * Defensive casts award 1.33 Magic and 1 Defence experience per damage instead of the ordinary 2 Magic experience.
 * The casting stance is retained for every hit from the spell, including secondary targets and equipment changes
 * during flight. Base spell experience and Hitpoints experience are unaffected by the stance.
 * Standard stat-draining curses reject already-drained targets and barricades before consuming runes or granting
 * cast experience. Rejected manual selections are cleared so the failed spell cannot leak into a later attack.
 * Bind, Snare, and Entangle also reject frozen targets, barricades, and no-movement duels before those costs.
 * God spells resolve Charge at the initial damage roll and apply their equipment-gated drains at cast time,
 * after requirements have succeeded. Splashes and rejected casts never drain the target.
 * Iban Blast checks its player-owned staff balance before consuming runes and spends charges once on a successful
 * cast, including splashes. The cost is one charge against NPCs or two against players, independent of impact.
 *
 * @author lare96
 */
public class PlayerMagicCombatAttack extends MagicCombatAttack<Player> {

    /**
     * The player's magic combat state.
     */
    protected final PlayerMagicCombat magic;

    /**
     * Whether defensive casting was selected when this spell was successfully cast.
     */
    private boolean defensiveCasting;

    /**
     * The complete Barrows set captured when the spell is successfully cast.
     */
    private BarrowsEquipmentSet barrowsSet;

    /**
     * Creates a new {@link PlayerMagicCombatAttack} from a spell definition.
     *
     * @param attacker The player performing the attack.
     * @param victim The mob receiving the attack.
     * @param spell The spell definition being cast.
     * @param selectedSpell {@code true} if the spell was manually selected, or {@code false} if it is being auto-cast.
     */
    public PlayerMagicCombatAttack(Player attacker, Mob victim, CombatSpellDefinition spell, boolean selectedSpell) {
        super(attacker, victim, spell, 5);
        magic = attacker.getCombat().getMagic();
    }

    /**
     * Creates a new {@link PlayerMagicCombatAttack} from a {@link CombatSpell} wrapper.
     *
     * @param attacker The player performing the attack.
     * @param victim The mob receiving the attack.
     * @param spell The combat spell being cast.
     * @param selectedSpell {@code true} if the spell was manually selected, or {@code false} if it is being auto-cast.
     */
    public PlayerMagicCombatAttack(Player attacker, Mob victim, CombatSpell spell, boolean selectedSpell) {
        this(attacker, victim, spell.getDef(), selectedSpell);
    }

    /**
     * Creates a new {@link PlayerMagicCombatAttack} using explicit visual and effect data.
     *
     * @param attacker The player casting the spell.
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
    public PlayerMagicCombatAttack(Player attacker, Mob victim, Animation cast, Graphic start,
                                   BiFunction<Mob, Mob, LocalProjectile> projectileFunction, Graphic end,
                                   Sound impactSound, CombatSpell spellEffect, int speed, int distance) {
        super(attacker, victim, cast, start, projectileFunction, end, impactSound, spellEffect, speed, distance);
        magic = attacker.getCombat().getMagic();
    }

    @Override
    protected int spellMaxHit() {
        return magic.getMaxHit(spellEffect);
    }

    @Override
    public void attack() {
        if (spellEffect != null) {
            GodSpells.INSTANCE.castEffect(attacker, victim, spellEffect.getSpell(), nextDamage.isAccurate());
        }
        super.attack();
    }

    @Override
    public CombatDamage onAttack(CombatDamage damage) {
        if (!magic.canHoldTarget(spellEffect, victim) || !magic.canCurseTarget(spellEffect, victim)) {
            magic.setSelectedSpell(CombatSpellDefinition.NONE);
            return null;
        }
        boolean ibanBlast = spellEffect != null && spellEffect.getSpell() == CombatSpell.IBAN_BLAST;
        if (ibanBlast && !IbanStaff.INSTANCE.canCast(attacker, victim)) {
            magic.setSelectedSpell(CombatSpellDefinition.NONE);
            return null;
        }
        if (!magic.removeRunes(spellEffect)) {
            // Cancel attack, we don't meet requirements.
            return null;
        }
        if (ibanBlast) {
            IbanStaff.INSTANCE.consume(attacker, victim);
        }
        defensiveCasting = attacker.getCombat().getStance() == CombatStance.DEFENSIVE;

        barrowsSet = BarrowsEquipmentSet.find(attacker);
        if (magic.getSelectedSpell() != CombatSpellDefinition.NONE) {
            // Remove target so we don't automatically keep attacking (how it functions on OSRS).
            magic.setSelectedSpell(CombatSpellDefinition.NONE);
            attacker.getCombat().setTarget(null);
        }
        attacker.playSound(spellEffect.getStartSound());

        attacker.skill(Skill.MAGIC).addExperience(spellEffect.getExp());
        return damage;
    }

    @Override
    public void onDamageApplied(CombatDamage damage) {
        super.onDamageApplied(damage);
        if (barrowsSet != null) {
            barrowsSet.onDamageApplied(damage);
        }
        int rawAmount = Math.max(0, damage.getRawAmount());
        if (defensiveCasting) {
            attacker.skill(Skill.MAGIC).addExperience(rawAmount * 1.33);
            attacker.skill(Skill.DEFENCE).addExperience(rawAmount);
        } else {
            attacker.skill(Skill.MAGIC).addExperience(rawAmount * 2);
        }
        attacker.skill(Skill.HITPOINTS).addExperience(rawAmount * 1.33);
    }

    /**
     * @return The player's magic combat state.
     */
    public PlayerMagicCombat getMagic() {
        return magic;
    }
}
