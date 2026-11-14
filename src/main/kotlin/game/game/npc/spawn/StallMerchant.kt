package game.npc.spawn

import game.skill.thieving.stealFromStall.StealFromAction
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.mob.Player
import kotlin.time.Duration.Companion.milliseconds

/**
 * Refuses market trading while a merchant still remembers a player's theft from that kind of stall.
 * Cooldowns use Lost City's stealing.constant durations in 600 millisecond game ticks.
 *
 * @author lare96
 */
object StallMerchant {

    /**
     * Checks all full stall variants so moving to another merchant cannot clear the theft cooldown.
     */
    fun recentTheft(player: Player, stall: ThievingStallType, ticks: Int): Boolean =
        stall.stalls.any { StealFromAction.stoleRecently(player, it.first, (ticks * 600L).milliseconds) }

    /**
     * Opens the merchant's shop, or refuses the thief and alerts any nearby guards.
     */
    fun open(player: Player, npcId: Int, shop: String, stall: ThievingStallType, ticks: Int) {
        if (recentTheft(player, stall, ticks)) {
            player.newDialogue().npc(npcId, "You stole from me! Come back when you've considered your actions.").open()
            StealFromAction.callGuards(player)
            return
        }
        GeneralStore.open(player, shop)
    }
}
