package game.skill.agility

import api.predef.*
import api.predef.ext.*
import engine.combat.status.hooks.PoisonStatusEffect
import game.player.Sound
import game.skill.Skills
import io.luna.game.model.Position
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Steps across Isafdar's two-tile tripwires, retaining their native poison and arrow damage on a failed attempt.
 *
 * @author lare96
 */
object IsafdarTripwire {

    const val ID = 3921

    /**
     * Finds the ground immediately before and after the rotated two-tile trap.
     */
    fun sides(position: Position, direction: ObjectDirection): Pair<Position, Position> =
        if (direction == ObjectDirection.WEST || direction == ObjectDirection.EAST) {
            position.translate(0, -1) to position.translate(0, 2)
        } else {
            position.translate(-1, 0) to position.translate(2, 0)
        }

    /**
     * Aligns the player with the trap before making the native three-tile step-over movement.
     */
    fun cross(plr: Player, wire: GameObject, roll: () -> Double = { rand().nextDouble() }) {
        if (wire.id != ID || wire.position.z != plr.z || plr.isLocked || !plr.canTraverseIsafdar()) return
        val (first, second) = sides(wire.position, wire.direction)
        val start = if (plr.position.computeLongestDistance(first) <= plr.position.computeLongestDistance(second)) first else second
        if (plr.position.computeLongestDistance(start) > AgilityApproach.RADIUS) return
        val destination = if (start == first) second else first
        val dx = (destination.x - start.x) / 3
        val dy = (destination.y - start.y) / 3
        val enter = enter@ {
            if (plr.isLocked || !plr.canTraverseIsafdar() || plr.position != start) return@enter
            plr.submitAction(AgilityAction(plr, listOf(
                AgilityStep(start.translate(dx, dy), 1, jump = true),
                AgilityStep(start.translate(dx, dy), 2, jump = true),
                AgilityStep(start.translate(2 * dx, 2 * dy), 1, animation = 1236, moveEnd = 30),
                AgilityStep(destination, 4, jump = true, begin = {
                    if (roll() >= Skills.successRate(30, 155, plr.agility.level)) {
                        plr.sendMessage("You snag the trip wire as you step over it.")
                        arrows(plr, wire)
                        plr.status.add(PoisonStatusEffect(plr, 10))
                        plr.damage(5)
                        plr.damage(5)
                        plr.playSound(Sound.TRIPWIRE_WITH_ARROWS)
                    } else {
                        plr.sendMessage("You successfully step over the tripwire.")
                    }
                    if (plr.canTraverseIsafdar()) plr.move(destination)
                })), "You step carefully over the tripwire.", valid = { plr.canTraverseIsafdar() }))
        }
        if (plr.position == start) enter() else plr.submitAction(AgilityApproach(plr, start) { enter() })
    }

    /**
     * Animates the four nearby arrow traps and their native iron-arrow projectiles when a wire is caught.
     */
    private fun arrows(plr: Player, wire: GameObject) {
        val offsets = if (wire.direction == ObjectDirection.WEST || wire.direction == ObjectDirection.EAST) {
            listOf(-1 to -1, 1 to -1, -1 to 2, 1 to 2)
        } else {
            listOf(-1 to 1, -1 to -1, 2 to 1, 2 to -1)
        }
        for ((dx, dy) in offsets) {
            val position = wire.position.translate(dx, dy)
            val trap = world.objects.findAll(position).filter { it.id == 3919 }.findFirst().orElse(null) ?: continue
            trap.animate(1213)
            LocalProjectile.followPath(ctx).setSourcePosition(position).setTargetPosition(plr.position)
                .setId(9).setStartHeight(4).setEndHeight(35).setTiming(0, 40).setInitialSlope(45)
                .setLaunchOffset(0).build().display()
        }
    }
}
