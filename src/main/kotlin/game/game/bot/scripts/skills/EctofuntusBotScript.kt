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
    private var failures: Int = 0
) : BotScript(bot) {
    companion object {
        private var Bot.ectoRetryAt by Attr.long().persist("bot_ectofuntus_retry_at")

        private fun owned(bot: Bot, id: Int): Long =
            bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)

        private fun wearsAmulet(bot: Bot): Boolean = Ectofuntus.GHOSTSPEAK_IDS.any { bot.equipment.contains(it) }

        private fun amulet(bot: Bot): Int? = Ectofuntus.GHOSTSPEAK_IDS.firstOrNull { owned(bot, it) > 0 }

        private fun permitted(bot: Bot): Boolean = bot.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
            BotCombatActionHandler.mayGainExperience(bot, SKILL_PRAYER) &&
            (!Ectofuntus.requiresGhostspeak || wearsAmulet(bot) || amulet(bot) != null)

        /**
         * Matches reusable empty containers to at most eight owned worship inputs, using an unfinished load first.
         * Existing bonemeal needs no empty pot and existing slime needs no empty bucket.
         */
        internal fun containerTargets(bot: Bot, bone: EctofuntusBone, limit: Int = 8): Map<Int, Int> {
            val loaded = Ectofuntus.loadedBone(bot)
            if (loaded != null && loaded != bone) return emptyMap()
            val loadedCount = if (loaded == bone) 1L else 0L
            val meals = owned(bot, bone.bonemealId)
            val batches = minOf(limit.coerceIn(1, 8).toLong(), loadedCount + meals + owned(bot, bone.boneId))
            val loadedUsed = minOf(batches, loadedCount)
            val mealUsed = minOf(batches - loadedUsed, meals)
            return buildMap {
                val pots = batches - mealUsed
                val buckets = (batches - owned(bot, Ectofuntus.SLIME)).coerceAtLeast(0)
                if (pots > 0) put(Ectofuntus.POT, pots.toInt())
                if (buckets > 0) put(Ectofuntus.BUCKET, buckets.toInt())
            }
        }

        /**
         * Plans at most eight worships from real unnoted stock, reserving room for tokens and an exchanged amulet.
         * A loaded bone has first claim on a pot; filled bonemeal and slime are used before fresh ingredients.
         */
        internal fun supplies(bot: Bot, bone: EctofuntusBone): List<Item>? {
            val loaded = Ectofuntus.loadedBone(bot)
            if (loaded != null && loaded != bone) return null
            val pots = owned(bot, Ectofuntus.POT)
            if (loaded != null && pots == 0L) return null
            val loadedCount = if (loaded == bone && pots > 0) 1L else 0L
            val meals = owned(bot, bone.bonemealId)
            val raw = minOf(owned(bot, bone.boneId), pots - loadedCount)
            val slime = owned(bot, Ectofuntus.SLIME)
            val batches = minOf(8L, meals + raw + loadedCount, slime + owned(bot, Ectofuntus.BUCKET))
            if (batches == 0L && (loaded != null || Ectofuntus.pendingTokens(bot) == 0)) return null
            val loadedUsed = minOf(batches, loadedCount)
            val mealsUsed = minOf(batches - loadedUsed, meals)
            val rawUsed = batches - loadedUsed - mealsUsed
            val slimeUsed = minOf(batches, slime)
            val plan = mutableListOf<Item>()
            fun add(id: Int, amount: Long) {
                if (amount > 0) plan += Item(id, amount.toInt())
            }
            add(bone.boneId, rawUsed)
            add(bone.bonemealId, mealsUsed)
            add(Ectofuntus.POT, rawUsed + loadedUsed)
            add(Ectofuntus.SLIME, slimeUsed)
            add(Ectofuntus.BUCKET, batches - slimeUsed)
            if (Ectofuntus.requiresGhostspeak && !wearsAmulet(bot)) {
                plan += Item(amulet(bot) ?: return null)
            }
            return plan
        }

        /**
         * Prefers the strongest owned bone or bonemeal recipe, retaining an unfinished load for recovery.
         * Missing empty containers are prepared during startup. A failed trip leaves ordinary burial available
         * during the retry cooldown, and pending earned tokens can be collected without buying supplies.
         */
        fun select(bot: Bot, duration: Duration): EctofuntusBotScript? {
            if (!permitted(bot) || bot.ectoRetryAt > System.currentTimeMillis()) return null
            val loaded = Ectofuntus.loadedBone(bot)
            val selected = if (loaded != null) loaded
            else {
                val recipes = EctofuntusBone.entries.sortedByDescending { it.baseXp }
                recipes.firstOrNull { owned(bot, it.boneId) > 0 || owned(bot, it.bonemealId) > 0 }
                    ?: recipes.firstOrNull { Ectofuntus.pendingTokens(bot) > 0 }
            }
            return selected?.let {
                EctofuntusBotScript(bot, it,
                    System.currentTimeMillis() + duration.inWholeMilliseconds.coerceIn(300_000L, 1_200_000L))
            }
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

        override fun load(data: JsonObject) {
            recipe = data.get("recipe").asString
            deadline = data.get("deadline").asLong
            prepared = data.get("prepared")?.asBoolean ?: false
            failures = data.get("failures")?.asInt ?: 0
        }

        override fun save(data: JsonObject) {
            data.addProperty("recipe", recipe)
            data.addProperty("deadline", deadline)
            data.addProperty("prepared", prepared)
            data.addProperty("failures", failures)
        }
    }

    constructor(bot: Bot, data: EctofuntusData) :
        this(bot, EctofuntusBone.valueOf(data.recipe), data.deadline, data.prepared, data.failures)

    private var lastProgress = System.currentTimeMillis()
    private var previous: List<Any?>? = null
    private var poolAttempt = 0
    override fun allowsLoot(item: GroundItem): Boolean = false

    private fun valid(): Boolean = System.currentTimeMillis() < deadline && permitted(bot)

    private fun count(id: Int): Int = bot.inventory.computeAmountForId(id)

    private fun workRemaining(): Boolean = Ectofuntus.loadedBone(bot) != null ||
        count(bone.boneId) > 0 && count(Ectofuntus.POT) > 0 ||
        count(bone.bonemealId) > 0 && (count(Ectofuntus.SLIME) > 0 || count(Ectofuntus.BUCKET) > 0)

    /**
     * Secures one usable worship before optional batch preparation, so a small wallet cannot buy only pots.
     * Both phases share the native shop deadline and the visit's saved deadline. Actual partial stock is replanned
     * afterwards; empty containers are never confused with replacement bones or fabricated bonemeal and slime.
     */
    internal suspend fun prepareSupplies(): Boolean {
        if (!valid()) return false
        fun readyBatch(): Boolean {
            val plan = supplies(bot, bone) ?: return false
            return Ectofuntus.loadedBone(bot) == bone || plan.any {
                it.id == bone.boneId || it.id == bone.bonemealId
            }
        }
        if (readyBatch() || containerTargets(bot, bone).isEmpty()) return supplies(bot, bone) != null
        val budget = minOf(120_000L, deadline - System.currentTimeMillis())
        if (budget <= 0) return false
        withTimeoutOrNull(budget) {
            for (limit in listOf(1, 8)) {
                if (!valid()) return@withTimeoutOrNull
                val targets = containerTargets(bot, bone, limit).filter { (id, amount) -> owned(bot, id) < amount }
                if (targets.isNotEmpty()) {
                    targets.forEach { (id, amount) -> bot.preferences.raiseWantedItemTarget(id, amount) }
                    handler.shop.obtainWantedItems(targets)
                }
                if (limit == 1 && !readyBatch()) return@withTimeoutOrNull
            }
        }
        return valid() && supplies(bot, bone) != null
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
                if (!prepareSupplies()) return@withTimeoutOrNull true
                val plan = supplies(bot, bone) ?: return@withTimeoutOrNull true
                if ((!bot.inventory.containsAll(plan) || bot.inventory.isFull) && !bank(plan))
                    return@withTimeoutOrNull true
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
        if (loaded != null) {
            if (loaded != bone || count(Ectofuntus.POT) == 0) return false
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
     * The shared pool centre is a search anchor, never an assumed walkable destination.
     */
    private suspend fun fillBucket(): Boolean {
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
        if (failures > 0 || System.currentTimeMillis() >= deadline)
            bot.ectoRetryAt = System.currentTimeMillis() + 300_000
    }

    override fun snapshot() = EctofuntusData().also {
        it.recipe = bone.name
        it.deadline = deadline
        it.prepared = prepared
        it.failures = failures
    }
}
