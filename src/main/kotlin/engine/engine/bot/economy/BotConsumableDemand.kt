package engine.bot.economy

import api.bot.action.trading.BotTradeIntentions
import api.bot.isRealBot
import io.luna.game.model.mob.Player

/**
 * Replenishes a single useful consumable only after a bot actually exhausts it through native gameplay.
 * Requests use ordinary shopping, cash reserves and expiry; they never interrupt combat or award items.
 *
 * @author lare96
 */
object BotConsumableDemand {

    /**
     * Preserves one working replacement for a destroyed item or a fully exhausted charge family.
     * Partially charged jewellery and rechargeable empty items do not create another purchase here.
     */
    fun depleted(player: Player, replacementId: Int) {
        if (!player.isRealBot()) return
        BotProductionMetrics.recordSink(replacementId)
        BotTradeIntentions.recordSupplies(player, emptyList(), tools = setOf(replacementId), stockTarget = 1)
    }
}
