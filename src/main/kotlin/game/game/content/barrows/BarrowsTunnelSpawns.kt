package game.content.barrows

import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import kotlin.random.Random

/**
 * Door encounter weights and population limit from barrows_door_spawn_monster.
 * Rolls 0..11 choose uniformly among undefeated brothers, or a crypt rat when none remain.
 *
 * @author lare96
 */
object BarrowsTunnelSpawns {
    fun monsterForRoll(roll: Int): BarrowsMonster? {
        require(roll in 0..127)
        return when (roll) {
            in 0..11 -> null
            in 12..31 -> BarrowsMonster.ARMED_SKELETON
            in 32..63 -> BarrowsMonster.UNARMED_SKELETON
            in 64..95 -> BarrowsMonster.BLOODWORM
            else -> BarrowsMonster.CRYPT_RAT
        }
    }

    fun spawn(player: Player, random: Random = Random.Default): Npc? {
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.position.z != 0 ||
            !BarrowsAreaListener.inside(player.position) || BarrowsRun.state(player) == 0 ||
            player.world.locator.findNpcs(player.position, 5) { true }.size >= 12) return null

        var monster = monsterForRoll(random.nextInt(128))
        if (monster == null) {
            val brothers = BarrowsBrother.entries.filterNot { BarrowsProgress.isKilled(player, it) }
            if (brothers.isNotEmpty()) return BarrowsEncounters.spawn(player, brothers.random(random))
            monster = BarrowsMonster.CRYPT_RAT
        }
        val position = BarrowsEncounters.nearbyTile(player, player.position) ?: return null
        val npc = BarrowsTunnelNpc(player, monster, position)
        return if (player.world.npcs.add(npc)) npc else null
    }
}
