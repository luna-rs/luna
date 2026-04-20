package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import api.predef.*
import engine.controllers.Controllers.inMultiArea
import io.luna.game.model.Direction
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_HALBERD
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

val MAX_PLAYER_TARGETS = 3
val MAX_NPC_TARGETS = 10

/**
 * The dragon mace special attack animation id.
 */
val ANIMATION = 1203

/**
 * The graphic displayed when the dragon mace special attack is performed.
 */
val GRAPHIC = Graphic(282, 100, 0)

fun resolveAdjacentTiles(from: Locatable, to: Locatable): Pair<Position, Position> {
    val fromPos = from.abs()
    val toPos = to.abs()
    val direction = Direction.between(fromPos, toPos)
    val adjacent = when (direction) {
        Direction.NORTH, Direction.SOUTH -> Direction.WEST to Direction.EAST
        Direction.WEST, Direction.EAST -> Direction.NORTH to Direction.SOUTH
        Direction.NORTH_WEST -> Direction.SOUTH_WEST to Direction.NORTH_EAST
        Direction.NORTH_EAST -> Direction.SOUTH_EAST to Direction.NORTH_WEST
        Direction.SOUTH_WEST -> Direction.NORTH_WEST to Direction.SOUTH_EAST
        Direction.SOUTH_EAST -> Direction.NORTH_EAST to Direction.SOUTH_WEST
        else -> throw IllegalStateException("No valid adjacent direction.")
    }
    return toPos.translate(1, adjacent.first) to toPos.translate(1, adjacent.second)
}

fun resolveDamage(attacker: Player, victim: Mob, reducedAccuracy: Boolean = false): CombatDamage  {
    val request = CombatDamageRequest.builder(attacker, victim, CombatDamageType.MELEE).setPercentBonusDamage(0.10)
    if(reducedAccuracy) {
        request.setFlatBonusAccuracy(-0.25)
    }
    return request.build().resolve()
}


attack(type = DRAGON_HALBERD,
       drain = 30,
       damageBonus = 0.10) {

    attack { melee(ANIMATION) }

    launched {
        if (victim.size() > 1) {
            // Deal additional hit against large targets.
            resolveDamage(attacker, victim, true).apply(attack)
        } else if (attacker.inMultiArea() && victim.inMultiArea()) {
            // Deal damage to adjacent targets in multi-combat.
            val (tile1, tile2) = resolveAdjacentTiles(attacker, victim)
            val cond: (Mob) -> Boolean = {
                // Filter for potential enemies on the correct tiles.
                it != attacker && it != victim &&
                        it.inMultiArea() && (it.position == tile1 || it.position == tile2)
            }
            val local = if (victim is Player) world.locator.findViewablePlayers(victim, cond) else
                world.locator.findViewableNpcs(victim, cond)
            var targets = if (victim is Player) MAX_PLAYER_TARGETS else MAX_NPC_TARGETS
            for (other in local) {
                // Apply damage to filtered targets.
                resolveDamage(attacker, other).apply(attack)
                if (--targets < 1) {
                    // Maximum number of targets damaged.
                    break
                }
            }

        }
        attacker.graphic(GRAPHIC);
        damage
    }
}