package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import engine.bank.QuestBank
import game.content.demonSlayer.Delrith
import game.content.demonSlayer.DemonSlayer as Quest
import game.content.demonSlayer.DemonSlayerDialogue as Dialogue
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Completes Demon Slayer through native travel, dialogue, inventory interactions and combat.
 * The journal and carried or banked items determine the next step after interruption.
 *
 * @author lare96
 */
class DemonSlayerBotScript(bot: Bot, private val data: DemonSlayerData = DemonSlayerData()) : BotScript(bot) {
    companion object {
        private const val ATTEMPT_MS = 18 * 60_000L
        private const val RETRY_MS = 60 * 60_000L
        private const val SINK = 874
        private val food = BossFood.Policy(minimumBiteHealing = 3, requiredHealing = 0)
        private var Bot.retryAt by Attr.long().persist("demon_slayer_bot_retry_at")

        private val aris = Position(3203, 3424)
        private val varrockSouthRoad = Position(3210, 3398)
        private val prysin = Position(3204, 3473)
        private val rovin = Position(3204, 3496, 2)
        private val traiborn = Position(3112, 3162, 1)
        private val drain = Position(3225, 3495)
        private val sink = Position(3224, 3494)

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            if (stage == Quest.COMPLETE || !bot.isAlive || bot.combat.inCombat() || bot.overlays.hasWindow() ||
                bot.controllers.primary.javaClass != PlayerController::class.java || System.currentTimeMillis() < bot.retryAt ||
                bot.skill(Skill.ATTACK).staticLevel < 15 || bot.skill(Skill.HITPOINTS).staticLevel < 20) return false
            if (Quest.needsFortunePayment(bot) && owned(bot, Quest.COINS) == 0L) return false
            if (stage >= Quest.SILVERLIGHT_RECLAIMED) return true
            if (owned(bot, Quest.DRAIN_KEY) == 0L && Quest.washedKey(bot) == null &&
                owned(bot, Quest.EMPTY_BUCKET) == 0L && owned(bot, Quest.WATER_BUCKET) == 0L) return false
            val bones = Quest.bonesRemaining(bot)
            return bones == 0 || owned(bot, Quest.TRAIBORN_KEY) > 0L || owned(bot, Quest.BONES) >= bones
        }

        private fun owned(bot: Bot, item: Int): Long =
            bot.inventory.computeAmountForId(item).toLong() + bot.bank.computeAmountForId(item)
    }

    /**
     * Retains the current attempt's deadline when the script stack is restored.
     *
     * @author lare96
     */
    class DemonSlayerData : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS

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

    override fun snapshot(): DemonSlayerData = data

    override fun allowsLoot(item: GroundItem): Boolean = item === Quest.washedKey(bot)

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        var step = "start the quest"
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            while (live() && stage() != Quest.COMPLETE) {
                step = nextStep()
                bot.log("Demon Slayer: $step.")
                val previousStage = stage()
                if (!advance() && stage() == previousStage) {
                    delay(600)
                    if (!live() || !advance() && stage() == previousStage) return@withTimeoutOrNull false
                }
            }
            stage() == Quest.COMPLETE
        } == true
        if (!finished) {
            failure = "Could not $step at ${bot.position}, quest stage ${stage()}."
            bot.log("Demon Slayer: $failure")
            bot.retryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private fun nextStep(): String = when (stage()) {
        0 -> "hear Aris's fortune"
        Quest.ARIS_SPOKEN_TO -> "ask Prysin about Silverlight"
        Quest.SILVERLIGHT_RECLAIMED -> "banish Delrith"
        else -> when {
            !carries(Quest.TRAIBORN_KEY) -> "retrieve Traiborn's key"
            !carries(Quest.ROVIN_KEY) -> "retrieve Rovin's key"
            !carries(Quest.DRAIN_KEY) -> "retrieve the drain key"
            else -> "exchange the three keys"
        }
    }

    private suspend fun advance(): Boolean = when (stage()) {
        0 -> (!Quest.needsFortunePayment(bot) || carry(Quest.COINS)) &&
            talk(Quest.ARIS, aris, Dialogue.PAY_ARIS, Dialogue.ASK_DELRITH, Dialogue.ACCEPT) { stage() > 0 }
        Quest.ARIS_SPOKEN_TO -> talk(Quest.PRYSIN, prysin, Dialogue.ARIS_SENT_ME, Dialogue.ASK_KEYS) {
            stage() >= Quest.KEY_HUNT
        }
        Quest.SILVERLIGHT_RECLAIMED -> banishDelrith()
        Quest.COMPLETE -> true
        else -> when {
            !carries(Quest.TRAIBORN_KEY) -> traibornKey()
            !carries(Quest.ROVIN_KEY) -> carry(Quest.ROVIN_KEY) ||
                talk(Quest.ROVIN, rovin, Dialogue.TELL_ROVIN, Dialogue.WARN_ROVIN) { carries(Quest.ROVIN_KEY) }
            !carries(Quest.DRAIN_KEY) -> carry(Quest.DRAIN_KEY) || drainKey()
            else -> talk(Quest.PRYSIN, prysin, Dialogue.GIVE_KEYS) { stage() >= Quest.SILVERLIGHT_RECLAIMED }
        }
    }

    private suspend fun traibornKey(): Boolean {
        if (carry(Quest.TRAIBORN_KEY)) return true
        if (stage() == Quest.KEY_HUNT && !talk(Quest.TRAIBORN, traiborn, Dialogue.ASK_TRAIBORN, Dialogue.GET_BONES) {
                stage() >= Quest.FIND_BONES
            }) return false
        val remaining = Quest.bonesRemaining(bot)
        if (remaining == 0) {
            if (bot.overlays.getOverlay(NpcDialogue::class.java)?.npcId == Quest.TRAIBORN &&
                await(4_000) { carries(Quest.TRAIBORN_KEY) }) return true
            return talk(Quest.TRAIBORN, traiborn, Dialogue.CLAIM_KEY) { carries(Quest.TRAIBORN_KEY) }
        }
        val carried = bot.inventory.computeAmountForId(Quest.BONES)
        val missing = minOf(remaining - carried, bot.inventory.computeRemainingSize())
        if (missing > 0 && bot.bank.contains(Quest.BONES) &&
            !withdraw(Item(Quest.BONES, minOf(missing, bot.bank.computeAmountForId(Quest.BONES))))) return false
        if (!carries(Quest.BONES)) return false
        return talk(Quest.TRAIBORN, traiborn, Dialogue.OFFER_BONES) {
            carries(Quest.TRAIBORN_KEY) || (!Quest.donatingBones(bot) && Quest.bonesRemaining(bot) < remaining)
        }
    }

    private suspend fun drainKey(): Boolean {
        if (Quest.washedKey(bot) == null) {
            if (!carry(Quest.WATER_BUCKET)) {
                if (!carry(Quest.EMPTY_BUCKET)) return false
                val tap = objectAt(SINK, sink) ?: return false
                if (!handler.inventory.useItem(Quest.EMPTY_BUCKET).onObject(tap) ||
                    !await { carries(Quest.WATER_BUCKET) }) return false
            }
            val pipe = objectAt(Quest.DRAIN, drain) ?: return false
            if (!handler.inventory.useItem(Quest.WATER_BUCKET).onObject(pipe) ||
                !await { Quest.washedKey(bot) != null }) return false
        }
        val key = Quest.washedKey(bot) ?: return false
        return handler.interactions.interact(1, key) { live() } && await { carries(Quest.DRAIN_KEY) }
    }

    private suspend fun banishDelrith(): Boolean {
        if (bot.equipment[Equipment.WEAPON]?.id != Quest.SILVERLIGHT) {
            if (!carry(Quest.SILVERLIGHT) &&
                !talk(Quest.PRYSIN, prysin, Dialogue.LOST_SWORD) { carries(Quest.SILVERLIGHT) }) return false
            if (!handler.equipment.equip(Quest.SILVERLIGHT)) return false
        }
        val incantation = learnIncantation() ?: return false
        val enemy = withTimeoutOrNull(65_000) {
            while (live()) {
                val npc = bot.world.locator.findNpcs(Delrith.spawn, 12) {
                    it.id == Delrith.DEMON && it.state == EntityState.ACTIVE && it.isAlive
                }.firstOrNull()
                if (npc != null) return@withTimeoutOrNull npc
                delay(300)
            }
            null
        } ?: return false
        val reach = bot.combat.getDefaultAttack(enemy)?.interactionPolicy ?: return false
        if (bot.position.isWithinDistance(aris, 12) &&
            !handler.webWalkTo(varrockSouthRoad, radius = 2, attempts = 2, fallback = false)) return false
        if (!handler.interactions.approach(enemy, reach, timeout = 120.seconds, allowOutOfView = true, canContinue = ::live) ||
            !bot.output.sendNpcInteraction(3, enemy)) return false
        if (!await(50_000) {
                if (bot.healthPercent < 50 && enemy.id == Delrith.DEMON) BossFood.eat(bot, food)
                Delrith.hasAttempt(bot, enemy) &&
                    bot.overlays.getOverlay(OptionDialogue::class.java)?.options?.contains(incantation) == true
            }) return false
        return handler.widgets.clickDialogueOption(incantation) && await { stage() == Quest.COMPLETE }
    }

    private suspend fun learnIncantation(): String? {
        var learned: String? = null
        val heard = talk(Quest.ARIS, aris, Dialogue.ASK_INCANTATION) {
            val page = bot.overlays.getOverlay(NpcDialogue::class.java)
            if (page?.npcId == Quest.ARIS && page.text.firstOrNull() == "Say these words in this order:") {
                learned = page.text.drop(1).joinToString(" ").removeSuffix(".").takeIf { it.isNotBlank() }
            }
            learned != null
        }
        return learned.takeIf { heard }
    }

    private suspend fun carry(id: Int): Boolean = carries(id) || bot.bank.contains(id) && withdraw(Item(id))

    private suspend fun withdraw(item: Item): Boolean {
        if (!bot.inventory.hasSpaceFor(item)) return false
        if (!bot.bank.isOpen) {
            val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
            val option = (bank as? GameObject)?.let { QuestBank.forObject(it.id)?.option } ?: 2
            if (!handler.interactions.interact(option, bank) || !await { bot.bank.isOpen }) return false
        }
        val result = handler.banking.withdrawAll(listOf(item))
        bot.overlays.closeWindows()
        return result
    }

    private suspend fun talk(id: Int, at: Position, vararg options: String, done: () -> Boolean): Boolean {
        if (done()) return true
        val npc = bot.world.locator.findNpcs(at, 10) { it.id == id && it.state == EntityState.ACTIVE }.firstOrNull()
            ?: return false
        if (!handler.interactions.approach(npc, timeout = 300.seconds, allowOutOfView = true, canContinue = ::live)) return false
        return BotNpcDialogues.respond(bot, npc, options.toList(), completed = done, advanceSpeech = true) {
            handler.interactions.interactLocally(1, npc, allowed = ::live)
        } || await(2_400, done)
    }

    private fun objectAt(id: Int, at: Position): GameObject? =
        bot.world.locator.findObjectsOnTile(at) { it.id == id && it.state == EntityState.ACTIVE }.firstOrNull()

    private fun carries(id: Int): Boolean = bot.inventory.contains(id)

    private fun stage(): Int = Quest.stage(bot)

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive

    private suspend fun await(timeout: Long = 15_000, done: suspend () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live() && !done()) delay(150)
        live()
    } == true
}
