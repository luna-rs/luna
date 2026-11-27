package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import engine.spawn.PersistentGroundItem
import game.content.romeoJuliet.RomeoJuliet as Quest
import game.content.romeoJuliet.RomeoJulietDialogue as Dialogue
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.DialogueInterface
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Follows the Varrock quest through ordinary interactions and resumes from the saved story stage.
 *
 * @author lare96
 */
class RomeoJulietBotScript(bot: Bot) : BotScript(bot) {
    companion object {
        private val square = Position(3211, 3425)
        private val julietHouse = Position(3158, 3425, 1)
        private val church = Position(3254, 3484)
        private val apothecary = Position(3195, 3404)
        private val berrySpawns = listOf(Position(3266, 3361), Position(3273, 3375), Position(3277, 3370))
        private val questItems = setOf(Quest.MESSAGE, Quest.POTION, Quest.BERRIES)

        private var Bot.romeoJulietRetryAt by Attr.long().persist("romeo_juliet_bot_retry_at")

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            return stage < Quest.COMPLETE && bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) &&
                !bot.overlays.hasWindow() && bot.controllers.primary.javaClass == PlayerController::class.java &&
                System.currentTimeMillis() >= bot.romeoJulietRetryAt &&
                (stage != Quest.NOT_STARTED || bot.position.isWithinDistance(square, 72))
        }
    }

    var failure: String? = null
        private set

    /**
     * The registered script resumes from the character's quest stage and belongings.
     *
     * @author lare96
     */
    class RomeoJulietData : BotScriptData() {
        override fun load(data: JsonObject) {}
        override fun save(data: JsonObject) {}
    }

    override fun snapshot(): RomeoJulietData = RomeoJulietData()

    override fun allowsLoot(item: GroundItem): Boolean = item.id in questItems

    override suspend fun run(): Boolean {
        var step = "start the quest"
        val finished = withTimeoutOrNull(15.minutes) {
            while (live() && stage() < Quest.COMPLETE) {
                val previous = stage()
                step = when (previous) {
                    Quest.NOT_STARTED -> "speak to Romeo"
                    Quest.SPOKEN_ROMEO -> "get Juliet's message"
                    Quest.SPOKEN_JULIET -> "deliver Juliet's message"
                    Quest.PASSED_MESSAGE -> "speak to Father Lawrence"
                    Quest.SPOKEN_FATHER -> "learn the Cadava recipe"
                    Quest.SPOKEN_APOTHECARY -> "brew and deliver the Cadava potion"
                    Quest.JULIET_CRYPT -> "tell Romeo the plan"
                    else -> return@withTimeoutOrNull false
                }
                bot.log("Romeo & Juliet: $step.")
                if (!advance() && stage() == previous) {
                    delay(600)
                    if (!live() || !advance() && stage() == previous) return@withTimeoutOrNull false
                }
            }
            stage() == Quest.COMPLETE
        } == true
        if (!finished) {
            failure = "Could not $step at ${bot.position}, quest stage ${stage()}."
            bot.log("Romeo & Juliet: $failure")
            bot.romeoJulietRetryAt = System.currentTimeMillis() + 30.minutes.inWholeMilliseconds
        }
        return true
    }

    private fun stage(): Int = Quest.stage(bot)

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat()

    private suspend fun advance(): Boolean = when (stage()) {
        Quest.NOT_STARTED -> talk(Quest.ROMEO, square, Dialogue.HELP_ROMEO) { stage() >= Quest.SPOKEN_ROMEO }
        Quest.SPOKEN_ROMEO -> carry(Quest.MESSAGE) { true } &&
            talk(Quest.JULIET_SPAWN, julietHouse) { stage() >= Quest.SPOKEN_JULIET }
        Quest.SPOKEN_JULIET -> carry(Quest.MESSAGE) {
            talk(Quest.JULIET_SPAWN, julietHouse) { bot.inventory.contains(Quest.MESSAGE) }
        } && talk(Quest.ROMEO, square) { stage() >= Quest.PASSED_MESSAGE }
        Quest.PASSED_MESSAGE -> talk(Quest.FATHER_LAWRENCE, church) { stage() >= Quest.SPOKEN_FATHER }
        Quest.SPOKEN_FATHER -> talk(Quest.APOTHECARY, apothecary, Dialogue.ASK_CADAVA) { stage() >= Quest.SPOKEN_APOTHECARY }
        Quest.SPOKEN_APOTHECARY -> carry(Quest.POTION) {
            carry(Quest.BERRIES, ::collectBerries) &&
                talk(Quest.APOTHECARY, apothecary, Dialogue.BREW_CADAVA) { bot.inventory.contains(Quest.POTION) }
        } && talk(Quest.JULIET_SPAWN, julietHouse) { stage() >= Quest.JULIET_CRYPT }
        Quest.JULIET_CRYPT -> {
            if (bot.dynamicMap != null || talk(Quest.ROMEO, square, Dialogue.GO_TO_CRYPT) { bot.dynamicMap != null }) {
                waitFor(45.seconds) { stage() == Quest.COMPLETE || bot.dynamicMap == null || !live() }
            }
            stage() == Quest.COMPLETE
        }
        else -> false
    }

    private suspend fun talk(id: Int, at: Position, option: String? = null, done: () -> Boolean): Boolean {
        if (done()) return true
        waitFor(5.seconds) { !bot.overlays.has(DialogueInterface::class.java) || !live() }
        if (!live()) return false
        val npc = bot.world.locator.findNpcs(at, 12) {
            it.id == id && it.z == at.z && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return false
        if (!handler.interactions.approach(npc, timeout = 120.seconds, allowOutOfView = true, canContinue = ::live)) {
            delay(600)
            if (!live() || !handler.interactions.approach(npc, timeout = 120.seconds, allowOutOfView = true,
                    canContinue = ::live)) return false
        }
        if (option != null) {
            return BotNpcDialogues.respond(bot, npc, listOf(option), allowed = ::live, completed = done, advanceSpeech = true) {
                handler.interactions.interactLocally(1, npc, allowed = ::live)
            }
        }
        if (!handler.interactions.interactLocally(1, npc, allowed = ::live)) return false
        waitFor(45.seconds) { done() || !live() }
        return done()
    }

    private suspend fun carry(id: Int, acquire: suspend () -> Boolean): Boolean {
        if (bot.inventory.contains(id)) return true
        val banked = bot.bank.contains(id)
        if (banked || !bot.inventory.hasSpaceFor(Item(id))) {
            val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
            if (!live() || !handler.banking.open(bank)) return false
            if (!bot.inventory.hasSpaceFor(Item(id))) {
                val deposit = bot.inventory.filterNotNull().firstOrNull { it.id !in questItems }
                if (deposit == null || !handler.banking.deposit(deposit)) {
                    handler.widgets.clickCloseInterface()
                    return false
                }
            }
            val withdrawn = !banked || handler.banking.withdrawUnnoted(Item(id), ::live)
            if (!handler.widgets.clickCloseInterface() || !withdrawn) return false
        }
        return bot.inventory.contains(id) || acquire()
    }

    private suspend fun collectBerries(): Boolean {
        if (!handler.webWalkToAny(berrySpawns) || !live()) return false
        fun targets() = berrySpawns.flatMap { tile ->
            bot.world.locator.findItemsOnTile(tile) {
                it is PersistentGroundItem && it.id == Quest.BERRIES && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
            }
        }.sortedBy { it.position.computeLongestDistance(bot.position) }
        waitFor(10.seconds) { targets().isNotEmpty() || !live() }
        return targets().any { handler.loot.pickup(it) { live() && !bot.inventory.contains(Quest.BERRIES) } }
    }
}
