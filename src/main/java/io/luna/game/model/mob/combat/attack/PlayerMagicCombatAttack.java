package io.luna.game.model.mob.combat.attack;

import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.game.model.mob.combat.CombatSpell;
import io.luna.game.model.mob.combat.damage.CombatDamage;
import io.luna.game.model.mob.combat.damage.CombatDamageType;
import io.luna.game.model.mob.combat.state.PlayerMagicCombat;

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
        return CombatDamage.computed(attacker, victim, CombatDamageType.MAGIC);
    }

    /**
     * @return The player's magic combat state.
     */
    public PlayerMagicCombat getMagic() {
        return magic;
    }
}