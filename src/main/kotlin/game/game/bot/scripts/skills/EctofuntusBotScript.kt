package game.bot.scripts.skills

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.ectofuntus.Ectofuntus
import game.content.ectofuntus.EctofuntusBone
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Processes one small owned Ectofuntus batch through the manual revision 377 player interactions.
 * Prayer trainees load, wind and empty the grinder, fill buckets at the native pool,
 * worship and claim tokens. Existing bonemeal, slime and a player's unfinished grinder load are preserved.
 *
 * Normal banking and the existing stairs, trapdoor and cavern web routes own all travel. Missing empty containers
 * are obtained through ordinary shops before the owned batch is packed. Any required ghostspeak amulet must
 * already belong to the bot. No materials, experience, tokens or saved stages are injected.
 * A saved deadline, consecutive failure limit and retry cooldown bound failed trips without recovery teleports.
 *
 * @author lare96
 */
class EctofuntusBotScript(
    bot: Bot,
    val bone: EctofuntusBone,
    private val deadline: Long,
    private var prepared: Boolean = false,
    private var failures: Int = 0,
    private var maxWorships: Int = PrayerOuting.MAX_WORSHIPS,
    private var recoverLoad: Boolean = true,
    private val preferWorship: Boolean = false,
    private val remainingLoads: Int = 1
) : BotScript(bot) {
    companion object {
        private var Bot.ectoRetryAt by Attr.long().persist("bot_ectofuntus_retry_at")
        private val poolLanding = Position(3683, 9887, 0)

        private fun owned(bot: Bot, id: Int): Long =
            bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)

        private fun wearsAmulet(bot: Bot): Boolean = Ectofuntus.GHOSTSPEAK_IDS.any { bot.equipment.contains(it) }

        private fun amulet(bot: Bot): Int? = Ectofuntus.GHOSTSPEAK_IDS.firstOrNull { owned(bot, it) > 0 }

        private fun permitted(bot: Bot): Boolean = bot.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
            BotCombatActionHandler.mayGainExperience(bot, SKILL_PRAYER) &&
            (!Ectofuntus.requiresGhostspeak || wearsAmulet(bot) || amulet(bot) != null)

        /**
         * Selects an economic real batch or recovers already committed native work.
         */
        fun select(bot: Bot, duration: Duration, recipe: EctofuntusBone? = null,
                   preferWorship: Boolean = false): EctofuntusBotScript? {
            val budget = duration.inWholeMilliseconds.coerceIn(300_000L, 1_200_000L)
            return selectVisit(bot, System.currentTimeMillis() + budget, recipe, preferWorship,
                if (preferWorship) 3 else 1)
        }

        internal fun recover(bot: Bot, duration: Duration): EctofuntusBotScript? {
            if (Ectofuntus.loadedBone(bot) == null && Ectofuntus.pendingTokens(bot) == 0) return null
            val budget = duration.inWholeMilliseconds.coerceIn(300_000L, 1_200_000L)
            return selectVisit(bot, System.currentTimeMillis() + budget, null, true, 1, recoveryOnly = true)
        }

        private fun selectVisit(bot: Bot, deadline: Long, recipe: EctofuntusBone?, preferWorship: Boolean,
                                loads: Int, recoveryOnly: Boolean = false): EctofuntusBotScript? {
            val now = System.currentTimeMillis()
            if (!permitted(bot) || bot.ectoRetryAt > now || deadline <= now) return null
            val batch = PrayerOuting.select(bot, (deadline - now) / 600, onlyRecipe = recipe,
                preferWorship = preferWorship, recoveryOnly = recoveryOnly) ?: return null
            return EctofuntusBotScript(bot, batch.recipe, deadline, maxWorships = batch.worships,
                recoverLoad = batch.loaded > 0, preferWorship = preferWorship, remainingLoads = loads)
        }

        /**
         * Returns the minimum carried raw stock needed by the next worthwhile batch. Combat may occur before
         * that batch can safely depart, so only the current non-combat safety check is deferred.
         */
        fun reservedBones(bot: Bot): Item? {
            if (bot.ectoRetryAt > System.currentTimeMillis() || bot.inWilderness() ||
                !bot.inventory.any { it != null && EctofuntusBone.forBone(it.id) != null }) return null
            val batch = PrayerOuting.select(bot, 500, shop = false, recover = false) ?: return null
            val needed = batch.raw - bot.bank.computeAmountForId(batch.recipe.boneId)
            return if (needed > 0) Item(batch.recipe.boneId, needed) else null
        }
    }

    /**
     * Retains the chosen recipe and bounded visit state while native attributes own all production progress.
     *
     * @author lare96
     */
    class EctofuntusData : BotScriptData() {
        var recipe = EctofuntusBone.entries.first().name
        var deadline = 0L
        var prepared = false
        var failures = 0
        var maxWorships = PrayerOuting.MAX_WORSHIPS
        var recoverLoad = true
        var preferWorship = false
        var remainingLoads = 1

        override fun load(data: JsonObject) {
            recipe = data.get("recipe").asString
            deadline = data.get("deadline").asLong
            prepared = data.get("prepared")?.asBoolean ?: false
            failures = data.get("failures")?.asInt ?: 0
            maxWorships = data.get("max_worships")?.asInt?.coerceIn(0, PrayerOuting.MAX_WORSHIPS)
                ?: PrayerOuting.MAX_WORSHIPS
            recoverLoad = data.get("recover_load")?.asBoolean ?: true
            preferWorship = data.get("prefer_worship")?.asBoolean ?: false
            remainingLoads = data.get("remaining_loads")?.asInt?.coerceIn(1, 3) ?: 1
        }

        override fun save(data: JsonObject) {
            data.addProperty("recipe", recipe)
            data.addProperty("deadline", deadline)
            data.addProperty("prepared", prepared)
            data.addProperty("failures", failures)
            data.addProperty("max_worships", maxWorships)
            data.addProperty("recover_load", recoverLoad)
            data.addProperty("prefer_worship", preferWorship)
            data.addProperty("remaining_loads", remainingLoads)
        }
    }

    constructor(bot: Bot, data: EctofuntusData) :
        this(bot, EctofuntusBone.valueOf(data.recipe), data.deadline, data.prepared, data.failures,
            data.maxWorships, data.recoverLoad, data.preferWorship, data.remainingLoads)

    private var lastProgress = System.currentTimeMillis()
    private var previous: List<Any?>? = null
    private var poolAttempt = 0
    override fun allowsLoot(item: GroundItem): Boolean = false

    private fun valid(): Boolean = System.currentTimeMillis() < deadline && permitted(bot)

    private fun count(id: Int): Int = bot.inventory.computeAmountForId(id)

    private fun workRemaining(): Boolean {
        if (recoverLoad && Ectofuntus.loadedBone(bot) == bone && count(Ectofuntus.POT) > 0) return true
        if (maxWorships == 0) return false
        val meals = count(bone.bonemealId)
        val slime = count(Ectofuntus.SLIME) + count(Ectofuntus.BUCKET)
        return slime > 0 && (meals > 0 || count(bone.boneId) > 0 && count(Ectofuntus.POT) > 0)
    }

    /**
     * Requotes empty containers before buying and rechecks the useful batch against the supplies actually secured.
     * Existing grinder work and earned token credit can still be recovered when a supplier runs out.
     */
    private suspend fun prepareSupplies(): PrayerOuting.Batch? {
        fun plan(shop: Boolean) = PrayerOuting.select(bot, (deadline - System.currentTimeMillis()) / 600,
            maxWorships = maxWorships, shop = shop, onlyRecipe = bone, preferWorship = preferWorship)
        val batch = plan(true) ?: return null
        if (batch.purchases.isEmpty()) return batch
        val targets = batch.supplies.filter { it.id in batch.purchases }.associate { it.id to it.amount }
        val budget = minOf(120_000L, deadline - System.currentTimeMillis())
        if (budget <= 0) return null
        withTimeoutOrNull(budget) {
            handler.shop.obtainWantedItems(targets, batch.purchaseCoins.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                batch.purchaseCeilings)
        }
        return if (valid()) plan(false) else null
    }

    override suspend fun awaitActionSafety(safety: ActionSafety): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0L) return false
        return withTimeoutOrNull(remaining) { super.awaitActionSafety(safety) } == true
    }

    override suspend fun init(resumed: Boolean): Boolean {
        val stopped = withTimeoutOrNull(180_000) {
            if (!valid() || failures >= 4) return@withTimeoutOrNull true
            if (!prepared) {
                val batch = prepareSupplies() ?: return@withTimeoutOrNull true
                val bankNeeded = PrayerOuting.needsBank(bot, bone, batch.supplies, batch.raw, batch.meals,
                    batch.worships > 0)
                if (bankNeeded && !bank(batch.supplies)) return@withTimeoutOrNull true
                maxWorships = batch.worships
                recoverLoad = batch.loaded > 0 || maxWorships > 0
                prepared = true
            }
            if (Ectofuntus.requiresGhostspeak && !wearsAmulet(bot)) {
                val id = Ectofuntus.GHOSTSPEAK_IDS.firstOrNull { bot.inventory.contains(it) }
                    ?: return@withTimeoutOrNull true
                if (!handler.equipment.equip(id)) return@withTimeoutOrNull true
            }
            !valid()
        } ?: true
        if (stopped) failures++
        return stopped
    }

    private fun observe() {
        val state = listOf(bot.position, Ectofuntus.loadedBone(bot), Ectofuntus.isGround(bot),
            Ectofuntus.pendingTokens(bot), count(bone.boneId), count(bone.bonemealId),
            count(Ectofuntus.POT), count(Ectofuntus.BUCKET), count(Ectofuntus.SLIME))
        if (state != previous) {
            previous = state
            lastProgress = System.currentTimeMillis()
        }
    }

    override suspend fun run(): Boolean {
        if (!valid() || failures >= 4) return true
        observe()
        if (System.currentTimeMillis() - lastProgress >= 120_000) {
            failures++
            return true
        }
        if (!workRemaining() && Ectofuntus.pendingTokens(bot) == 0) return true
        val success = withTimeoutOrNull(150_000) { cycle() } == true
        failures = if (success) 0 else failures + 1
        delay(1.seconds)
        return !workRemaining() && Ectofuntus.pendingTokens(bot) == 0
    }

    private fun find(position: Position, id: Int): GameObject? =
        world.locator.findObjects(position, 0) { it.id == id }.firstOrNull()

    private suspend fun approach(target: Entity): Boolean =
        handler.interactions.approach(target, timeout = 120.seconds, allowOutOfView = true) { valid() }

    private suspend fun click(position: Position, id: Int): Boolean {
        val obj = find(position, id) ?: return false
        return approach(obj) && handler.interactions.interact(1, obj) { valid() }
    }

    private suspend fun cycle(): Boolean {
        val pending = Ectofuntus.pendingTokens(bot)
        if (pending > 0 && (pending + Ectofuntus.TOKEN_REWARD > Ectofuntus.TOKEN_CAP || !workRemaining()))
            return claim()
        val loaded = Ectofuntus.loadedBone(bot)
        if (loaded != null && recoverLoad && count(Ectofuntus.POT) > 0) {
            if (loaded != bone) return false
            return if (!Ectofuntus.isGround(bot)) {
                click(Ectofuntus.GRINDER_POSITION, Ectofuntus.GRINDER) &&
                    waitFor(8.seconds) { Ectofuntus.isGround(bot) }
            } else {
                val before = count(bone.bonemealId)
                click(Ectofuntus.BIN_POSITION, Ectofuntus.BIN) &&
                    waitFor(8.seconds) { Ectofuntus.loadedBone(bot) == null && count(bone.bonemealId) > before }
            }
        }
        if (count(bone.boneId) > 0 && count(Ectofuntus.POT) > 0) {
            val loader = find(Ectofuntus.LOADER_POSITION, Ectofuntus.LOADER) ?: return false
            return approach(loader) && handler.inventory.useItem(bone.boneId).onObject(loader) { valid() } &&
                waitFor(8.seconds) { Ectofuntus.loadedBone(bot) == bone }
        }
        if (count(bone.bonemealId) > count(Ectofuntus.SLIME) && count(Ectofuntus.BUCKET) > 0)
            return fillBucket()
        if (count(bone.bonemealId) > 0 && count(Ectofuntus.SLIME) > 0) {
            val before = count(bone.bonemealId)
            return click(Ectofuntus.FOUNTAIN_POSITION, Ectofuntus.FOUNTAIN) &&
                waitFor(8.seconds) { count(bone.bonemealId) < before }
        }
        return !workRemaining()
    }

    /**
     * Selects actual cache pool objects and lets native collision reach choose a usable standing tile.
     * The bottom stair lands on a known tile, where distance becomes meaningful for pool selection.
     */
    private suspend fun fillBucket(): Boolean {
        if (!bot.position.isWithinDistance(poolLanding, 12) &&
            (!handler.webWalkToAny(listOf(poolLanding)) || !valid())) return false
        val pools = world.locator.findObjects(Ectofuntus.POOL, 12) { it.id in Ectofuntus.POOL_IDS }
            .sortedBy { it.position.computeLongestDistance(bot.position) }
        if (pools.isEmpty()) return false
        val pool = pools[poolAttempt % pools.size]
        val before = count(Ectofuntus.SLIME)
        val success = approach(pool) && handler.inventory.useItem(Ectofuntus.BUCKET).onObject(pool) { valid() } &&
            waitFor(8.seconds) { count(Ectofuntus.SLIME) > before }
        poolAttempt = if (success) 0 else poolAttempt + 1
        return success
    }

    private suspend fun claim(): Boolean {
        if (Ectofuntus.requiresGhostspeak && !wearsAmulet(bot)) return false
        val disciple = world.locator.findNpcs(Ectofuntus.DISCIPLE_POSITION, 12) {
            it.id in Ectofuntus.DISCIPLE_IDS && it.position.z == 0
        }.minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false
        val before = Ectofuntus.pendingTokens(bot)
        val success = approach(disciple) && handler.interactions.interact(1, disciple) { valid() } &&
            waitFor(8.seconds) { Ectofuntus.pendingTokens(bot) < before }
        handler.widgets.clickCloseInterface()
        return success
    }

    /**
     * Consolidates the exact owned batch at a native bank and verifies every unnoted withdrawal.
     */
    private suspend fun bank(plan: List<Item>): Boolean {
        val bank = handler.webWalkToNearestBank(fallback = false) ?: return false
        if (!valid() || !handler.interactions.interact(2, bank) { valid() } ||
            !waitFor(5.seconds) { bot.bank.isOpen } || !handler.banking.depositInventory()) return false
        if (!handler.widgets.clickButton(5292, 5387) ||
            !waitFor(5.seconds) { bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE) == 0 }) return false
        for (item in plan) {
            if (!valid() || !handler.banking.withdraw(item) || !bot.inventory.contains(item)) return false
        }
        return bot.inventory.containsAll(plan) && handler.widgets.clickCloseInterface()
    }

    override suspend fun finish() {
        output.sendCloseInterface()
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun completed() {
        val now = System.currentTimeMillis()
        if (failures > 0 || now >= deadline) bot.ectoRetryAt = now + 300_000
        if (!preferWorship || maxWorships == 0 || !valid() ||
            !BotCombatActionHandler.wantsToTrain(bot, SKILL_PRAYER)) return
        val next = if (failures == 0 && remainingLoads > 1) {
            selectVisit(bot, deadline, bone, true, remainingLoads - 1)
        } else if (failures > 0 && PrayerBoneStock.owned(bot, bone.boneId) > PrayerBoneStock.reserve(bot, bone.boneId)) {
            BuryBoneBotScript(bot, bone.bone, (deadline - now).milliseconds, ownedOuting = true)
        } else null
        if (next != null) {
            next.trainingGoal = trainingGoal
            bot.scriptStack.softPushHead(next)
        }
    }

    override fun snapshot() = EctofuntusData().also {
        it.recipe = bone.name
        it.deadline = deadline
        it.prepared = prepared
        it.failures = failures
        it.maxWorships = maxWorships
        it.recoverLoad = recoverLoad
        it.preferWorship = preferWorship
        it.remainingLoads = remainingLoads
    }
}
