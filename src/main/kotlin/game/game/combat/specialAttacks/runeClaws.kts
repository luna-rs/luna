package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import game.player.Sound
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.RUNE_CLAWS

/**
 * The rune claws special attack animation id.
 */
val ANIMATION = 923

/**
 * The graphic displayed when the rune claws special attack is performed.
 */
val GRAPHIC = Graphic(274, 100, 0)

/**
 * Impale increases maximum damage by 10 percent while retaining the ordinary accuracy roll.
 */
val MAX_HIT_BONUS = 0.10

/**
 * Impale adds one recovery tick to the weapon's normal attack delay before the attacker can strike again.
 */
val EXTRA_ATTACK_DELAY = 1

/**
 * Configures rune claws special attack, including its verified Lost City launch sound.
 *
 * @author lare96
 */
attack(type = RUNE_CLAWS,
       drain = 25,
       strengthBonus = MAX_HIT_BONUS) {

    launchSound = Sound.IMPALE

    attack { melee(ANIMATION, speed = atkCombat.weapon.speed + EXTRA_ATTACK_DELAY) }

    launched { attacker.graphic(GRAPHIC); damage }
}
