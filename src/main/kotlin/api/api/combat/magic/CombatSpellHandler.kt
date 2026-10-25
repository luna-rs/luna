package api.combat.magic

import api.combat.CombatHandler.displayCombatOverlay
import engine.controllers.Controllers.inMultiArea
import io.luna.game.model.area.Area
import engine.combat.status.hooks.ImmobilizedStatusEffect
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.state.PlayerMagicCombat
import kotlin.math.floor
import kotlin.time.Duration

/**
 * A registry and helper container for combat spell side effect handlers.
 *
 * Each registered spell maps to a damage listener that is executed in the context of a [CombatDamage] instance.
 * This allows individual spells to attach secondary effects such as weakening, immobilization, poison, healing, or
 * other post-hit behavior.
 *
 * @author lare96
 */
object CombatSpellHandler {

    /**
     * The registered side effect listeners keyed by combat spell.
     *
     * Each listener executes in the context of the resulting [CombatDamage] instance for that spell hit.
     */
    private val spells = HashMap<CombatSpell, CombatDamage.() -> Unit>()

    /**
     * Applies a standard stat-draining curse after an accurate hit has been delivered.
     *
     * Eligibility is checked again at impact because another hit may have drained the stat during flight.
     * Lost City's standard curses subtract one plus the percentage: players use their current level, while NPCs
     * use their base level for the percentage. The amount is subtracted from the current level in both cases,
     * preserving any remaining boost instead of forcing the stat below its base level.
     *
     * @param mob The surviving recipient of the spell.
     * @param skill The affected combat skill.
     * @param percent The drain fraction, either 0.05 or 0.10 for the standard curses.
     */
    fun drainCurse(mob: Mob, skill: Skill, percent: Double) {
        if (!mob.isAlive || skill.level < skill.staticLevel) return
        val oldLevel = skill.level
        val percentageLevel = if (mob is Player) oldLevel else skill.staticLevel
        val amount = 1 + floor(percentageLevel * percent).toInt()
        skill.adjustLevel(-amount)
        if (mob is Player && skill.level < oldLevel) {
            mob.sendMessage("You have been weakened!")
        }
    }

    /**
     * Applies a weakening effect to a mob's skill level.
     *
     * If the target is a [Player] and the skill is successfully reduced, the standard weakened message is sent
     * to that player.
     *
     * @param mob The mob being weakened.
     * @param skill The skill to reduce.
     * @param percent The fraction of the skill to weaken by.
     */
    fun weaken(mob: Mob, skill: Skill, percent: Double) {
        if (!mob.isAlive) return
        val amount = floor(skill.staticLevel * percent).toInt()
        val result = skill.weaken(amount)
        if (mob is Player && result) {
            mob.sendMessage("You have been weakened!")
        }
    }

    /**
     * Applies an immobilization effect to a mob.
     *
     * If the mob is not already immobilized by an active matching action, a new [ImmobilizationAction] is submitted
     * for the supplied duration.
     *
     * @param mob The mob to immobilize.
     * @param duration The immobilization duration.
     */
    fun immobilize(mob: Mob, duration: Duration) {
        if (!mob.isAlive) return
        mob.status.add(ImmobilizedStatusEffect(mob, duration))
    }

    /**
     * Registers a side effect listener for a combat spell.
     *
     * If a listener is already registered for the supplied spell, an [IllegalStateException] will be thrown.
     *
     * @param spell The spell to register.
     * @param damageListener The listener to invoke for damage dealt by that spell.
     */
    fun spell(spell: CombatSpell, damageListener: CombatDamage.() -> Unit) {
        if (spells.putIfAbsent(spell, damageListener) != null) {
            throw IllegalStateException("A listener already exists for spell $spell.")
        }
    }

    /**
     * Executes the registered side effect listener for a combat spell, if one exists.
     *
     * If no listener has been registered for the supplied spell, this method does nothing.
     *
     * @param spell The spell whose side effect handler should be executed.
     * @param damage The applied hit after defensive hooks and health capping.
     */
    fun effect(spell: CombatSpell, damage: CombatDamage) {
        spells[spell]?.invoke(damage)
    }

    /**
     * Selects and rolls up to eight secondary hits without applying spell effects.
     *
     * Preserves the existing primary-accuracy gate. Both primary participants and each secondary must be in multi
     * combat. Each secondary must be alive, attackable, and allowed by the attacker's controllers.
     *
     * @param attack The primary magic attack whose impact is being processed.
     * @return Independently rolled secondary hits, including splashes.
     */
    fun secondaryHits(attack: MagicCombatAttack<*>): List<CombatDamage> {
        val spell = attack.spellEffect ?: return emptyList()
        val attacker = attack.attacker
        val victim = attack.victim
        if (spell.radius < 1 || !attack.nextDamage.isAccurate || !attacker.inMultiArea() || !victim.inMultiArea()) {
            return emptyList()
        }
        val area = Area.of(victim.position, spell.radius)
        val viewable: Set<Mob> = attacker.world.locator.findViewable(victim.type, victim.position)
        return viewable.asSequence().filter {
            it !== attacker && it !== victim && it.isAlive && area.contains(it) && it.inMultiArea() &&
                    it.combat.isAttackable && attacker.combat.checkMultiCombat(it) &&
                    (attacker !is Player || attacker.controllers.checkCombat(it))
        }.take(8).map { attack.calculateDamage(it) }.toList()
    }

    /**
     * Resets the player's current autocast state.
     *
     * This disables autocasting, clears the selected autocast spell by resetting it to [CombatSpellDefinition.NONE],
     * and refreshes the combat overlay so the client reflects the updated state immediately.
     */
    fun PlayerMagicCombat.resetAutocast() {
        isAutocasting = false
        autocastSpell = CombatSpellDefinition.NONE
        player.combat.displayCombatOverlay()
    }
}
