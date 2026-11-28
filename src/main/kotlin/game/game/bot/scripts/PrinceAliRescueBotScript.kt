package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import com.google.gson.JsonObject
import game.content.princeAliRescue.DraynorJail
import game.content.princeAliRescue.PrinceAliContacts
import game.content.princeAliRescue.PrinceAliRescue as Quest
import game.content.princeAliRescue.PrinceAliRescue.aliCellUnlocked
import game.content.princeAliRescue.PrinceAliSupplies
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
 * Rescues Prince Ali through native dialogues, supplies and item interactions.
 *
 * @author lare96
 */
class PrinceAliRescueBotScript(bot: Bot, private val data: Data = Data()) : BotScript(bot) {
    companion object {
        private const val ATTEMPT_MS = 18 * 60_000L
        private const val RETRY_MS = 30 * 60_000L
        private const val DYE_PRICE = 5
        private val hassanTile = Position(3302, 3163)
        private val osmanTile = Position(3286, 3180)
        private val leelaTile = Position(3113, 3263)
        private val nedTile = Position(3100, 3258)
        private val aggieTile = Position(3086, 3259)
        private val keliTile = Position(3128, 3244)
        private val joeTile = Position(3123, 3245)
        private val aliTile = Position(3123, 3242)
        private val outsideCell = Position(3123, 3244)
        private var Bot.princeAliRetryAt by Attr.long().persist("prince_ali_bot_retry_at")

        fun eligible(bot: Bot): Boolean =
            Quest.stage(bot) < Quest.COMPLETE && bot.isAlive && !bot.combat.inCombat() && !bot.overlays.hasWindow() &&
                bot.controllers.primary.javaClass == PlayerController::class.java &&
                System.currentTimeMillis() >= bot.princeAliRetryAt && bot.actionHandler.hasAll(supplies(bot))

        /**
         * Uses owned props where available; otherwise prepares the ingredients for the native exchanges.
         */
        private fun supplies(bot: Bot): List<Item> {
            val stage = Quest.stage(bot)
            if (stage >= Quest.ALI_RESCUED) return emptyList()
            val amounts = mutableMapOf<Int, Int>()
            fun need(id: Int, amount: Int = 1) {
                amounts.merge(id, amount, Int::plus)
            }
            val handler = bot.actionHandler
            fun owned(id: Int) = handler.has(Item(id))
            if (owned(Quest.BLONDE_WIG)) {
                need(Quest.BLONDE_WIG)
            } else {
                if (owned(Quest.WIG)) need(Quest.WIG) else need(Quest.WOOL, 3)
                if (owned(Quest.YELLOW_DYE)) {
                    need(Quest.YELLOW_DYE)
                } else {
                    need(Quest.ONION, 2)
                    need(Quest.COINS, DYE_PRICE)
                }
            }
            if (owned(Quest.SKIN_PASTE)) {
                need(Quest.SKIN_PASTE)
            } else {
                need(Quest.ASHES)
                need(Quest.FLOUR)
                need(Quest.REDBERRIES)
                need(if (owned(Quest.BUCKET_OF_WATER)) Quest.BUCKET_OF_WATER else Quest.JUG_OF_WATER)
            }
            need(Quest.PINK_SKIRT)
            if (stage < Quest.KELI_TIED) {
                if (owned(Quest.ROPE)) need(Quest.ROPE) else need(Quest.COINS, PrinceAliSupplies.ROPE_PRICE)
            }
            if (stage < Quest.JOE_DRUNK) need(Quest.BEER, 3)
            if (owned(Quest.BRONZE_KEY)) {
                need(Quest.BRONZE_KEY)
            } else when (Quest.keyStatus(bot)) {
                Quest.KEY_COLLECTED -> need(Quest.COINS, Quest.KEY_REPLACEMENT_PRICE)
                Quest.KEY_ORDERED -> Unit
                else -> {
                    need(Quest.BRONZE_BAR)
                    need(if (owned(Quest.KEY_PRINT)) Quest.KEY_PRINT else Quest.SOFT_CLAY)
                }
            }
            return amounts.map { (id, amount) -> Item(id, amount) }
        }
    }

    /**
     * Retains the run's deadline across script saves. The character save owns all quest stages.
     *
     * @author lare96
     */
    class Data : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    override val actionSafety = ActionSafety.NON_COMBAT
    var failure: String? = null
        private set

    override fun snapshot(): Data = data

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        var objective = "prepare supplies"
        suspend fun step(description: String, action: suspend () -> Boolean): Boolean {
            objective = description
            bot.log("Prince Ali Rescue: $description.")
            repeat(4) {
                if (withTimeoutOrNull(45.seconds) { awaitActionSafety() } != true || !live()) return false
                if (action()) return true
                delay(600)
            }
            return false
        }

        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            if (!step("prepare supplies", ::prepare)) return@withTimeoutOrNull false
            if (stage() == 0 && !step("accept Hassan's assignment", ::startQuest)) return@withTimeoutOrNull false
            if (stage() == Quest.STARTED && !step("hear Osman's plan", ::briefing)) return@withTimeoutOrNull false
            if (stage() < Quest.ALI_RESCUED) {
                if (!has(Quest.BLONDE_WIG)) {
                    if (!has(Quest.WIG) && !step("ask Ned for a wig", ::getWig)) return@withTimeoutOrNull false
                    if (!step("dye the wig blonde", ::dyeWig)) return@withTimeoutOrNull false
                }
                if (!has(Quest.SKIN_PASTE) && !step("ask Aggie for skin paste", ::getPaste)) return@withTimeoutOrNull false
                if (stage() < Quest.KELI_TIED && !has(Quest.ROPE) &&
                    !step("buy rope from Ned", ::getRope)) return@withTimeoutOrNull false
                if (!has(Quest.BRONZE_KEY) && Quest.keyStatus(bot) == 0) {
                    if (!has(Quest.KEY_PRINT) && !step("take an impression of Keli's key", ::getImpression)) {
                        return@withTimeoutOrNull false
                    }
                    if (!step("take the impression to Osman", ::orderKey)) return@withTimeoutOrNull false
                }
                if (!has(Quest.BRONZE_KEY) && !step("collect the key from Leela", ::collectKey)) return@withTimeoutOrNull false
            }
            if (stage() == Quest.BRIEFED && !step("show Leela the equipment", ::showLeela)) return@withTimeoutOrNull false
            if (stage() == Quest.PREPARED && !step("give Joe three beers", ::distractJoe)) return@withTimeoutOrNull false
            if (stage() == Quest.JOE_DRUNK && !step("tie up Lady Keli", ::tieKeli)) return@withTimeoutOrNull false
            if (stage() == Quest.KELI_TIED && !step("unlock the cell and rescue Ali", ::rescue)) return@withTimeoutOrNull false
            step("return to Hassan for the reward", ::claimReward)
        } == true
        if (!finished && stage() != Quest.COMPLETE) {
            failure = "Could not $objective at ${bot.position} (stage ${stage()})."
            bot.log("Prince Ali Rescue: $failure")
            bot.princeAliRetryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private suspend fun prepare(): Boolean {
        val missing = supplies(bot).mapNotNull { item ->
            val amount = item.amount - bot.inventory.computeAmountForId(item.id)
            if (amount > 0) Item(item.id, amount) else null
        }
        if (missing.isEmpty()) return true
        if (!handler.hasAll(missing) || !bot.inventory.hasSpaceForAll(missing) || !leaveCell()) return false
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!handler.interactions.interact(if (bank is Npc) 2 else 1, bank, ::live) ||
            !await { bot.bank.isOpen }) return false
        return try {
            handler.banking.withdrawAll(missing)
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }

    private suspend fun startQuest() = talk(Quest.HASSAN, hassanTile, PrinceAliContacts.OFFER_HELP) {
        stage() >= Quest.STARTED
    }

    private suspend fun briefing() = talk(Quest.OSMAN, osmanTile, PrinceAliContacts.FIRST_TASK,
        PrinceAliContacts.SECOND_TASK, PrinceAliContacts.GET_SUPPLIES) { stage() >= Quest.BRIEFED }

    private suspend fun getWig() = leaveCell() &&
        talk(Quest.NED, nedTile, PrinceAliSupplies.ASK_WIG, PrinceAliSupplies.MAKE_WIG) { has(Quest.WIG) }

    private suspend fun getRope() = leaveCell() &&
        talk(Quest.NED, nedTile, PrinceAliSupplies.ASK_ROPE, PrinceAliSupplies.BUY_ROPE) { has(Quest.ROPE) }

    private suspend fun dyeWig(): Boolean {
        if (has(Quest.BLONDE_WIG)) return true
        if (!has(Quest.YELLOW_DYE)) {
            if (!leaveCell()) return false
            val aggie = approach(Quest.AGGIE, aggieTile) ?: return false
            if (!BotNpcDialogues.respond(bot, aggie, emptyList(), allowed = ::live, completed = { has(Quest.YELLOW_DYE) },
                    playerSpeech = setOf("Okay, make me some yellow dye please."), advanceSpeech = true) {
                    handler.inventory.useItem(Quest.ONION).onNpc(aggie, ::live)
                }) return false
        }
        return handler.inventory.useItem(Quest.YELLOW_DYE).onItem(Quest.WIG) && await { has(Quest.BLONDE_WIG) }
    }

    private suspend fun getPaste() = leaveCell() &&
        talk(Quest.AGGIE, aggieTile, PrinceAliSupplies.ASK_PASTE, PrinceAliSupplies.MAKE_PASTE) { has(Quest.SKIN_PASTE) }

    private suspend fun getImpression() = leaveCell() &&
        talk(Quest.KELI, keliTile, DraynorJail.FLATTER, DraynorJail.ASK_PRISONER, DraynorJail.ASK_ESCAPE,
            DraynorJail.SEE_KEY, DraynorJail.TOUCH_KEY) { has(Quest.KEY_PRINT) }

    private suspend fun orderKey() = leaveCell() &&
        talk(Quest.OSMAN, osmanTile) { Quest.keyStatus(bot) == Quest.KEY_ORDERED }

    private suspend fun collectKey(): Boolean {
        if (!leaveCell()) return false
        return if (Quest.keyStatus(bot) == Quest.KEY_COLLECTED) {
            talk(Quest.LEELA, leelaTile, PrinceAliContacts.REPLACE_KEY) { has(Quest.BRONZE_KEY) }
        } else talk(Quest.LEELA, leelaTile) { has(Quest.BRONZE_KEY) }
    }

    private suspend fun showLeela() = talk(Quest.LEELA, leelaTile) { stage() >= Quest.PREPARED }

    private suspend fun distractJoe() = talk(Quest.JOE, joeTile, DraynorJail.OFFER_BEER) { stage() >= Quest.JOE_DRUNK }

    private suspend fun tieKeli(): Boolean {
        val keli = approach(Quest.KELI, keliTile) ?: return false
        return handler.inventory.useItem(Quest.ROPE).onNpc(keli, ::live) && await { stage() >= Quest.KELI_TIED }
    }

    private suspend fun rescue(): Boolean {
        if (!bot.aliCellUnlocked) {
            if (!leaveCell() || !approachCell()) return false
            val door = waitForCellDoor() ?: return false
            if (!handler.inventory.useItem(Quest.BRONZE_KEY).onObject(door, ::live) ||
                !await { bot.aliCellUnlocked }) return false
        }
        if (!insideCell()) {
            // A resumed character may have unlocked the door before it took the step through.
            if (!await(4_000) { insideCell() } && !enterCell()) return false
        }
        return talk(Quest.ALI, aliTile, playerSpeech = setOf("Dress as Lady Keli and go to Leela. I have the disguise and the key.")) {
            stage() >= Quest.ALI_RESCUED
        }
    }

    private suspend fun claimReward(): Boolean =
        leaveCell() && talk(Quest.HASSAN, hassanTile) { stage() == Quest.COMPLETE }

    private fun insideCell(): Boolean = Quest.insideCell(bot.position)

    private suspend fun leaveCell(): Boolean = !insideCell() || crossCell(entering = false)

    private suspend fun approachCell(): Boolean =
        handler.webWalkTo(outsideCell, bank = BankAccess.NONE, fallback = false)

    private suspend fun enterCell(): Boolean = approachCell() && crossCell(entering = true)

    private suspend fun crossCell(entering: Boolean): Boolean {
        val door = waitForCellDoor() ?: return false
        return handler.interactions.interact(1, door, ::live) && await { insideCell() == entering }
    }

    private suspend fun waitForCellDoor(): GameObject? {
        fun door() = bot.world.locator.findObjectsOnTile(Quest.cellDoorTile) {
            it.id == Quest.CELL_DOOR && it.state == EntityState.ACTIVE
        }.firstOrNull()
        return if (await { door() != null }) door() else null
    }

    private suspend fun approach(id: Int, at: Position): Npc? {
        val npc = bot.world.locator.findNpcs(at, 12) {
            it.id == id && it.isAlive && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return null
        return npc.takeIf {
            handler.interactions.approach(it, timeout = 150.seconds, allowOutOfView = true, canContinue = ::live)
        }
    }

    private suspend fun talk(id: Int, at: Position, vararg choices: String, playerSpeech: Set<String> = emptySet(),
                             done: () -> Boolean): Boolean {
        if (done()) return true
        val speaker = approach(id, at) ?: return false
        return BotNpcDialogues.respond(bot, speaker, choices.toList(), allowed = ::live, completed = done,
            playerSpeech = playerSpeech, advanceSpeech = true) {
            handler.interactions.interactLocally(1, speaker, allowed = ::live)
        }
    }

    private fun stage(): Int = Quest.stage(bot)

    private fun has(id: Int): Boolean = bot.inventory.contains(id)

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat()

    private suspend fun await(timeout: Long = 12_000, done: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live() && !done()) delay(150)
        done()
    } == true
}
