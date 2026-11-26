package game.bot.scripts.skills

import api.attr.Attr
import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.crafting
import api.predef.ext.totalItemAmount
import api.predef.firemaking
import api.predef.hitpoints
import com.google.gson.JsonObject
import game.content.mortton.FlamtaerTemple
import game.content.mortton.PyreLogs
import game.content.mortton.ShadePyres
import game.content.mortton.ShadeRewards
import game.skill.firemaking.Firemaking
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Uses owned shade remains for Prayer training and brings the native chest rewards back to the bank.
 *
 * @author lare96
 */
class MorttonPrayerBotScript private constructor(bot: Bot, private val deadline: Long) : BotScript(bot) {
    companion object {
        private const val BATCH = 4
        private const val PHRIN_SHADOW = 1243
        private const val PHRIN_COMBAT_LEVEL = 45
        private const val OLIVE_OIL = 3424
        private val TEMPLE_APPROACH = Position(3506, 3313)
        private val PYRE_APPROACH = Position(3495, 3270)
        private val TOMB_DOOR = Position(3485, 3320)
        private val EXIT = Position(3493, 9726)
        private var Bot.retryMorttonAfter by Attr.long { 0L }

        fun select(bot: Bot, duration: Duration): MorttonPrayerBotScript? {
            if (System.currentTimeMillis() < bot.retryMorttonAfter ||
                ShadeRewards.remains.none { bot.totalItemAmount(it) > 0 } || logsFor(bot) == null) return null
            return MorttonPrayerBotScript(bot,
                System.currentTimeMillis() + duration.coerceIn(8.minutes, 12.minutes).inWholeMilliseconds)
        }

        private fun logsFor(bot: Bot): PyreLogs? {
            val unlocked = PyreLogs.entries.filter { bot.firemaking.level >= it.level }
            return unlocked.firstOrNull { bot.totalItemAmount(it.item) > 0 }
                ?: unlocked.firstOrNull {
                    it.doses == 2 && bot.totalItemAmount(it.wood) > 0 &&
                        (bot.crafting.level >= FlamtaerTemple.CRAFTING_LEVEL ||
                            FlamtaerTemple.sacredOils.take(3).any { oil -> bot.totalItemAmount(oil) > 0 })
                }
        }
    }

    /**
     * The saved time limit. Resumed trips read the bot's actual inventory and bank to decide what remains.
     *
     * @author lare96
     */
    class MorttonData : BotScriptData() {
        var deadline = 0L

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: MorttonData) : this(bot, data.deadline)

    private val world get() = bot.world
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun snapshot() = MorttonData().also { it.deadline = deadline }

    override suspend fun run(): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) return true
        val completed = withTimeoutOrNull(remaining) { outing() } == true
        if (!completed) bot.retryMorttonAfter = System.currentTimeMillis() + 10.minutes.inWholeMilliseconds
        return true
    }

    private suspend fun outing(): Boolean {
        if (inCatacombs()) {
            if (!exitCatacombs()) return false
        }
        val first = if (bot.totalItemAmount(ShadeRewards.PHRIN_REMAINS) > 0) {
            ShadeRewards.PHRIN_REMAINS
        } else {
            ShadeRewards.LOAR_REMAINS
        }
        val burnt = cremateBatch(first)
        if (first == ShadeRewards.LOAR_REMAINS && bot.combatLevel >= PHRIN_COMBAT_LEVEL &&
            ShadeRewards.keys.any { bot.totalItemAmount(it) > 0 }) {
            if (!gatherPhrin()) return false
            cremateBatch(ShadeRewards.PHRIN_REMAINS)
        }
        val opened = openChests()
        return handler.banking.travelToBankDepositAll() && (burnt || opened)
    }

    private suspend fun cremateBatch(remains: Int): Boolean {
        val logs = logsFor(bot) ?: return false
        val ownedPrepared = bot.totalItemAmount(logs.item)
        val requested = minOf(BATCH, bot.totalItemAmount(remains),
            (ownedPrepared.toLong() + bot.totalItemAmount(logs.wood)).coerceAtMost(BATCH.toLong()).toInt())
        if (requested <= 0) return false
        if (ownedPrepared < requested && logs.doses == 2) makePyreLogs(logs, requested - ownedPrepared)
        val count = minOf(requested, bot.totalItemAmount(logs.item))
        if (count <= 0 || !bankLoad(listOf(Item(remains, count), Item(logs.item, count), Item(Firemaking.TINDERBOX)),
                withFood = bot.combatLevel >= PHRIN_COMBAT_LEVEL)) {
            return false
        }
        if (!walkNear(PYRE_APPROACH)) return false
        var burned = false
        repeat(count) {
            if (!cremate(remains, logs)) return burned
            burned = true
        }
        return burned
    }

    private suspend fun makePyreLogs(logs: PyreLogs, count: Int) {
        val sacred = FlamtaerTemple.sacredOils.take(3).firstOrNull { bot.totalItemAmount(it) >= count }
        val oil = sacred ?: FlamtaerTemple.sacredOils[1]
        val missing = (count - bot.totalItemAmount(oil)).coerceAtLeast(0)
        if (!handler.banking.travelToBankDepositAll()) return
        if (missing > 0 && !blessOil(missing)) return
        if (!bankLoad(listOf(Item(logs.wood, count), Item(oil, count)))) return
        repeat(count) {
            val before = carried(logs.item)
            if (!handler.inventory.useItem(oil).onItem(logs.wood) ||
                !waitFor(5.seconds) { carried(logs.item) > before }) return
        }
    }

    private suspend fun blessOil(count: Int): Boolean {
        if (bot.crafting.level < FlamtaerTemple.CRAFTING_LEVEL) return false
        val repairs = maxOf(FlamtaerTemple.repairsRemaining(world), 3)
        val supplies = mapOf(FlamtaerTemple.HAMMER to 1, Firemaking.TINDERBOX to 1,
            FlamtaerTemple.PLANK to repairs, FlamtaerTemple.LIMESTONE_BRICK to repairs,
            ShadeRewards.SWAMP_PASTE to FlamtaerTemple.PASTE_PER_REPAIR * repairs, OLIVE_OIL to count)
        if (!handler.shop.obtainWantedItems(supplies).containsAll(supplies.keys) ||
            !handler.widgets.clickCloseInterface() || !walkNear(TEMPLE_APPROACH)) return false
        val neededSanctity = 11 + 3 * (count - 1)
        repeat(repairs) {
            if (FlamtaerTemple.repairsRemaining(world) == 0 && FlamtaerTemple.sanctity(bot) >= neededSanctity) {
                return@repeat
            }
            val wall = world.locator.findNearestObject(bot, 7) { it.id in FlamtaerTemple.walls } ?: return false
            val before = carried(FlamtaerTemple.PLANK)
            if (!handler.interactions.interact(1, wall) ||
                !waitFor(5.seconds) { carried(FlamtaerTemple.PLANK) < before }) return false
        }
        val altar = findObject(FlamtaerTemple.ALTAR_POSITION, FlamtaerTemple.UNLIT_ALTAR)
        if (altar != null && (!handler.interactions.interact(1, altar) ||
                !waitFor(5.seconds) { findObject(FlamtaerTemple.ALTAR_POSITION, FlamtaerTemple.FLAME) != null })) {
            return false
        }
        repeat(count) {
            val flame = findObject(FlamtaerTemple.ALTAR_POSITION, FlamtaerTemple.FLAME) ?: return false
            val before = carried(OLIVE_OIL)
            if (!handler.inventory.useItem(OLIVE_OIL).onObject(flame) ||
                !waitFor(5.seconds) { carried(OLIVE_OIL) < before }) return false
        }
        return true
    }

    private suspend fun cremate(remains: Int, logs: PyreLogs): Boolean {
        val pyre = world.locator.findNearestObject(bot, 12) { it.id == ShadePyres.EMPTY } ?: return false
        if (!handler.inventory.useItem(logs.item).onObject(pyre) ||
            !waitFor(8.seconds) { findObject(pyre.position, logs.loadedObject) != null }) return false
        val loaded = findObject(pyre.position, logs.loadedObject) ?: return false
        if (!handler.inventory.useItem(remains).onObject(loaded) ||
            !waitFor(8.seconds) { findObject(pyre.position, logs.readyObject) != null }) return false
        val ready = findObject(pyre.position, logs.readyObject) ?: return false
        val before = carried(remains)
        return handler.interactions.interact(1, ready) && waitFor(8.seconds) { carried(remains) < before }
    }

    private suspend fun gatherPhrin(): Boolean {
        if (ShadeRewards.keys.none { carried(it) > 0 }) {
            val key = ShadeRewards.keys.firstOrNull { bot.totalItemAmount(it) > 0 } ?: return false
            if (!bankLoad(listOf(Item(key)), withFood = true)) return false
        }
        if (!enterCatacombs()) return false
        val limit = minOf(deadline, System.currentTimeMillis() + 2.minutes.inWholeMilliseconds)
        while (carried(ShadeRewards.PHRIN_REMAINS) < BATCH && System.currentTimeMillis() < limit) {
            val loot = world.locator.findNearestItem(bot, 12) {
                it.id == ShadeRewards.PHRIN_REMAINS && handler.loot.canPickup(it)
            }
            if (loot != null && handler.loot.pickup(loot)) continue
            if (!fightPhrin(limit)) break
        }
        return exitCatacombs()
    }

    private suspend fun fightPhrin(limit: Long): Boolean {
        val shade = world.locator.findNearestNpc(bot, 15) {
            it.baseId == PHRIN_SHADOW && it.isAlive && it.combat.isAttackable &&
                (it.combat.target == null || it.combat.target === bot)
        } ?: return false
        if (!handler.interactions.interact(3, shade)) return false
        val fightDeadline = minOf(limit, System.currentTimeMillis() + 45.seconds.inWholeMilliseconds)
        while (shade.isAlive && System.currentTimeMillis() < fightDeadline) {
            if (bot.health < maxOf(12, bot.hitpoints.staticLevel / 2) && !handler.inventory.eatAnyFood()) return false
            if (!bot.isAlive || !inCatacombs()) return false
            delay(1.seconds)
        }
        return !shade.isAlive
    }

    private suspend fun openChests(): Boolean {
        val keys = chestKeys(::carried).takeIf { it.any { key -> key in ShadeRewards.steelKeys } }
            ?: chestKeys(bot::totalItemAmount)
        if (keys.isEmpty()) return false
        val supplies = keys.groupingBy { it }.eachCount().map { (id, quantity) -> Item(id, quantity) }
        if (!bot.inventory.containsAll(supplies) && !bankLoad(supplies, withFood = true)) return false
        if (!enterCatacombs()) return false
        var opened = false
        for (key in keys.sorted()) {
            val id = ShadeRewards.chestForKey(key)
            val chest = world.locator.findNearestObject(bot, 30) { it.id == id } ?: break
            val before = carried(key)
            if (!handler.inventory.useItem(key).onObject(chest) ||
                !waitFor(8.seconds) { carried(key) < before }) break
            opened = true
        }
        return exitCatacombs() && opened
    }

    private fun chestKeys(count: (Int) -> Int): List<Int> = buildList {
        for (key in ShadeRewards.steelKeys) repeat(minOf(count(key), BATCH - size)) { add(key) }
        val reserve = ShadeRewards.bronzeKeys.firstOrNull { count(it) > 0 }
        for (key in ShadeRewards.bronzeKeys) {
            val available = count(key) - if (key == reserve) 1 else 0
            repeat(minOf(available, BATCH - size)) { add(key) }
        }
    }

    private suspend fun bankLoad(items: List<Item>, withFood: Boolean = false): Boolean {
        val banking = handler.banking
        if (!banking.travelToBankDepositAll()) return false
        banking.clickBankingMode(false)
        for (item in items) {
            if (!banking.withdrawUnnoted(item) || carried(item.id) < item.amount) return false
        }
        if (withFood) banking.withdrawAnyFood(6, retry = false)
        return handler.widgets.clickCloseInterface()
    }

    private fun carried(id: Int): Int = bot.inventory.computeAmountForId(id)

    private fun inCatacombs(): Boolean = bot.position.y in 9660..9738 && bot.position.x in 3450..3530

    private suspend fun enterCatacombs(): Boolean {
        if (inCatacombs()) return true
        if (!walkNear(TOMB_DOOR.translate(0, 2))) return false
        val door = findObject(TOMB_DOOR, 4132) ?: return false
        return handler.interactions.interact(1, door) && waitFor(12.seconds) { inCatacombs() }
    }

    private suspend fun exitCatacombs(): Boolean {
        if (!inCatacombs()) return true
        val door = findObject(EXIT, 4106) ?: return false
        return handler.interactions.interact(1, door) && waitFor(12.seconds) { !inCatacombs() }
    }

    private suspend fun walkNear(position: Position): Boolean =
        handler.webWalkTo(position, radius = 4, bank = BankAccess.NONE, fallback = false)

    private fun findObject(position: Position, id: Int): GameObject? =
        world.locator.findObjectsOnTile(position) { it.id == id && it.state == EntityState.ACTIVE }.firstOrNull()
}
