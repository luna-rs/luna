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
import io.luna.game.model.mob.combat.damage.CombatDamageRequest;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.combat.state.PlayerMagicCombat;

import java.util.function.BiFunction;

/**
 * A {@link MagicCombatAttack} implementation for player-cast magic attacks.
 * <p>
 * This type adds player-specific behavior to the generic magic attack flow, including rune consumption, selected-spell
 * clearing, cast sound playback, and Magic and Hitpoints experience rewards.
 * <p>
 * The attack delay is determined by whether the spell was manually selected or auto-cast.
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
        super(attacker, victim, spell, !selectedSpell ? 5 : 4);
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
     */
    public PlayerMagicCombatAttack(Player attacker, Mob victim, Animation cast, Graphic start,
                                   BiFunction<Mob, Mob, LocalProjectile> projectileFunction, Graphic end,
                                   Sound impactSound, CombatSpell spellEffect, int speed) {
        super(attacker, victim, cast, start, projectileFunction, end, impactSound, spellEffect, speed);
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

        int rawAmount = damage.getRawAmount();
        attacker.skill(Skill.MAGIC).addExperience(spellEffect.getExp() + (damage.getRawAmount() * 2));
        attacker.skill(Skill.HITPOINTS).addExperience(rawAmount * 1.33);
        return damage;
    }

    @Override
    public CombatDamage onEffect(CombatDamage damage) {
        int rawAmount = damage.getRawAmount();
        attacker.skill(Skill.MAGIC).addExperience(rawAmount * 2);
        attacker.skill(Skill.HITPOINTS).addExperience(rawAmount * 1.33);
        return damage;
    }

    @Override
    public CombatDamage calculateDamage(Mob other) {
        return new CombatDamageRequest.Builder(attacker, victim, CombatDamageType.MAGIC).build().resolve();
    }

    /**
     * @return The player's magic combat state.
     */
    public PlayerMagicCombat getMagic() {
        return magic;
    }
}