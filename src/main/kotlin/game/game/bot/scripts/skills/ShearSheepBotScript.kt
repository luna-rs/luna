package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.TargetingZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.ext.totalItemAmount
import api.predef.world
import game.skill.crafting.shearing.SheepShearing
import game.skill.crafting.shearing.SheepShearing.SHEARS
import game.skill.crafting.shearing.SheepShearing.ShearAction
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.Position
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Gathers a batch of wool in Lumbridge and queues ordinary textile spinning at the castle.
 *
 * @author lare96
 */
class ShearSheepBotScript(bot: Bot, duration: Duration) :
    TargetingZonedBotScript<Npc>(bot, duration, mutableListOf(SubZone.LUMBRIDGE_SHEEP_PEN)) {

    companion object {
        private const val STOCK_TARGET = 27
        private val RECIPE = Textile.BALL_OF_WOOL

        private fun hasStock(bot: Bot): Boolean =
            bot.totalItemAmount(RECIPE.rawItem.id).toLong() + bot.totalItemAmount(RECIPE.processedItem.id) >= STOCK_TARGET

        /**
         * Checks that the bot needs wool and can supply the shears.
         */
        internal fun eligible(bot: Bot): Boolean =
            !hasStock(bot) && (bot.totalItemAmount(SHEARS) > 0 || bot.totalItemAmount(Currency.COINS.id) > 0)
    }

    override val targetSearchTimeout = 2.minutes

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (hasStock(bot)) return false
        if (bot.inventory.isFull && !handler.banking.travelToBankDepositAll()) return false
        if (SHEARS !in bot.inventory) handler.shop.obtainWantedItem(SHEARS)
        return SHEARS in bot.inventory
    }

    override suspend fun equipment() = null

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean = false

    override suspend fun beforeZoneWork(): Boolean {
        if (canGather()) return false
        duration = Duration.ZERO
        return true
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        val zone = activeZone ?: return mutableListOf()
        return world.locator.findNpcs(searchBase, searchRadius, true) {
            it.position in zone.area && SheepShearing.canShear(it)
        }
    }

    override suspend fun interactionOption(target: Npc): Int? =
        1.takeIf { SheepShearing.canShear(target) }

    override suspend fun interactWithTarget(option: Int, target: Npc): Boolean {
        val zone = activeZone ?: return false
        if (!handler.inventory.useItem(SHEARS).onNpc(target) {
                isActionSafe() && canGather() && target.position in zone.area && SheepShearing.canShear(target)
            }) return false
        waitFor(3.seconds) { !SheepShearing.canShear(target) || bot.actions.first(ShearAction::class.java) != null }
        return true
    }

    override suspend fun refocus(): Boolean = bot.actions.first(ShearAction::class.java) == null

    private fun canGather(): Boolean = SHEARS in bot.inventory && !bot.inventory.isFull && !hasStock(bot)

    override suspend fun finish() {
        bot.actions.first(ShearAction::class.java)?.interrupt()
    }

    override suspend fun completed() {
        val spin = SpinTextileBotScript(bot, 20.minutes, mutableListOf(SubZone.LUMBRIDGE_SPINNING_WHEEL), RECIPE)
        if (spin.isEligible()) bot.scriptStack.pushTail(spin, 2)
    }

    override fun snapshot(): ZonedBotScriptData = ZonedBotScriptData().also {
        it.duration = remainingDuration()
        it.zones = originalZones.toMutableList()
    }
}
