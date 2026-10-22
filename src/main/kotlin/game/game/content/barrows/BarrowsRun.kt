package game.content.barrows

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import kotlin.random.Random

/**
 * Saved run lifecycle from barrows_full_reset and barrows_crypt_enter in the pinned Lost City contribution.
 * Parent 452 uses bits 0..5 for the tunnel brother and bit 26 for collapse; these are not cache varbits.
 *
 * @author lare96
 */
object BarrowsRun {
    const val COLLAPSING_MASK = 1 shl 26
    private const val ENTRANCE_MASK = 63

    fun state(player: Player): Int = player.varpManager.getValue(PersistentVarp.BARROWS)

    fun tunnelBrother(player: Player): BarrowsBrother? = BarrowsBrother.entries.getOrNull(state(player) and ENTRANCE_MASK)

    fun chamber(player: Player): BarrowsChamber? =
        BarrowsChamber.entries.singleOrNull { it.enabled.read(state(player)) == 1 }

    /**
     * Starts a run on the first dig, or replaces a looted/collapsing run on the next dig.
     * Leaving, dying, logging out and digging into another crypt do not otherwise clear progress.
     */
    fun enterCrypt(player: Player, random: Random = Random.Default) {
        val oldState = state(player)
        if (oldState == 0 || oldState and COLLAPSING_MASK != 0) {
            val layout = BarrowsMaze.randomize(0, random) or random.nextInt(6)
            player.varpManager.setAndSendValue(PersistentVarp.BARROWS, layout)
            player.varpManager.setAndSendValue(PersistentVarp.BARROWS_KILLS, 0)
        }
    }
}
