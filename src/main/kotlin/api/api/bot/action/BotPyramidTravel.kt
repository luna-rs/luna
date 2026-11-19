package api.bot.action

import api.bot.webwalk.plan.BankAccess
import api.predef.ext.totalItemAmount
import game.npc.spawn.shantayPass.ShantayPass
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Routes bots through the pyramid's native entrances and supplies a paid Shantay pass when the web needs one.
 *
 * @author lare96
 */
internal object BotPyramidTravel {
    val altarPosition = Position(3232, 9311)
    val altarStand = Position(3232, 9312)
    const val ALTAR = 6552
    const val PASS_BUDGET = 10
    private const val COINS = 995
    private const val SHOP = "Shantay Pass Shop"

    /**
     * Checks if the bot can bring a ticket or pay for one before booking a hunt.
     */
    fun canSupplyPass(bot: Bot): Boolean = bot.totalItemAmount(ShantayPass.PASS) > 0 ||
        bot.totalItemAmount(COINS) >= PASS_BUDGET

    /**
     * Prepares owned travel supplies at an already open bank, before equipment trips leave for the pyramid.
     */
    suspend fun withdrawPassOrFare(bot: Bot, handler: BotActionHandler, allowed: () -> Boolean): Boolean {
        if (ShantayPass.PASS in bot.inventory) return true
        if (ShantayPass.PASS in bot.bank) {
            return handler.banking.withdrawUnnoted(Item(ShantayPass.PASS), allowed)
        }
        val missing = PASS_BUDGET - bot.inventory.computeAmountForId(COINS)
        return missing <= 0 || handler.banking.withdrawUnnoted(Item(COINS, missing), allowed)
    }

    /**
     * Tries the carried or bank-assisted route first. If a pass is missing, buys one through Shantay's native shop.
     * A bounded equipment trip carries funding from its own bank visit and uses no additional bank detour.
     */
    suspend fun travel(bot: Bot, handler: BotActionHandler, destination: Position, bank: BankAccess,
                       allowed: () -> Boolean): Boolean {
        if (!allowed()) return false
        if (handler.webWalkTo(destination, attempts = 2, bank = bank, fallback = false)) return allowed()
        if (!allowed() || ShantayPass.PASS in bot.inventory) return false
        if (bank != BankAccess.NONE && (ShantayPass.PASS in bot.bank ||
                bot.inventory.computeAmountForId(COINS) < PASS_BUDGET)) {
            if (!canSupplyPass(bot) || !handler.banking.travelToBankOpen() || !allowed() ||
                !withdrawPassOrFare(bot, handler, allowed)) return false
            handler.widgets.clickCloseInterface()
        }
        if (ShantayPass.PASS !in bot.inventory) {
            val shop = bot.world.shops.lookup(SHOP).orElse(null) ?: return false
            if (bot.inventory.computeAmountForId(COINS) < PASS_BUDGET ||
                handler.shop.travelToShop(shop, allowed) == null) return false
            try {
                if (!allowed() || !handler.shop.buyQuoted(shop, ShantayPass.PASS, 1, PASS_BUDGET)) return false
            } finally {
                handler.widgets.clickCloseInterface()
            }
        }
        return allowed() && handler.webWalkTo(destination, attempts = 2, bank = bank, fallback = false) && allowed()
    }
}
