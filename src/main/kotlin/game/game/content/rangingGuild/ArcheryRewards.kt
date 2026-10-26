package game.content.rangingGuild

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * Fixed-price bundles displayed in the revision 377 ticket exchange's native Value/Buy grid.
 *
 * @author lare96
 */
object ArcheryRewards {
    /**
     * Ticket prices retained from Lost City's ticket merchant script.
     *
     * @author lare96
     */
    enum class Reward(val item: Int, val amount: Int, val cost: Int) {
        BARBED_TIPS(47, 30, 140), STUDDED_BODY(1129, 1, 150), RUNE_ARROWS(892, 50, 2000),
        COIF(1169, 1, 100), GREEN_BODY(1135, 1, 2400), ADAMANT_JAVELINS(829, 20, 2000)
    }

    fun open(p: Player, merchant: Npc) {
        if (ArcheryCompetition.near(p, merchant, 694)) p.overlays.open(Exchange(merchant))
    }

    fun select(p: Player, index: Int, item: Int, buy: Boolean): Boolean {
        val exchange = p.overlays.getOverlay(Exchange::class.java) ?: return false
        val reward = Reward.values().getOrNull(index) ?: return false
        if (reward.item != item || !ArcheryCompetition.near(p, exchange.merchant, 694)) return false
        if (!buy) {
            p.sendMessage("${reward.amount} ${Item(item).itemDef.name}: ${reward.cost} archery tickets.")
            return true
        }
        return ArcheryCompetition.purchase(p, Item(ArcheryCompetition.TICKET, reward.cost), Item(item, reward.amount))
    }

    /**
     * Binds purchases to the merchant who opened the exchange.
     *
     * @author lare96
     */
    class Exchange(val merchant: Npc) : StandardInterface(11942) {
        override fun open(player: Player) {
            super.open(player)
            player.queue(WidgetItemsMessageWriter(12033, Reward.values().map { Item(it.item, it.amount) }))
        }
    }
}
