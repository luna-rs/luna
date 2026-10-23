package io.luna.game.model.mob.combat.attack;

import game.player.Sound;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.block.Animation;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.CombatSpell;
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
 *
 * @author lare96
 */
public class PlayerMagicCombatAttack extends MagicCombatAttack<Player> {

    /**
     * The player's magic combat state.
     */
    protected final PlayerMagicCombat magic;

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
    public CombatDamage onAttack(CombatDamage damage) {
        if (!magic.removeRunes(spellEffect)) {
            // Cancel attack, we don't meet requirements.
            return null;
        }

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
        int rawAmount = Math.max(0, damage.getRawAmount());
        attacker.skill(Skill.MAGIC).addExperience(rawAmount * 2);
        attacker.skill(Skill.HITPOINTS).addExperience(rawAmount * 1.33);
    }

    /**
     * @return The player's magic combat state.
     */
    public PlayerMagicCombat getMagic() {
        return magic;
    }
}
