package game.content.barrows

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.player.Jingles
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.JingleMessageWriter
import kotlin.random.Random

/**
 * Opens the personal chest and awards a run once. Full inventories retain the original rolled reward
 * across logout, so making room cannot reroll loot. This retry behavior is a server adaptation.
 *
 * @author lare96
 */
object BarrowsChest {
    val position = Position(3551, 9695)
    private var Player.pendingBarrowsReward by Attr.nullableObj(Reward::class).persist("barrows_pending_reward")

    /**
     * A saved reward, including an empty roll, awaiting enough inventory space.
     *
     * @author lare96
     */
    data class Reward(val items: Array<Item>)

    /**
     * Activates the lazily loaded attribute even when a player logs in outside Barrows.
     */
    fun restore(player: Player) {
        player.pendingBarrowsReward
    }

    fun search(player: Player, random: Random = Random.Default) {
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.position.z != 0 ||
            !player.position.isWithinDistance(position, 3) || BarrowsRun.state(player) == 0) return
        val manager = player.varpManager
        val kills = manager.getValue(PersistentVarp.BARROWS_KILLS)
        if (BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK != 0) {
            player.sendMessage("The chest is empty.")
            return
        }
        if (BarrowsVarbit.CHEST_OPEN.read(kills) == 0) {
            BarrowsRun.tunnelBrother(player)?.let {
                BarrowsEncounters.spawn(player, it, "You dare steal from us!")
            }
            manager.setAndSendValue(PersistentVarp.BARROWS_KILLS, BarrowsVarbit.CHEST_OPEN.write(kills, 1))
            return
        }
        val reward = player.pendingBarrowsReward ?: Reward(BarrowsRewards.roll(kills, random).toTypedArray()).also {
            player.pendingBarrowsReward = it
        }
        if (!player.inventory.hasSpaceForAll(reward.items.asList())) {
            player.sendMessage("You need more inventory space to take the treasure.")
            return
        }
        // All checks and mutations run together on the game thread. Inventory and flags share the player save.
        player.inventory.addAll(reward.items.asList())
        manager.setAndSendValue(PersistentVarp.BARROWS, BarrowsRun.state(player) or BarrowsRun.COLLAPSING_MASK)
        player.pendingBarrowsReward = null
        player.queue(JingleMessageWriter(Jingles.GRAVE_ROBBER))
        BarrowsCollapse.enter(player)
    }
}
