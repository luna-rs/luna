package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.content.yanilleDungeon.OpenSinisterChestAction
import game.content.yanilleDungeon.SinisterChest
import game.skill.thieving.lockedDoor.YanilleDoorAction
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Exchanges one owned sinister key at a time for the native chest's herbs, then banks the reward.
 *
 * Travel uses the dungeon's ordinary obstacle or lockpick crossings, including recovery stairs after a fall.
 * Opening still runs the player item-on-object handler: it owns the key, four-tick action, gas, herbs and chest
 * reset. Live immunity and eight free slots are required immediately before the click.
 *
 * @author lare96
 */
class SinisterChestBotScript(bot: Bot, duration: Duration,
    zones: MutableList<SubZone> = mutableListOf(SubZone.YANILLE_DUNGEON_SINISTER_CHEST)) :
    ZonedBotScript(bot, duration, zones) {

    companion object {

        /**
         * Closed chest position verified against the revision 377 cache.
         */
        val CHEST_POSITION = Position(2561, 9507)

        private const val MAX_FAILURES = 3

        /**
         * Saved session budget and bounded failed opening attempts.
         *
         * @author lare96
         */
        class ChestData : ZonedBotScriptData() {
            var failures = 0

            override fun load(data: JsonObject) {
                super.load(data)
                failures = (data.get("failures")?.asInt ?: 0).coerceIn(0, MAX_FAILURES)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("failures", failures)
            }
        }

        /**
         * Either the pipe route or the native back-door lock is available from owned supplies and current levels.
         */
        fun accessReady(bot: Bot): Boolean = bot.agility.staticLevel >= 49 && bot.agility.level >= 49 ||
            bot.thieving.staticLevel >= YanilleDoorAction.REQUIRED_LEVEL &&
                bot.thieving.level >= YanilleDoorAction.REQUIRED_LEVEL &&
                (YanilleDoorAction.LOCKPICK in bot.inventory || YanilleDoorAction.LOCKPICK in bot.bank)

        /**
         * Whether an owned key and safe trip supplies can be used without manufacturing or buying stock.
         */
        fun ready(bot: Bot): Boolean = accessReady(bot) && bot.hitpoints.staticLevel >= 25 &&
            keys(bot) > 0 && YanilleDungeonBotSupplies.owned(bot)

        private fun keys(bot: Bot): Long = bot.inventory.computeAmountForId(SinisterChest.KEY).toLong() +
            bot.bank.computeAmountForId(SinisterChest.KEY)
    }

    private var failures = 0

    constructor(bot: Bot, data: ChestData) : this(bot, data.duration, data.zones) {
        failures = data.failures
    }

    override suspend fun onInit(resumed: Boolean): Boolean =
        originalZones.all { it == SubZone.YANILLE_DUNGEON_SINISTER_CHEST } && accessReady(bot) &&
            bot.hitpoints.staticLevel >= 25 && (keys(bot) > 0 || SinisterChest.HERBS.any { it in bot.inventory })

    override suspend fun equipment() = null

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean =
        handler.webWalkTo(zone = zone, bank = bank, attempts = 4, fallback = false)

    override suspend fun onBankOpen(initial: Boolean) {
        if (failures >= MAX_FAILURES || keys(bot) == 0L || !accessReady(bot)) {
            stop()
            return
        }
        handler.banking.clickBankingMode(false)
        if ((bot.agility.staticLevel < 49 || bot.agility.level < 49) &&
            (!handler.banking.withdraw(Item(YanilleDoorAction.LOCKPICK)) ||
                YanilleDoorAction.LOCKPICK !in bot.inventory)) {
            stop()
            return
        }
        if (!YanilleDungeonBotSupplies.withdrawAntipoison(bot) ||
            !handler.banking.withdraw(Item(SinisterChest.KEY)) || SinisterChest.KEY !in bot.inventory ||
            !handler.banking.withdrawAnyFood(amount = 4, minimumHeal = 10, retry = false) ||
            !handler.inventory.hasAnyFood(minimumHeal = 10) || bot.inventory.computeRemainingSize() < 8) stop()
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (bot.overlays.hasWindow()) return false
        if (!YanilleDungeonBotSupplies.eatIfHurt(bot) ||
            (bot.status.isPoisoned() && !YanilleDungeonBotSupplies.protect(bot))) {
            forceBanking = true
        }
        if (SinisterChest.KEY !in bot.inventory) forceBanking = true
        return false
    }

    override suspend fun executeInZone(): Boolean {
        if (bot.actions.size(ActionType.WEAK) > 0 || bot.isLocked) return true
        if (SinisterChest.KEY !in bot.inventory || bot.inventory.computeRemainingSize() < 8 ||
            !YanilleDungeonBotSupplies.eatIfHurt(bot)) {
            forceBanking = true
            return true
        }
        val opened = withTimeoutOrNull(90_000) {
            if (!YanilleDungeonBotSupplies.protect(bot)) return@withTimeoutOrNull false
            if (!waitFor(15.seconds) {
                    world.locator.findObjectsOnTile(CHEST_POSITION) { it.id == SinisterChest.CLOSED }.isNotEmpty()
                }) return@withTimeoutOrNull false
            val chest = world.locator.findObjectsOnTile(CHEST_POSITION) { it.id == SinisterChest.CLOSED }
                .firstOrNull() ?: return@withTimeoutOrNull false
            val before = bot.inventory.computeAmountForId(SinisterChest.KEY)
            val herbs = SinisterChest.HERBS.groupingBy { it }.eachCount()
            val previous = herbs.mapValues { (id, _) -> bot.inventory.computeAmountForId(id) }
            if (!handler.inventory.useItem(SinisterChest.KEY).onObject(chest) {
                    isActionSafe() && bot.status.isAntiPoison() && bot.health > 12 &&
                        bot.inventory.computeRemainingSize() >= 8 && SinisterChest.KEY in bot.inventory
                }) return@withTimeoutOrNull false
            waitFor(15.seconds) {
                bot.inventory.computeAmountForId(SinisterChest.KEY) < before && !bot.isLocked &&
                    bot.actions.first(OpenSinisterChestAction::class.java) == null
            } && herbs.all { (id, amount) -> bot.inventory.computeAmountForId(id) == previous.getValue(id) + amount }
        } == true
        if (opened) failures = 0 else if (isActionSafe()) failures++
        forceBanking = true
        return true
    }

    override suspend fun beforeLeavingZone(): Boolean {
        if (bot.actions.first(OpenSinisterChestAction::class.java) != null || bot.isLocked) return false
        return SinisterChest.HERBS.none { it in bot.inventory } || handler.banking.travelToBankDepositAll()
    }

    override fun snapshot(): ChestData = ChestData().also {
        it.duration = remainingDuration().coerceAtLeast(Duration.ZERO)
        it.zones = originalZones.toMutableList()
        it.failures = failures
    }
}
