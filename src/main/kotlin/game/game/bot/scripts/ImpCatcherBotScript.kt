package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import com.google.gson.JsonObject
import game.content.impCatcher.ImpCatcher as Quest
import game.content.impCatcher.ImpCatcherDialogue as Dialogue
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Follows Mizgog's request using native dialogue, ordinary Imp drops and a final conversation.
 *
 * @author lare96
 */
class ImpCatcherBotScript(bot: Bot, private val data: ImpCatcherData = ImpCatcherData()) : BotScript(bot) {
    companion object {
        private const val ATTEMPT_MS = 25 * 60_000L
        private const val RETRY_MS = 30 * 60_000L
        private const val IMP = 708
        private val tower = Position(3103, 3163, 2)
        private val impCamp = Position(3011, 3312)
        private var Bot.impCatcherRetryAt by Attr.long().persist("imp_catcher_bot_retry_at")

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            if (stage == Quest.COMPLETE || bot.state != EntityState.ACTIVE || !ActionSafety.NON_COMBAT.allows(bot) ||
                bot.overlays.hasWindow() || bot.controllers.primary.javaClass != PlayerController::class.java ||
                System.currentTimeMillis() < bot.impCatcherRetryAt ||
                BotCombatActionHandler.limitExperience(bot, bot.skill(Skill.MAGIC), Quest.MAGIC_XP) < Quest.MAGIC_XP) return false

            if (stage == 0 && (bot.y >= 4000 || bot.position.computeLongestDistance(tower) > 85)) return false
            return Quest.missingBeads(bot).all { bot.bank.contains(it) } ||
                bot.equipment[Equipment.WEAPON] != null && !bot.combat.weapon.isRanged && !bot.combat.magic.isCasting &&
                bot.skill(Skill.ATTACK).staticLevel >= 15 && bot.skill(Skill.STRENGTH).staticLevel >= 15 &&
                bot.combat.styleDef.exp.all { BotCombatActionHandler.mayGainExperience(bot, it) }
        }
    }

    /**
     * Preserves the deadline across saved sessions while quest stages and carried beads record the actual progress.
     *
     * @author lare96
     */
    class ImpCatcherData : BotScriptData() {
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

    override fun allowsLoot(item: GroundItem) = item.id in Quest.beads && !bot.inventory.contains(item.id)

    override fun snapshot() = data

    override suspend fun run(): Boolean {
        if (Quest.stage(bot) == Quest.COMPLETE) return true
        var objective = "ask Wizard Mizgog for a quest"
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            if (Quest.stage(bot) == 0 && !talk(starting = true)) return@withTimeoutOrNull false
            objective = "collect the missing beads"
            if (!prepareInventory() || !collectBeads()) return@withTimeoutOrNull false
            objective = "return the four beads to Wizard Mizgog"
            talk(starting = false)
        } == true
        if (!finished && Quest.stage(bot) != Quest.COMPLETE) {
            failure = "Could not $objective at ${bot.position}, quest stage ${Quest.stage(bot)}."
            bot.log("Imp Catcher: $failure")
            bot.impCatcherRetryAt = System.currentTimeMillis() + RETRY_MS
        }
        return true
    }

    private suspend fun talk(starting: Boolean): Boolean {
        val targetStage = if (starting) Quest.STARTED else Quest.COMPLETE
        val responses = if (starting) listOf(Dialogue.ASK_FOR_QUEST, Dialogue.ASK_POLITELY) else emptyList()
        val playerSpeech = if (starting) setOf(Dialogue.ACCEPT) else setOf(Dialogue.ALL_BEADS)
        repeat(2) {
            if (Quest.stage(bot) >= targetStage) return true
            val mizgog = bot.world.locator.findNpcs(tower, 10) { it.id == Quest.MIZGOG && it.isAlive }
                .firstOrNull() ?: return false
            if (handler.interactions.approach(mizgog, timeout = 230.seconds, allowOutOfView = true, canContinue = ::live) &&
                BotNpcDialogues.respond(bot, mizgog, responses, timeout = 45.seconds, allowed = ::live,
                    completed = { Quest.stage(bot) >= targetStage }, playerSpeech = playerSpeech, advanceSpeech = true) {
                    handler.interactions.interact(1, mizgog, ::live)
                }) return true
        }
        return false
    }

    private suspend fun prepareInventory(): Boolean {
        val missing = Quest.missingBeads(bot)
        if (missing.isEmpty()) return true
        val banked = missing.filter { bot.bank.contains(it) }
        if (banked.isEmpty() && bot.inventory.computeRemainingSize() >= missing.size) return true
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!handler.banking.open(bank)) return false
        try {
            for (item in bot.inventory.filterNotNull().filter { it.id !in Quest.beads }) {
                if (bot.inventory.computeRemainingSize() >= missing.size) break
                if (!handler.banking.deposit(item)) return false
            }
            return bot.inventory.computeRemainingSize() >= missing.size &&
                banked.all { handler.banking.withdrawUnnoted(Item(it), ::live) }
        } finally {
            if (bot.bank.isOpen) bot.overlays.closeWindows()
        }
    }

    private suspend fun collectBeads(): Boolean {
        if (Quest.missingBeads(bot).isEmpty()) return true
        if (!handler.webWalkTo(impCamp, radius = 6, attempts = 2, fallback = false)) return false
        while (live()) {
            val missing = Quest.missingBeads(bot)
            if (missing.isEmpty()) return true
            if (bot.healthPercent <= 45 && !handler.inventory.eatAnyFood()) return false
            val drop = bot.world.locator.findItems(bot, 28, true) { it.id in missing && handler.loot.canPickup(it) }
                .firstOrNull()
            if (drop != null && handler.loot.pickup(drop, ::live)) continue

            val imps = bot.world.locator.findNpcs(impCamp, 22) {
                it.id == IMP && it.isAlive && (!it.combat.inCombat() || it.combat.target === bot)
            }.sortedBy { it.position.computeLongestDistance(bot.position) }
            if (imps.none { fight(it) }) {
                if (!bot.position.isWithinDistance(impCamp, 10) &&
                    !handler.webWalkTo(impCamp, radius = 4, attempts = 2, fallback = false)) return false
                delay(600)
            }
        }
        return false
    }

    private suspend fun fight(imp: Npc): Boolean = withTimeoutOrNull(35.seconds) {
        while (live() && imp.isAlive && imp.state == EntityState.ACTIVE) {
            if (bot.healthPercent <= 45 && !handler.inventory.eatAnyFood()) return@withTimeoutOrNull false
            if (bot.combat.target !== imp) {
                val allowed = { live() && imp.isAlive }
                val interactions = handler.interactions
                val inView = imp.isViewableFrom(bot)
                if (!inView && !interactions.approach(imp, interactions.interactionPolicy(3, imp),
                        timeout = 12.seconds, allowOutOfView = true, canContinue = allowed)) return@withTimeoutOrNull false
                if (!interactions.interactLocally(3, imp, timeout = 8.seconds, allowed = allowed)) {
                    return@withTimeoutOrNull false
                }
            }
            delay(300)
        }
        // Give the native death action time to place the loot before selecting another Imp.
        delay(1_800)
        true
    } == true

    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive
}
