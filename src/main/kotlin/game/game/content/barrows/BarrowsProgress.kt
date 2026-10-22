package game.content.barrows

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Updates the saved Barrows kill parent varp on the game thread.
 *
 * Lost City's barrows_increment_kc at 0e7d7a8291d837f48646b2a962f3c5e208e744ad adds the NPC's
 * combat level to a score capped at 1,000, and adds one to the displayed count. Its 0xFFFF count
 * cap exceeds varbit 464's 15 bits; use the cache field's actual maximum to avoid wrapping.
 * PersistentVarpManager handles character saving and the existing login handler restores the client value.
 *
 * @author lare96
 */
object BarrowsProgress {

    fun isKilled(player: Player, brother: BarrowsBrother): Boolean =
        brother.killed.read(player.varpManager.getValue(PersistentVarp.BARROWS_KILLS)) == 1

    fun killedBrothers(player: Player): List<BarrowsBrother> = BarrowsBrother.entries.filter { isKilled(player, it) }

    /**
     * Credits an owned brother once per run, including its combat level and the displayed kill count.
     */
    fun recordBrotherKill(player: Player, brother: BarrowsBrother): Boolean {
        if (isKilled(player, brother)) return false
        val manager = player.varpManager
        val value = brother.killed.write(manager.getValue(PersistentVarp.BARROWS_KILLS), 1)
        manager.setAndSendValue(PersistentVarp.BARROWS_KILLS, increment(value, brother.combatLevel))
        return true
    }

    /**
     * Records an attributed tunnel kill, preserving the brother and chest flags in the same parent.
     * Sends one combined update so the client and saved value agree.
     */
    fun recordMonsterKill(player: Player, monster: BarrowsMonster) {
        val manager = player.varpManager
        val value = manager.getValue(PersistentVarp.BARROWS_KILLS)
        manager.setAndSendValue(PersistentVarp.BARROWS_KILLS, increment(value, monster.combatLevel))
    }

    private fun increment(parent: Int, combatLevel: Int): Int {
        var value = parent
        val score = BarrowsVarbit.MONSTER_SCORE
        val count = BarrowsVarbit.KILL_COUNT
        value = score.write(value, (score.read(value) + combatLevel).coerceAtMost(1000))
        value = count.write(value, (count.read(value) + 1).coerceAtMost(count.maxValue))
        return value
    }
}
