package api.combat.magic

import api.combat.CombatHandler.displayCombatOverlay
import api.predef.ext.*
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
    private val spells = HashMap<CombatSpell, MagicCombatAttack<*>.() -> Unit>()

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
    fun spell(spell: CombatSpell, damageListener: MagicCombatAttack<*>.() -> Unit) {
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
     * @param attack The combat damage context passed into the spell effect listener.
     */
    fun effect(spell: CombatSpell, attack: MagicCombatAttack<*>) {
        spells[spell]?.invoke(attack)
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