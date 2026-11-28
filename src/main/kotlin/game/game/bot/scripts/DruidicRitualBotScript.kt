package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import game.content.druidicRitual.DruidicDialogue
import game.content.druidicRitual.DruidicRitual as Quest
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Follows the saved Druidic Ritual journal using native conversations, combat, loot and cauldron interactions.
 *
 * @author lare96
 */
class DruidicRitualBotScript(bot: Bot, private val data: Data = Data()) : BotScript(bot) {

    companion object {
        private const val ATTEMPT_MS = 25 * 60_000L
        private const val RETRY_MS = 30 * 60_000L
        private val approachTimeout = 180.seconds
        private val food = BossFood.Policy(minimumBiteHealing = 3, requiredHealing = 0)
        private val sources = mapOf(
            2132 to Source(81, Position(2917, 3289)),
            2134 to Source(2723, Position(2925, 3256)),
            2136 to Source(106, Position(2966, 3466)),
            2138 to Source(41, Position(2853, 3373))
        )
        private var Bot.druidicRetryAt by Attr.long().persist("druidic_ritual_bot_retry_at")

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            if (stage == Quest.COMPLETE || !bot.isAlive || bot.combat.inCombat() || bot.overlays.hasWindow() ||
                bot.controllers.primary.javaClass != PlayerController::class.java ||
                System.currentTimeMillis() < bot.druidicRetryAt) return false
            if (stage == Quest.NOT_STARTED && !bot.position.isWithinDistance(Quest.kaqemeexPosition, 100)) return false
            return stage == Quest.DELIVERED_MEATS || canHunt(bot) ||
                Quest.meats.all { (raw, dipped) -> bot.actionHandler.has(raw) || bot.actionHandler.has(dipped) }
        }

        private fun canHunt(bot: Bot): Boolean =
            !bot.preferences.isSkiller && !bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting &&
                bot.skill(Skill.HITPOINTS).staticLevel >= 20
    }

    /**
     * Native spawn and drop source for one raw meat.
     *
     * @author lare96
     */
    private data class Source(val npc: Int, val position: Position)

    /**
     * Retains the attempt's deadline across logout and script restoration.
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
    override val allowsCombatResponse = false
    var failure: String? = null
        private set

    override fun snapshot(): Data = data

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        var objective = "start the quest"
        suspend fun tryStep(label: String, action: suspend () -> Boolean): Boolean {
            objective = label
            bot.log("Druidic Ritual: $label.")
            repeat(3) {
                if (!live()) return false
                if (action()) return true
                delay(600)
            }
            return false
        }

        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            if (stage() == Quest.NOT_STARTED && !tryStep("speak to Kaqemeex") {
                    talk(Quest.KAQEMEEX, Quest.kaqemeexPosition, Quest.STARTED, DruidicDialogue.SEEK_QUEST, DruidicDialogue.ACCEPT)
                }) return@withTimeoutOrNull false
            if (stage() == Quest.STARTED && !tryStep("ask Sanfew about the ritual") {
                    talk(Quest.SANFEW, Quest.sanfewPosition, Quest.SPOKE_TO_SANFEW, DruidicDialogue.OFFER_HELP)
                }) return@withTimeoutOrNull false
            if (stage() == Quest.SPOKE_TO_SANFEW) {
                if (!tryStep("withdraw any banked meats", ::withdrawMeats)) return@withTimeoutOrNull false
                for ((raw, enchanted) in Quest.meats) {
                    if (!carrying(raw, enchanted) && !tryStep("gather ${Item(raw).itemDef.name.lowercase()}") {
                            hunt(raw)
                        }) return@withTimeoutOrNull false
                }
                if (!tryStep("dip the four meats in the Cauldron of Thunder", ::dipMeats) ||
                    !tryStep("give Sanfew the enchanted meats") {
                        talk(Quest.SANFEW, Quest.sanfewPosition, Quest.DELIVERED_MEATS)
                    }) return@withTimeoutOrNull false
            }
            tryStep("report back to Kaqemeex") { talk(Quest.KAQEMEEX, Quest.kaqemeexPosition, Quest.COMPLETE) }
        } == true
        if (!finished && stage() != Quest.COMPLETE) {
            failure = "Could not $objective at ${bot.position} (stage ${stage()})."
            bot.log("Druidic Ritual: $failure")
            bot.druidicRetryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private suspend fun talk(id: Int, at: Position, next: Int, vararg responses: String): Boolean {
        if (stage() >= next) return true
        val npc = bot.world.locator.findNpcs(at, 10) {
            it.id == id && it.state == EntityState.ACTIVE && it.isAlive
        }.firstOrNull() ?: return false
        if (!handler.interactions.approach(npc, timeout = approachTimeout, allowOutOfView = true,
                canContinue = ::live)) return false
        return BotNpcDialogues.respond(bot, npc, responses.toList(), allowed = ::live, completed = { stage() >= next },
            advanceSpeech = true) {
            handler.interactions.interactLocally(1, npc, allowed = ::live)
        }
    }

    private suspend fun withdrawMeats(): Boolean {
        val missing = Quest.meats.filterNot { (raw, dipped) -> carrying(raw, dipped) }
        if (bot.inventory.computeRemainingSize() < missing.size) return false
        val banked = missing.mapNotNull { (raw, dipped) ->
            when {
                bot.bank.contains(dipped) -> Item(dipped)
                bot.bank.contains(raw) -> Item(raw)
                else -> null
            }
        }
        if (banked.isEmpty()) return true
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!handler.banking.open(bank)) return false
        return try {
            handler.banking.withdrawAll(banked)
        } finally {
            bot.overlays.closeWindows()
        }
    }

    private suspend fun hunt(raw: Int): Boolean {
        if (bot.inventory.contains(raw)) return true
        if (!canHunt(bot) || !handler.combat.prepareTrainingStyle()) return false
        val source = sources.getValue(raw)
        fun drop() = bot.world.locator.findItems(source.position, 14, true) {
            it.id == raw && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
        }.firstOrNull()

        if (drop() == null) {
            val npc = bot.world.locator.findNpcs(source.position, 10, true) {
                it.id == source.npc && it.isAlive && it.state == EntityState.ACTIVE && !it.combat.inCombat()
            }.firstOrNull() ?: return false
            if (!handler.interactions.approach(npc, timeout = approachTimeout, allowOutOfView = true,
                    canContinue = ::live) || !bot.output.sendNpcInteraction(3, npc)) return false
            if (!await(55_000) {
                    if (npc.isAlive && bot.healthPercent < 55) BossFood.eat(bot, food)
                    !npc.isAlive
                }) return false
        }
        if (bot.inventory.contains(raw)) return true
        if (!await { drop() != null }) return false
        return drop()?.let { handler.loot.pickup(it, ::live) } == true && bot.inventory.contains(raw)
    }

    private suspend fun dipMeats(): Boolean {
        if (Quest.hasOfferings(bot)) return true
        val cauldron = bot.world.locator.findObjectsOnTile(Quest.cauldronPosition) {
            it.id == Quest.CAULDRON && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return false
        if (!handler.interactions.approach(cauldron, timeout = approachTimeout, canContinue = ::live)) return false
        for ((raw, enchanted) in Quest.meats) {
            if (bot.inventory.contains(enchanted)) continue
            if (bot.healthPercent < 55) BossFood.eat(bot, food)
            if (!handler.inventory.useItem(raw).onObject(cauldron, ::live) ||
                !await { bot.inventory.contains(enchanted) }) return false
        }
        return true
    }

    private fun carrying(raw: Int, enchanted: Int) = bot.inventory.contains(raw) || bot.inventory.contains(enchanted)
    private fun stage() = Quest.stage(bot)
    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive

    private suspend fun await(timeout: Long = 10_000, done: suspend () -> Boolean): Boolean =
        withTimeoutOrNull(timeout) {
            while (live()) {
                if (done()) return@withTimeoutOrNull true
                delay(150)
            }
            false
        } == true
}
