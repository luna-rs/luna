package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import engine.spawn.PersistentGroundItem
import game.bot.scripts.skills.MillFlourBotScript.Companion.BotMill
import game.content.cooksAssistant.CooksAssistant as Quest
import game.content.cooksAssistant.CooksAssistantDialogue as Dialogue
import game.content.flourMill.FlourMill
import game.content.flourMill.FlourMill.flourInBin
import game.content.flourMill.FlourMill.hopperFull
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Helps the Cook using carried or banked ingredients, or gathers them from ordinary Lumbridge sources.
 * The quest varp, inventory and mill state are read again after an interruption.
 *
 * @author lare96
 */
class CooksAssistantBotScript(bot: Bot, private val data: CooksAssistantData = CooksAssistantData()) : BotScript(bot) {
    companion object {
        private const val ATTEMPT_MS = 15 * 60_000L
        private const val RETRY_MS = 30 * 60_000L
        private const val BUCKET = 1925
        private const val DAIRY_COW = 8689
        private const val WHEAT = 313
        private val cookSpawn = Position(3209, 3215)
        private val potSpawn = Position(3209, 3214)
        private val bucketSpawn = Position(3225, 3294)
        private val eggSpawns = listOf(Position(3226, 3301), Position(3229, 3299))
        private val dairyCows = listOf(Position(3252, 3275), Position(3254, 3272))
        private val supplies = setOf(Quest.MILK, Quest.EGG, Quest.FLOUR, BUCKET, FlourMill.POT, FlourMill.GRAIN)
        private var Bot.cooksAssistantRetryAt by Attr.long().persist("cooks_assistant_bot_retry_at")

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            return stage < Quest.COMPLETE && bot.state == EntityState.ACTIVE &&
                ActionSafety.NON_COMBAT.allows(bot) && !bot.overlays.hasWindow() &&
                bot.controllers.primary.javaClass == PlayerController::class.java &&
                !bot.inventory.contains(Quest.COOK_ANAGRAM) &&
                System.currentTimeMillis() >= bot.cooksAssistantRetryAt &&
                (stage == Quest.STARTED || bot.position.computeLongestDistance(cookSpawn) <= 128)
        }
    }

    /**
     * Retains the attempt deadline when the script stack is saved.
     *
     * @author lare96
     */
    class CooksAssistantData : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    var failure: String? = null
        private set

    override val allowsPaidTravel = false

    override fun snapshot() = data

    override suspend fun run(): Boolean {
        if (Quest.stage(bot) == Quest.COMPLETE) return true
        var objective = "start the quest"
        suspend fun step(description: String, action: suspend () -> Boolean): Boolean {
            objective = description
            bot.log("Cook's Assistant: $objective.")
            repeat(2) {
                if (!live()) return false
                if (action()) return true
            }
            return false
        }

        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            if (Quest.stage(bot) == Quest.NOT_STARTED && !step("speak to the Cook") { talk(starting = true) }) {
                return@withTimeoutOrNull false
            }
            step("get a pot of flour") { carry(Quest.FLOUR, ::millFlour) } &&
                step("get an egg") { carry(Quest.EGG) { take(Quest.EGG, eggSpawns) } } &&
                step("get a bucket of milk") { carry(Quest.MILK, ::milkCow) } &&
                step("hand the ingredients to the Cook") { talk(starting = false) }
        } == true
        if (!finished && Quest.stage(bot) < Quest.COMPLETE) {
            failure = "Could not $objective at ${bot.position}, quest stage ${Quest.stage(bot)}."
            bot.log("Cook's Assistant: $failure")
            bot.cooksAssistantRetryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat()

    private suspend fun talk(starting: Boolean): Boolean {
        fun done() = Quest.stage(bot) >= if (starting) Quest.STARTED else Quest.COMPLETE
        if (done()) return true
        val cook = bot.world.locator.findNpcs(cookSpawn, 10) {
            it.id == Quest.COOK && it.state == EntityState.ACTIVE && it.isAlive
        }.firstOrNull() ?: return false
        if (!handler.interactions.approach(cook, InteractionPolicy.STANDARD_SIZE, allowOutOfView = true,
                canContinue = ::live)) return false
        val choices = if (starting) listOf(Dialogue.ASK, Dialogue.ACCEPT) else emptyList()
        return BotNpcDialogues.respond(bot, cook, choices, allowed = ::live, completed = ::done,
            playerSpeech = setOf(Dialogue.READY, Dialogue.INGREDIENTS), advanceSpeech = true,
            textPages = setOf(Dialogue.HAND_IN)) {
            val previous = bot.dialogues
            bot.output.sendNpcInteraction(1, cook) &&
                waitFor(8.seconds) { !live() || done() || bot.dialogues?.let { it !== previous } == true }
        }
    }

    private suspend fun carry(id: Int, obtain: suspend () -> Boolean): Boolean {
        if (bot.inventory.contains(id)) return true
        val banked = bot.bank.contains(id)
        if (banked || bot.inventory.isFull) {
            val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
            if (!live() || !handler.banking.open(bank)) return false
            try {
                if (bot.inventory.isFull) {
                    val spare = bot.inventory.filterNotNull().firstOrNull {
                        it.id !in supplies || bot.inventory.computeAmountForId(it.id) > 1
                    } ?: return false
                    if (!handler.banking.deposit(spare) || bot.inventory.isFull) return false
                }
                if (banked) return handler.banking.withdrawUnnoted(Item(id), ::live)
            } finally {
                if (bot.bank.isOpen) handler.widgets.clickCloseInterface()
            }
        }
        return live() && obtain()
    }

    private suspend fun take(id: Int, spawns: List<Position>): Boolean {
        fun items() = spawns.flatMap { at ->
            bot.world.locator.findItemsOnTile(at) {
                it is PersistentGroundItem && it.id == id && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
            }
        }.sortedBy { it.position.computeLongestDistance(bot.position) }
        if (items().isEmpty()) {
            if (!handler.webWalkTo(spawns.first(), radius = 2, bank = BankAccess.NONE, fallback = false)) return false
            if (!waitFor(20.seconds) { !live() || items().isNotEmpty() }) return false
        }
        return items().any { handler.loot.pickup(it) { live() && !bot.inventory.contains(id) } }
    }

    private suspend fun milkCow(): Boolean {
        if (!carry(BUCKET) { take(BUCKET, listOf(bucketSpawn)) }) return false
        val cow = dairyCows.firstNotNullOfOrNull { objectAt(it, setOf(DAIRY_COW)) } ?: return false
        if (!handler.inventory.useItem(BUCKET).onObject(cow, ::live)) return false
        if (!waitFor(6.seconds) { bot.overlays.getOverlay(MakeItemDialogue::class.java) != null }) return false
        handler.widgets.clickMakeItem(0, 1)
        return waitFor(6.seconds) { bot.inventory.contains(Quest.MILK) }
    }

    private suspend fun millFlour(): Boolean {
        if (!carry(FlourMill.POT) { take(FlourMill.POT, listOf(potSpawn)) }) return false
        val mill = BotMill.LUMBRIDGE
        if (bot.flourInBin == 0) {
            if (!bot.hopperFull) {
                if (!carry(FlourMill.GRAIN, ::pickWheat)) return false
                val hopper = objectAt(mill.hopper, FlourMill.HOPPERS) ?: return false
                if (!handler.inventory.useItem(FlourMill.GRAIN).onObject(hopper, ::live) ||
                    !waitFor(6.seconds) { bot.hopperFull }) return false
            }
            val controls = objectAt(mill.controls, FlourMill.CONTROLS) ?: return false
            if (!handler.interactions.interact(1, controls, ::live) || !waitFor(6.seconds) { bot.flourInBin > 0 }) {
                return false
            }
        }
        val bin = objectAt(mill.bin, setOf(FlourMill.FLOUR_BIN)) ?: return false
        return handler.inventory.useItem(FlourMill.POT).onObject(bin, ::live) &&
            waitFor(6.seconds) { bot.inventory.contains(Quest.FLOUR) }
    }

    private suspend fun pickWheat(): Boolean {
        val field = SubZone.LUMBRIDGE_MILL_WHEAT.area
        fun crops() = bot.world.locator.findObjects(field.centerPosition, field.tileRadius) {
            it.id == WHEAT && it.position in field && it.state == EntityState.ACTIVE
        }
        if (!waitFor(14.seconds) { !live() || crops().isNotEmpty() }) return false
        val wheat = crops().minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false
        return handler.interactions.interact(2, wheat, ::live) &&
            waitFor(6.seconds) { bot.inventory.contains(FlourMill.GRAIN) }
    }

    private fun objectAt(at: Position, ids: Set<Int>) =
        bot.world.locator.findObjectsOnTile(at) { it.state == EntityState.ACTIVE && it.id in ids }.firstOrNull()
}
