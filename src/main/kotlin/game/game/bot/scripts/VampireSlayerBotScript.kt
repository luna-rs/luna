package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotNpcDialogues
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import game.content.vampireSlayer.CountDraynor
import game.content.vampireSlayer.VampireDialogue
import game.content.vampireSlayer.VampireProps
import game.content.vampireSlayer.VampireSlayer as Quest
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Follows the saved Vampire Slayer journal using ordinary dialogue, navigation and combat actions.
 *
 * @author lare96
 */
class VampireSlayerBotScript(bot: Bot, private val data: VampireSlayerData = VampireSlayerData()) : BotScript(bot) {
    companion object {
        private const val ATTEMPT_MS = 12 * 60_000L
        private const val COOLDOWN_MS = 30 * 60_000L
        private const val BARTENDER = 733
        private const val BEER_OPTION = "A glass of your finest ale please."
        private const val COINS = 995
        private const val BEER_PRICE = 2
        private const val MORGAN_STAIRS_DOWN = 1723
        private const val CRYPT_STAIRS_DOWN = 2616
        private val morganHouse = Position(3098, 3268)
        private val morganUpperStairs = Position(3100, 3266, 1)
        private val manorStairs = Position(3115, 3357)
        private val blueMoon = Position(3222, 3397)
        private val food = BossFood.Policy(minimumBiteHealing = 5, requiredHealing = 24)
        private var Bot.vampireSlayerRetryAt by Attr.long().persist("vampire_slayer_bot_retry_at")

        fun eligible(bot: Bot): Boolean {
            if (Quest.stage(bot) == Quest.COMPLETE || !bot.isAlive || bot.combat.inCombat() || bot.overlays.hasWindow() ||
                bot.controllers.primary.javaClass != PlayerController::class.java ||
                bot.equipment[Equipment.WEAPON] == null || bot.skill(Skill.ATTACK).staticLevel < 25 ||
                bot.skill(Skill.HITPOINTS).staticLevel < 25 || System.currentTimeMillis() < bot.vampireSlayerRetryAt ||
                BotCombatActionHandler.limitExperience(bot, bot.skill(Skill.ATTACK), Quest.ATTACK_REWARD) < Quest.ATTACK_REWARD) return false

            return bot.actionHandler.has(Item(Quest.HAMMER)) &&
                (bot.actionHandler.has(Item(Quest.STAKE)) || bot.actionHandler.has(Item(Quest.BEER)) ||
                    bot.actionHandler.has(Item(COINS, BEER_PRICE))) &&
                BossFood.plan(BossFood.stock(bot.inventory, bot.bank), food) != null
        }
    }

    /**
     * Keeps a bounded deadline when an in-progress script is saved and loaded.
     *
     * @author lare96
     */
    class VampireSlayerData : BotScriptData() {
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

    override fun snapshot(): VampireSlayerData = data

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        var objective = "prepare supplies"
        suspend fun step(label: String, action: suspend () -> Boolean): Boolean {
            objective = label
            bot.log("Vampire Slayer: $label.")
            repeat(3) {
                if (!live()) return false
                if (action()) return true
                delay(600)
            }
            return false
        }

        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            if (!step("prepare supplies", ::prepare)) return@withTimeoutOrNull false
            if (stage() == 0 && !step("speak to Morgan", ::startQuest)) return@withTimeoutOrNull false
            if (stage() == Quest.STARTED && !step("ask Dr Harlow for help", ::askHarlow)) return@withTimeoutOrNull false
            if (!has(Quest.STAKE) && !step("bring Dr Harlow a beer", ::getStake)) return@withTimeoutOrNull false
            if (!has(Quest.GARLIC) && !step("search Morgan's cupboard", ::getGarlic)) return@withTimeoutOrNull false
            step("defeat Count Draynor", ::fight)
        } == true
        if (!finished && stage() != Quest.COMPLETE) {
            failure = "Could not $objective at ${bot.position}, quest stage ${stage()}."
            bot.log("Vampire Slayer: $failure")
            bot.vampireSlayerRetryAt = System.currentTimeMillis() + COOLDOWN_MS
        }
        return true
    }

    private suspend fun prepare(): Boolean {
        if (!leaveMorganUpperFloor()) return false
        val inventory = bot.inventory
        val needed = buildList {
            if (!has(Quest.HAMMER)) add(Item(Quest.HAMMER))
            if (!has(Quest.STAKE) && bot.bank.contains(Quest.STAKE)) {
                add(Item(Quest.STAKE))
            } else if (!has(Quest.STAKE) && !has(Quest.BEER)) {
                if (bot.bank.contains(Quest.BEER)) add(Item(Quest.BEER))
                else {
                    val coins = inventory.computeAmountForId(COINS)
                    if (coins < BEER_PRICE) add(Item(COINS, BEER_PRICE - coins))
                }
            }
        }
        if (!handler.hasAll(needed) || !inventory.hasSpaceForAll(needed)) return false
        if (needed.isEmpty() && BossFood.carriedHealing(bot, food) >= food.requiredHealing) return true
        if (!bot.bank.isOpen) {
            val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
            if (!handler.interactions.interact(if (bank is Npc) 2 else 1, bank) || !await { bot.bank.isOpen }) return false
        }
        return try {
            handler.banking.withdrawAll(needed) && BossFood.withdraw(bot, food)
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }

    private suspend fun startQuest() =
        talk(Quest.MORGAN, morganHouse, VampireDialogue.ACCEPT, setOf("What's the problem?", "I'll go and find him.")) {
            stage() >= Quest.STARTED
        }

    private suspend fun askHarlow() =
        talk(Quest.HARLOW, blueMoon, VampireDialogue.ASK_FOR_HELP,
            setOf("A vampire is terrorising his village. He said you might be able to tell me how to stop it.")) {
            stage() >= Quest.SPOKE_TO_HARLOW
        }

    private suspend fun getStake(): Boolean {
        if (has(Quest.STAKE)) return true
        if (!has(Quest.BEER) && !talk(BARTENDER, blueMoon, BEER_OPTION) { has(Quest.BEER) }) return false
        val harlow = npc(Quest.HARLOW, blueMoon) ?: return false
        return handler.interactions.approach(harlow, allowOutOfView = true, canContinue = ::live) &&
            handler.interactions.interact(1, harlow, ::live) && await(20_000) { has(Quest.STAKE) }
    }

    private suspend fun getGarlic(): Boolean {
        if (has(Quest.GARLIC)) return true
        val at = VampireProps.cupboardTile
        val cupboard = objectAt(VampireProps.CUPBOARD, at) ?: objectAt(VampireProps.CUPBOARD_OPEN, at) ?: return false
        if (!handler.interactions.approach(cupboard, timeout = 150.seconds, canContinue = ::live)) return false
        objectAt(VampireProps.CUPBOARD, at)?.let { if (!bot.output.sendObjectInteraction(1, it)) return false }
        if (!await { objectAt(VampireProps.CUPBOARD_OPEN, at) != null }) return false
        val opened = objectAt(VampireProps.CUPBOARD_OPEN, at) ?: return false
        return bot.output.sendObjectInteraction(1, opened) && await { has(Quest.GARLIC) }
    }

    private suspend fun fight(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        // Going back down after a reset uses the same stairs and coffin as a fresh encounter.
        val count = CountDraynor.find(bot) ?: wakeCount() ?: return false
        if (!bot.output.sendNpcInteraction(3, count)) return false
        return withTimeoutOrNull(75.seconds) {
            while (live() && count.state == EntityState.ACTIVE && stage() != Quest.COMPLETE) {
                if (bot.healthPercent < 55 && !BossFood.eat(bot, food)) return@withTimeoutOrNull false
                delay(250)
            }
            stage() == Quest.COMPLETE
        } == true
    }

    private suspend fun wakeCount(): CountDraynor? {
        if (!leaveMorganUpperFloor()) return null
        if (!bot.position.isWithinDistance(CountDraynor.SPAWN, 10)) {
            val stairs = objectAt(CRYPT_STAIRS_DOWN, manorStairs) ?: return null
            if (!handler.interactions.approach(stairs, timeout = 120.seconds, canContinue = ::live) ||
                !bot.output.sendObjectInteraction(1, stairs) ||
                !await { bot.position.isWithinDistance(CountDraynor.SPAWN, 10) }) return null
        }
        val at = VampireProps.coffinTile
        val coffin = objectAt(VampireProps.COFFIN, at) ?: objectAt(VampireProps.COFFIN_OPEN, at) ?: return null
        if (!handler.interactions.approachLocally(coffin, InteractionPolicy.STANDARD_SIZE)) return null
        CountDraynor.find(bot)?.let { return it }
        if (!await(8_000) { objectAt(VampireProps.COFFIN, at) != null }) return null
        val closed = objectAt(VampireProps.COFFIN, at) ?: return null
        if (!bot.output.sendObjectInteraction(1, closed)) return null
        return if (await { CountDraynor.find(bot) != null }) CountDraynor.find(bot) else null
    }

    private suspend fun leaveMorganUpperFloor(): Boolean {
        if (bot.z != morganUpperStairs.z || !bot.position.isWithinDistance(morganUpperStairs, 6)) return true
        val stairs = objectAt(MORGAN_STAIRS_DOWN, morganUpperStairs) ?: return false
        return handler.interactions.approachLocally(stairs, InteractionPolicy.STANDARD_SIZE) &&
            bot.output.sendObjectInteraction(1, stairs) && await { bot.z == 0 }
    }

    private suspend fun talk(id: Int, at: Position, option: String, playerSpeech: Set<String> = emptySet(),
                             done: () -> Boolean): Boolean {
        if (done()) return true
        val speaker = npc(id, at) ?: return false
        if (!handler.interactions.approach(speaker, timeout = 150.seconds, allowOutOfView = true, canContinue = ::live)) return false
        return BotNpcDialogues.respond(bot, speaker, listOf(option), completed = done, allowed = ::live,
            playerSpeech = playerSpeech, advanceSpeech = true) {
            handler.interactions.interact(1, speaker, ::live)
        }
    }

    private fun npc(id: Int, at: Position): Npc? =
        bot.world.locator.findNpcs(at, 12) { it.id == id && it.isAlive && it.state == EntityState.ACTIVE }.firstOrNull()

    private fun objectAt(id: Int, at: Position): GameObject? =
        bot.world.locator.findObjectsOnTile(at) { it.id == id && it.state == EntityState.ACTIVE }.firstOrNull()

    private fun stage(): Int = Quest.stage(bot)
    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive
    private fun has(id: Int): Boolean = bot.inventory.contains(id)

    private suspend fun await(timeout: Long = 12_000L, done: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live()) {
            if (done()) return@withTimeoutOrNull true
            delay(150)
        }
        false
    } == true
}
