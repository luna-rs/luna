package game.combat.specialAttacks

import api.combat.player.VoidCombatAttack
import api.combat.specialAttack.SpecialAttackHandler.attack
import game.player.Sound
import api.predef.ext.*
import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.Direction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_SPEAR
import io.luna.game.model.mob.combat.damage.CombatDamageRequest

val SHOVE_GRAPHIC = Graphic(253, 100, 0)
val STUNNED_GRAPHIC = Graphic(254, 100, 0)
val ANIMATION = 1064

/**
 * Castle Wars barricades for both teams, including burning variants. These cannot receive Shove.
 */
val BARRICADE_IDS = setOf(1532, 1533, 1534, 1535)

/**
 * Shove's five-tick stun duration for players and NPCs. The Lost City scripts express this as a delay of four,
 * documented as five game ticks; Luna's status countdown directly counts the five processing ticks.
 */
val STUN_DURATION = 5.ticks

/**
 * Applies the shove's five-tick duration through the shared stun status.
 * Repeated shoves refresh one timer instead of leaving older timers that can unlock combat early.
 * Expiration removes only this status and cannot clear independent combat restrictions such as Slayer execution.
 * The queued server-side shove remains available while stun checks block new player actions.
 *
 * @param victim The shoved mob.
 */
fun applyShoveStun(victim: Mob) {
    victim.interact(null)
    victim.status.add(BasicStatusEffect(victim, StatusEffectType.STUNNED, STUN_DURATION, refreshable = true))
}

/**
 * Shove applies its stun to eligible targets regardless of size. Only one-tile targets can be displaced;
 * larger NPCs still consume the normal special energy and receive the stun without changing position.
 * Barricades are rejected before energy consumption, visuals, or status effects, and special selection is cleared.
 */
/**
 * Configures the dragon spear special attack, including its verified Lost City launch sound.
 *
 * @author lare96
 */
attack(type = DRAGON_SPEAR,
       drain = 25) {

    launchSound = Sound.SHOVE

    attack {
        if (victim is Npc && victim.id in BARRICADE_IDS) {
            attacker.sendMessage("You can't use this special attack on barricades.")
            attacker.combat.specialBar.forceOff()
            VoidCombatAttack(attacker, victim)
        } else {
            melee(ANIMATION)
        }
    }

    launched {
        attacker.graphic(SHOVE_GRAPHIC)
        victim.graphic(STUNNED_GRAPHIC)

        // Determine push direction.
        var pushDir = Direction.between(attacker.position, victim.position)
        if (pushDir == Direction.NONE) {
            pushDir = attacker.lastDirection
        }
        // Cancel any pending queued movement for the victim.
        victim.navigator.cancel()
        victim.walking.clear()
        attacker.combat.target = null

        // Stun and move the victim.
        if (victim.size() == 1) {
            victim.navigator.step(pushDir)
        }
        applyShoveStun(victim)
        CombatDamageRequest.effect(attacker, victim).resolve()
    }
}
