package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.isRealBot
import api.bot.script.DynamicBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.rand
import api.predef.ext.get
import api.predef.ext.totalItemAmount
import engine.controllers.Controllers.inWilderness
import game.content.dwarfMulticannon.CannonStage
import game.content.dwarfMulticannon.DwarfMulticannon
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Buys missing cannon equipment and replenishes ammunition the bot can afford.
 *
 * @author lare96
 */
internal class BuyCannonBotScript private constructor(bot: Bot) : DynamicBotScript(bot) {

    override val allowsPaidTravel = false
    private var attempted = false

    companion object {
        private const val COINS = 995
        private const val ASK_TO_BUY = "I was hoping you might sell me a cannon?"
        private const val BUY_SET = "Ok, I'll take a cannon please."
        private var Bot.nextCannonShopAt by Attr.long()

        fun select(bot: Bot): BuyCannonBotScript? {
            if (!canShop(bot) || System.currentTimeMillis() < bot.nextCannonShopAt) return null
            val missing = missingParts(bot)
            val hardware = missing.isNotEmpty() && shop(bot)?.let { shop ->
                (canBuySet(bot) || affordableParts(bot, shop).isNotEmpty()) &&
                    bot.actionHandler.shop.shopDistance(shop) != null
            } == true
            if (!hardware && !needsAmmo(bot)) return null
            return BuyCannonBotScript(bot).takeIf {
                rand(selectionChance(bot.personality.intelligence, bot.personality.dexterity))
            }
        }

        internal fun selectionChance(intelligence: Double, dexterity: Double): Double =
            0.05 + 0.70 * intelligence.coerceIn(0.0, 1.0) * dexterity.coerceIn(0.0, 1.0)

        private fun canShop(bot: Bot): Boolean =
            bot.isRealBot() && !bot.isTemporary && bot.state == EntityState.ACTIVE &&
                !bot.client.isPendingLogout && !bot.inWilderness() &&
                ActionSafety.NON_COMBAT.allows(bot) && CannonBotSupport.compatibleBuild(bot) &&
                !CannonBotSupport.hasCannonOut(bot)

        private fun shop(bot: Bot): Shop? = bot.world.shops.lookup(DwarfMulticannon.SHOP_NAME).orElse(null)

        private fun missingParts(bot: Bot) = CannonStage.PARTS.filter { bot.totalItemAmount(it) == 0 }

        private fun needsAmmo(bot: Bot): Boolean =
            missingParts(bot).size < CannonStage.PARTS.size &&
                bot.totalItemAmount(DwarfMulticannon.CANNONBALL) < CannonBotSupport.RESTOCK_BELOW &&
                CannonAmmoSupply.canTry(bot, CannonBotSupport.ammoTarget(bot))

        private fun coins(bot: Bot): Long = bot.bank.computeNonDynamicAmountForId(COINS).toLong() +
            bot.inventory.computeNonDynamicAmountForId(COINS)

        private fun canBuySet(bot: Bot) =
            missingParts(bot).size == CannonStage.PARTS.size && coins(bot) >= DwarfMulticannon.SET_PRICE

        private fun affordableParts(bot: Bot, shop: Shop): Map<Int, Int> = buildMap {
            var remaining = coins(bot)
            for (id in missingParts(bot)) {
                val cost = shop.computeBuyValue(id, 1).orElse(0)
                if (cost.toLong() !in 1L..remaining) continue
                put(id, cost)
                remaining -= cost
            }
        }
    }

    override suspend fun run(): Boolean {
        if (attempted || !canShop(bot)) return true
        attempted = true
        bot.nextCannonShopAt = System.currentTimeMillis() + 10.minutes.inWholeMilliseconds
        withTimeoutOrNull(6.minutes) { buyHardware() }
        if (canShop(bot) && needsAmmo(bot)) CannonAmmoSupply.restock(bot, CannonBotSupport.ammoTarget(bot))
        return true
    }

    private suspend fun buyHardware() {
        if (missingParts(bot).isEmpty()) return
        val shop = shop(bot) ?: return
        if (handler.shop.shopDistance(shop) == null) return
        if (canBuySet(bot)) {
            buySet()
            return
        }
        val parts = affordableParts(bot, shop)
        if (parts.isEmpty() || !carryPayment(parts.keys, parts.values.sum())) return
        val overlay = handler.shop.travelToShop(shop) { canShop(bot) } ?: return
        try {
            for ((id, quote) in parts) {
                if (!canShop(bot) || !handler.shop.canTradeWith(shop)) break
                if (bot.totalItemAmount(id) == 0) handler.shop.buyQuoted(shop, id, 1, quote)
            }
        } finally {
            if (bot.overlays[ShopInterface::class] === overlay) bot.overlays.closeWindows()
        }
    }

    private suspend fun carryPayment(items: Collection<Int>, cost: Int): Boolean {
        val incoming = items.map { Item(it) }
        val missing = cost - bot.inventory.computeNonDynamicAmountForId(COINS)
        val basket = if (missing > 0) incoming + Item(COINS, missing) else incoming
        if (missing <= 0 && bot.inventory.hasSpaceForAll(basket)) {
            return true
        }
        if (!handler.banking.travelToBankOpen() || !canShop(bot)) return false
        if (!bot.inventory.hasSpaceForAll(basket) && !handler.banking.depositInventory(setOf(COINS))) return false
        handler.banking.clickBankingMode(noted = false)
        val withdraw = cost - bot.inventory.computeNonDynamicAmountForId(COINS)
        return withdraw <= 0 || handler.banking.withdraw(Item(COINS, withdraw)) { canShop(bot) }
    }

    private suspend fun buySet() {
        val items = CannonStage.PARTS + setOf(DwarfMulticannon.AMMO_MOULD, DwarfMulticannon.INSTRUCTION_MANUAL)
        val cost = DwarfMulticannon.SET_PRICE
        if (!carryPayment(items, cost) || !canShop(bot)) return
        val zone = SubZone.ICE_MOUNTAIN
        if (!handler.webWalkTo(zone = zone, bank = BankAccess.NONE)) return
        fun canPay() = canShop(bot) && canBuySet(bot) &&
            bot.inventory.computeNonDynamicAmountForId(COINS) >= cost &&
            bot.inventory.hasSpaceForAll(items.map { Item(it) })
        if (!canPay()) return
        val nulodion = bot.world.locator.findNpcs(zone.inside, zone.area.tileRadius) {
            it.id == DwarfMulticannon.NULODION
        }.firstOrNull() ?: return
        repeat(2) {
            if (!canPay() || !handler.interactions.approach(nulodion, InteractionPolicy.STANDARD_SIZE,
                    canContinue = ::canPay)) return
            val purchased = BotNpcDialogues.respond(bot, nulodion, listOf(ASK_TO_BUY, BUY_SET), timeout = 30.seconds,
                allowed = ::canPay, completed = { missingParts(bot).isEmpty() },
                playerSpeech = setOf("Hello.", "Not bad thanks, yourself?", "I was hoping you might sell me a cannon.",
                    "How much?", "That's not cheap!"), advanceSpeech = true) {
                bot.output.sendNpcInteraction(1, nulodion)
            }
            if (purchased) return
        }
    }
}
