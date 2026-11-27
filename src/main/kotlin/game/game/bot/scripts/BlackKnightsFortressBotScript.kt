package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import game.content.blackKnightsFortress.BlackKnightsFortress as Quest
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Completes Sir Amik's assignment through the same dialogue, equipment and object interactions as players.
 * Resumes at the objective recorded in the quest journal.
 *
 * @author lare96
 */
class BlackKnightsFortressBotScript(bot: Bot, private val data: Data = Data()) : BotScript(bot) {

    companion object {
        private const val ATTEMPT_MS = 10 * 60_000L
        private const val RETRY_MS = 30 * 60_000L
        private val APPROACH_TIMEOUT = 180.seconds
        private var Bot.fortressRetryAt by Attr.long().persist("black_knights_fortress_bot_retry_at")
        private val disguise = listOf(Quest.BRONZE_MED_HELM, Quest.IRON_CHAINBODY)

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            return stage < Quest.COMPLETE && (stage != 0 || Quest.canStart(bot)) &&
                bot.isAlive && !bot.combat.inCombat() && !bot.overlays.hasWindow() &&
                bot.controllers.primary.javaClass == PlayerController::class.java &&
                System.currentTimeMillis() >= bot.fortressRetryAt &&
                requiredItems(stage).all(bot.actionHandler::has)
        }

        private fun requiredItems(stage: Int): List<Int> = when (stage) {
            0, Quest.ASSIGNED -> disguise + Quest.CABBAGE
            Quest.OVERHEARD -> listOf(Quest.CABBAGE)
            else -> emptyList()
        }
    }

    /**
     * Persists the current attempt's deadline when the bot logs out or the script is suspended.
     *
     * @author lare96
     */
    class Data : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS
            private set

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    override val actionSafety = ActionSafety.COMBAT
    var failure: String? = null
        private set

    override fun snapshot(): Data = data

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        var objective = "prepare supplies"
        val remaining = data.deadline - System.currentTimeMillis()
        val finished = remaining > 0 && withTimeoutOrNull(remaining) {
            if (stage() == 0 && !Quest.canStart(bot)) {
                objective = "meet Sir Amik's quest point requirement"
                return@withTimeoutOrNull false
            }
            if (!prepare()) return@withTimeoutOrNull false
            while (live() && stage() < Quest.COMPLETE) {
                val before = stage()
                objective = when (before) {
                    0 -> "accept Sir Amik's assignment"
                    Quest.ASSIGNED -> "wear the disguise and listen at the grill"
                    Quest.OVERHEARD -> "use a cabbage on the hole above the cauldron"
                    else -> "report to Sir Amik"
                }
                bot.log("Black Knights' Fortress: $objective.")
                repeat(3) {
                    if (!live() || stage() != before) return@repeat
                    advance(before)
                    if (stage() == before) delay(600)
                }
                if (stage() == before) return@withTimeoutOrNull false
            }
            stage() == Quest.COMPLETE
        } == true
        if (!finished) {
            failure = "Could not $objective at ${bot.position} (stage ${stage()})."
            bot.log("Black Knights' Fortress: $failure")
            bot.fortressRetryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private suspend fun prepare(): Boolean {
        val missing = requiredItems(stage()).filterNot { bot.inventory.contains(it) || bot.equipment.contains(it) }
            .map { Item(it) }
        if (missing.isEmpty()) return true
        if (!bot.inventory.hasSpaceForAll(missing)) return false
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!handler.interactions.interact(if (bank is Npc) 2 else 1, bank, ::live) ||
            !await(12_000) { bot.bank.isOpen }) return false
        return try {
            handler.banking.withdrawAll(missing)
        } finally {
            bot.overlays.closeWindows()
        }
    }

    private suspend fun advance(before: Int): Boolean = when (before) {
        0 -> talk(starting = true)
        Quest.ASSIGNED -> spy()
        Quest.OVERHEARD -> sabotage()
        Quest.SABOTAGED -> talk(starting = false)
        else -> false
    }

    private suspend fun talk(starting: Boolean): Boolean {
        val npc = bot.world.locator.findNpcs(Quest.sirAmikPosition, 8) {
            it.id == Quest.SIR_AMIK && it.isAlive && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return false
        val reach = handler.interactions.interactionPolicy(1, npc)
        if (!handler.interactions.approach(npc, reach, timeout = APPROACH_TIMEOUT, allowOutOfView = true,
                canContinue = ::live)) return false
        if (starting) {
            return BotNpcDialogues.respond(bot, npc, listOf(Quest.SEEK_QUEST, Quest.LAUGH_AT_DANGER),
                allowed = ::live, completed = { stage() >= Quest.ASSIGNED },
                playerSpeech = setOf("All right, I'll give it a try."), advanceSpeech = true) {
                handler.interactions.interactLocally(1, npc, allowed = ::live)
            }
        }
        return handler.interactions.interactLocally(1, npc, allowed = ::live) &&
            await(24_000) { stage() == Quest.COMPLETE }
    }

    private suspend fun spy(): Boolean {
        for (piece in disguise) {
            if (!bot.equipment.contains(piece) && !handler.equipment.equip(piece)) return false
        }
        val grill = objectAt(Quest.GRILL, Quest.grillPosition) ?: return false
        if (!approachObject(grill)) return false
        return handler.interactions.interactLocally(1, grill, allowed = ::live) &&
            await(30_000) { stage() >= Quest.OVERHEARD }
    }

    private suspend fun sabotage(): Boolean {
        val hole = objectAt(Quest.HOLE, Quest.holePosition) ?: return false
        if (!approachObject(hole)) return false
        return handler.inventory.useItem(Quest.CABBAGE).onObject(hole, ::live) &&
            await(6_000) { stage() == Quest.SABOTAGED }
    }

    private suspend fun approachObject(obj: GameObject): Boolean =
        handler.interactions.approach(obj, timeout = APPROACH_TIMEOUT, canContinue = ::live)

    private fun objectAt(id: Int, position: Position): GameObject? =
        bot.world.locator.findObjectsOnTile(position) { it.id == id && it.state == EntityState.ACTIVE }.firstOrNull()

    private fun stage() = Quest.stage(bot)

    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive

    private suspend fun await(timeout: Long, done: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live() && !done()) delay(150)
        done()
    } == true
}
