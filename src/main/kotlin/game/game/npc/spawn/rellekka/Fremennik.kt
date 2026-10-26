package game.npc.spawn.rellekka

import io.luna.game.model.mob.Player

/**
 * Shared checks for Rellekka's Fremennik merchants, who will only trade with players the Fremenniks accept as one of
 * their own.
 */
object Fremennik {

    /**
     * Returns whether [plr] is a Fremennik, which Rellekka's merchants require before they will trade with them.
     */
    fun isFremennik(plr: Player): Boolean {
        // TODO The Fremennik Trials: only players who have finished the quest are Fremenniks.
        return true
    }
}
