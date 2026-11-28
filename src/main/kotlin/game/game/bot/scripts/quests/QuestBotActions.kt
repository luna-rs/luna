package game.bot.scripts.quests

import api.bot.action.BotNpcDialogues
import api.bot.boss.BossFood
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import com.google.gson.JsonObject
import game.content.merlinsCrystal.MerlinVisitor
import io.luna.game.model.EntityState
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Native navigation, dialogue, combat and pickup actions shared by the quest scripts.
 *
 * @author lare96
 */
internal class QuestBotActions(private val bot: Bot) {
    private val handler = bot.actionHandler
    private val food = BossFood.Policy(minimumBiteHealing = 5, requiredHealing = 24)

    fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive

    fun has(id: Int, count: Int = 1): Boolean = bot.inventory.computeAmountForId(id) >= count

    fun npc(id: Int, at: Position): Npc? =
        bot.world.locator.findNpcs(at, 10) { it.id == id && it.z == at.z && it.state == EntityState.ACTIVE &&
            it.isAlive && (it !is MerlinVisitor || it.owner === bot) }.firstOrNull()

    fun objectAt(id: Int, at: Position): GameObject? = bot.world.locator.findObjectsOnTile(at) {
        it.id == id && it.state == EntityState.ACTIVE && it.view.isViewableFor(bot)
    }.firstOrNull()

    suspend fun walk(at: Position, radius: Int = 0): Boolean =
        live() && handler.webWalkTo(at, radius, attempts = 2, bank = BankAccess.NONE, fallback = false)

    suspend fun talk(id: Int, at: Position, vararg options: String, portrait: Int = id, done: () -> Boolean): Boolean {
        repeat(2) {
            if (done()) return true
            val speaker = npc(id, at) ?: return false
            if (approach(speaker) && (BotNpcDialogues.respond(bot, speaker, options.toList(), timeout = 15.seconds,
                    completed = done, allowed = ::live, advanceSpeech = true, speakerId = portrait) {
                    bot.output.sendNpcInteraction(1, speaker)
                } || await(4_000L, done))) return true
        }
        return false
    }

    suspend fun click(id: Int, at: Position, option: Int = 1): Boolean {
        val target = objectAt(id, at) ?: return false
        return approach(target) && bot.output.sendObjectInteraction(option, target)
    }

    suspend fun clickNpc(npc: Npc, option: Int, policy: InteractionPolicy): Boolean =
        approach(npc, policy) && bot.output.sendNpcInteraction(option, npc)

    suspend fun fight(id: Int, at: Position, done: () -> Boolean): Boolean {
        if (done()) return true
        val target = npc(id, at) ?: return false
        if (!approach(target) || !bot.output.sendNpcInteraction(3, target)) return false
        return withTimeoutOrNull(90_000) {
            while (live()) {
                if (done() || !target.isAlive || target.state != EntityState.ACTIVE) return@withTimeoutOrNull true
                if (bot.healthPercent < 45 && !BossFood.eat(bot, food)) return@withTimeoutOrNull false
                delay(250)
            }
            false
        } == true
    }

    private suspend fun approach(target: Entity, policy: InteractionPolicy = InteractionPolicy.STANDARD_SIZE): Boolean {
        if (handler.interactions.approach(target, policy, timeout = 300.seconds, allowOutOfView = true,
                canContinue = ::live)) return true
        if (!live() || bot.z != target.position.z || !bot.position.isWithinDistance(target.position, 64)) return false
        return withTimeoutOrNull(40_000L) {
            handler.interactions.approachLocally(target, policy)
        } == true
    }

    suspend fun loot(id: Int, at: Position, count: Int = 1, wait: Long = 0L): Boolean {
        val before = bot.inventory.computeAmountForId(id)
        fun find(): GroundItem? = bot.world.locator.findItems(at, 12) {
            it.id == id && it.position.z == at.z && it.state == EntityState.ACTIVE && it.view.isViewableFor(bot)
        }.firstOrNull()
        var ground = find()
        if (ground == null && wait > 0) await(wait) { find().also { ground = it } != null }
        val item = ground ?: return false
        return handler.loot.pickup(item, ::live) && await { has(id, before + count) }
    }

    suspend fun await(timeout: Long = 12_000L, done: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live()) {
            if (done()) return@withTimeoutOrNull true
            delay(150)
        }
        false
    } == true
}

/**
 * Persists a quest attempt's time limit across native bot-script saves.
 *
 * @author lare96
 */
open class QuestAttemptData(attemptMillis: Long = 30 * 60_000L) : BotScriptData() {
    var deadline = System.currentTimeMillis() + attemptMillis

    override fun load(data: JsonObject) {
        deadline = data.get("deadline").asLong
    }

    override fun save(data: JsonObject) {
        data.addProperty("deadline", deadline)
    }
}
