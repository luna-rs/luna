package game.combat.npcHooks.kalphiteQueen

import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation

/**
 * The Queen's phase lifecycle and cocoon workers, adapted from Lost City's area_kalphite scripts.
 *
 * @author lare96
 */
object KalphiteQueen {
    private val cocoons = listOf(30 to 34, 37 to 11, 24 to 9, 21 to 9, 8 to 23, 16 to 36,
        30 to 46, 32 to 46, 46 to 32, 46 to 29, 34 to 30, 30 to 26, 26 to 30)
        .map { (x, y) -> Position(3456 + x, 9472 + y) }

    /**
     * Changes form while retaining drained combat stats, healing only hitpoints and clearing phase-one credit.
     */
    fun rise(npc: Npc) {
        val levels = npc.skills.map { it.level }
        npc.transform(1160)
        npc.skills.forEach { if (it.id != Skill.HITPOINTS) it.level = levels[it.id] }
        npc.health = 255
        npc.combat.damageStack.clear()
        npc.combat.target = null
        npc.combat.autoRetaliateTarget = null
        npc.animation(Animation(1181))
        npc.world.scheduleOnce(2000) {
            // A removed/dead Queen must not replace the normal death respawn.
            if (npc.state == EntityState.ACTIVE && npc.id == 1160 && npc.isAlive) {
                val replacement = Npc(npc.context, 1158, npc.basePosition)
                replacement.respawnTicks = npc.respawnTicks
                npc.world.npcs.remove(npc)
                npc.world.npcs.add(replacement)
            }
        }
    }

    /**
     * Opens every nearby cocoon on a successful retaliation spawn roll. Workers expire after 500 ticks.
     */
    fun hatch(npc: Npc) {
        val centre = npc.position.translate(2, 2)
        for (position in cocoons) {
            if (!centre.isWithinDistance(position, 10)) continue
            val egg = npc.world.objects.findAll(position).filter { it.id == 3833 }.findFirst().orElse(null)
                ?: continue
            egg.animate(1174)
            val worker = Npc(npc.context, 1156, position)
            worker.respawnTicks = -1
            npc.world.npcs.add(worker)
            worker.animation(Animation(1188))
            worker.navigator.navigate(position.translate(2, egg.direction.toNormalDirection()), false)
            npc.world.scheduleOnce(500) {
                if (worker.state == EntityState.ACTIVE) npc.world.npcs.remove(worker)
            }
        }
    }
}
