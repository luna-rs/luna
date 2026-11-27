package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import game.content.dragonSlayer.DragonSlayerDialogue
import game.content.runeMysteries.RuneMysteries as Quest
import game.content.runeMysteries.RuneMysteriesDialogue as Dialogue
import game.skill.runecrafting.essenceMine.EssenceMineWizard
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.interact.InteractionPolicy
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Delivers the talisman, package and notes through normal dialogue and item use.
 * A resumed attempt reads the saved quest stage and recovers missing or banked items.
 *
 * @author lare96
 */
class RuneMysteriesBotScript(bot: Bot, private val data: RuneMysteriesData = RuneMysteriesData()) : BotScript(bot) {
    companion object {
        private const val ATTEMPT_MS = 20 * 60_000L
        private const val RETRY_MS = 30 * 60_000L
        private var Bot.runeMysteriesRetryAt by Attr.long().persist("rune_mysteries_bot_retry_at")
        private val questItems = setOf(Quest.AIR_TALISMAN, Quest.RESEARCH_PACKAGE, Quest.RESEARCH_NOTES)

        fun eligible(bot: Bot) = Quest.stage(bot) < Quest.COMPLETE && bot.state == EntityState.ACTIVE &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.overlays.hasWindow() &&
            bot.controllers.primary.javaClass == PlayerController::class.java &&
            System.currentTimeMillis() >= bot.runeMysteriesRetryAt
    }

    /**
     * Keeps the remaining attempt budget when the native script stack saves or resumes the bot.
     *
     * @author lare96
     */
    class RuneMysteriesData : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    /**
     * Locates each quest contact near the NPC's ordinary world spawn.
     *
     * @author lare96
     */
    private enum class Contact(val id: Int, val spawn: Position) {
        DUKE(DragonSlayerDialogue.DUKE, Position(3212, 3220, 1)),
        SEDRIDOR(EssenceMineWizard.SEDRIDOR.npcId, Position(3103, 9571)),
        AUBURY(EssenceMineWizard.AUBURY.npcId, Position(3253, 3402))
    }

    var failure: String? = null
        private set

    override fun snapshot() = data

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            while (live() && stage() < Quest.COMPLETE) {
                val before = stage()
                bot.log("Rune Mysteries: stage $before.")
                if (!advance(before) && stage() == before) return@withTimeoutOrNull false
            }
            stage() == Quest.COMPLETE
        } == true
        if (!finished) {
            failure = "Could not finish stage ${stage()} at ${bot.position}."
            bot.log("Rune Mysteries: $failure")
            bot.runeMysteriesRetryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private fun stage() = Quest.stage(bot)

    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat()

    private suspend fun advance(previousStage: Int): Boolean = when (previousStage) {
        Quest.NOT_STARTED -> makeSpace() &&
            talk(Contact.DUKE, Dialogue.ASK_FOR_QUEST, Dialogue.ACCEPT_QUEST) { stage() > previousStage }
        Quest.STARTED -> carry(Quest.AIR_TALISMAN) {
            talk(Contact.DUKE, Dialogue.ASK_FOR_QUEST) { bot.inventory.contains(Quest.AIR_TALISMAN) }
        } && talk(Contact.SEDRIDOR, Dialogue.SEEK_HEAD_WIZARD, Dialogue.HAND_OVER_TALISMAN, Dialogue.ACCEPT_PACKAGE) {
            stage() >= Quest.PACKAGE_RECEIVED
        }
        Quest.TALISMAN_DELIVERED -> makeSpace() &&
            talk(Contact.SEDRIDOR, Dialogue.ASK_ABOUT_RESEARCH, Dialogue.ACCEPT_PACKAGE) { stage() > previousStage }
        Quest.PACKAGE_RECEIVED -> carry(Quest.RESEARCH_PACKAGE) {
            talk(Contact.SEDRIDOR, Dialogue.ASK_FOR_PACKAGE) { bot.inventory.contains(Quest.RESEARCH_PACKAGE) }
        } && handOver(Contact.AUBURY, Quest.RESEARCH_PACKAGE) { stage() > previousStage }
        Quest.PACKAGE_DELIVERED -> makeSpace() &&
            talk(Contact.AUBURY, Dialogue.ASK_FOR_NOTES) { stage() > previousStage }
        Quest.NOTES_RECEIVED -> carry(Quest.RESEARCH_NOTES) {
            talk(Contact.AUBURY, Dialogue.ASK_FOR_COPY) { bot.inventory.contains(Quest.RESEARCH_NOTES) }
        } && handOver(Contact.SEDRIDOR, Quest.RESEARCH_NOTES) { stage() > previousStage }
        else -> false
    }

    private suspend fun carry(id: Int, replace: suspend () -> Boolean): Boolean {
        if (bot.inventory.contains(id)) return true
        if (!makeSpace()) return false
        if (!bot.bank.contains(id)) return replace()
        return atBank { handler.banking.withdrawUnnoted(Item(id), ::live) }
    }

    private suspend fun makeSpace(): Boolean {
        if (!bot.inventory.isFull) return true
        val spare = bot.inventory.filterNotNull().firstOrNull { it.id !in questItems } ?: return false
        return atBank { handler.banking.deposit(spare) && !bot.inventory.isFull }
    }

    private suspend fun atBank(action: suspend () -> Boolean): Boolean {
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!handler.banking.open(bank)) return false
        try {
            return action()
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }

    private suspend fun talk(contact: Contact, vararg responses: String, done: () -> Boolean) =
        interact(contact, responses.toList(), done) { bot.output.sendNpcInteraction(1, it) }

    private suspend fun handOver(contact: Contact, item: Int, done: () -> Boolean) =
        interact(contact, emptyList(), done) { handler.inventory.useItem(item).onNpc(it, ::live) }

    private suspend fun interact(contact: Contact, responses: List<String>, done: () -> Boolean,
                                 start: suspend (Npc) -> Boolean): Boolean {
        val npc = bot.world.locator.findNpcs(contact.spawn, 12) {
            it.id == contact.id && it.isAlive && it.state == EntityState.ACTIVE
        }.minByOrNull { it.position.computeLongestDistance(contact.spawn) } ?: return false
        if (!approach(npc)) return false
        return BotNpcDialogues.respond(bot, npc, responses, timeout = 55.seconds, allowed = ::live,
            completed = done, playerSpeech = setOf("The Duke of Lumbridge sent me with a talisman he found."),
            advanceSpeech = true) {
            val previous = bot.dialogues
            start(npc) && waitFor(12.seconds) { !live() || done() || bot.dialogues?.let { it !== previous } == true }
        }
    }

    private suspend fun approach(npc: Npc): Boolean = withTimeoutOrNull(7.minutes) {
        repeat(3) {
            if (!live() || !npc.isAlive) return@withTimeoutOrNull false
            val anchor = npc.position
            // Starting face-to-face avoids a corrective step closing the newly opened dialogue.
            val stands = handler.interactions.interactionStands(npc, InteractionPolicy.STANDARD_SIZE).filter {
                abs(it.x - anchor.x) + abs(it.y - anchor.y) == 1
            }
            if (bot.position in stands && bot.walking.isEmpty) return@withTimeoutOrNull true
            if (stands.isEmpty() || !handler.webWalkToAny(stands)) return@withTimeoutOrNull false
        }
        false
    } == true
}
