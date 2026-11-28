package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import engine.spawn.PersistentGroundItem
import game.content.sheepShearer.SheepShearer as Quest
import game.content.sheepShearer.SheepShearerDialogue as Dialogue
import game.skill.crafting.shearing.SheepShearing
import game.skill.crafting.shearing.SheepShearing.SHEARS
import game.skill.crafting.textileCrafting.MakeTextileActionItem
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Completes Sheep Shearer through Fred's dialogue and the ordinary wool production actions.
 *
 * @author lare96
 */
class SheepShearerBotScript(bot: Bot) : BotScript(bot) {
    companion object {
        private val farm = Position(3189, 3273)
        private val shearsSpawn = Position(3192, 3272)
        private val pen = SubZone.LUMBRIDGE_SHEEP_PEN
        private val spinningRoom = SubZone.LUMBRIDGE_SPINNING_WHEEL
        private val recipe = Textile.BALL_OF_WOOL
        private val wool = recipe.rawItem.id
        private val ball = recipe.processedItem.id
        private const val CASTLE_WHEEL = 2644

        private var Bot.sheepShearerRetryAt by Attr.long().persist("sheep_shearer_bot_retry_at")

        fun eligible(bot: Bot): Boolean {
            val stage = Quest.stage(bot)
            return stage < Quest.COMPLETE && bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) &&
                !bot.overlays.hasWindow() && bot.controllers.primary.javaClass == PlayerController::class.java &&
                System.currentTimeMillis() >= bot.sheepShearerRetryAt &&
                (stage != Quest.NOT_STARTED || bot.position.isWithinDistance(farm, 48))
        }
    }

    /**
     * Restores the script using the player's saved quest varp and inventory.
     *
     * @author lare96
     */
    class SheepShearerData : BotScriptData() {
        override fun load(data: JsonObject) {}
        override fun save(data: JsonObject) {}
    }

    var failure: String? = null
        private set

    override fun snapshot(): SheepShearerData = SheepShearerData()

    override suspend fun run(): Boolean {
        var step = "speak to Fred"
        val finished = withTimeoutOrNull(12.minutes) {
            var failures = 0
            while (live() && Quest.stage(bot) < Quest.COMPLETE) {
                val needed = Quest.ballsRemaining(bot)
                val balls = count(ball)
                val raw = count(wool)
                val full = bot.inventory.isFull
                val progressed = when {
                    Quest.stage(bot) == Quest.NOT_STARTED -> {
                        step = "accept Fred's request"
                        talk(starting = true)
                    }
                    balls >= needed || full && balls > 0 -> {
                        step = "deliver balls of wool to Fred"
                        talk(starting = false)
                    }
                    raw > 0 && (raw + balls >= needed || full) -> {
                        step = "spin wool in Lumbridge Castle"
                        spin(minOf(raw, needed - balls))
                    }
                    full -> {
                        step = "make room for wool"
                        makeRoom()
                    }
                    SHEARS !in bot.inventory -> {
                        step = "pick up the shears at Fred's farm"
                        takeShears()
                    }
                    else -> {
                        step = "shear sheep in Fred's pen"
                        gather(needed - balls)
                    }
                }
                if (progressed) {
                    failures = 0
                } else {
                    if (++failures >= 3) break
                    delay(600)
                }
            }
            Quest.stage(bot) == Quest.COMPLETE
        } == true
        if (!finished) {
            failure = "Could not $step at ${bot.position}, quest stage ${Quest.stage(bot)}."
            bot.log("Sheep Shearer: $failure")
            bot.sheepShearerRetryAt = System.currentTimeMillis() + 15.minutes.inWholeMilliseconds
        }
        return true
    }

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && isActionSafe(ActionSafety.NON_COMBAT)

    private fun count(id: Int): Int = bot.inventory.computeAmountForId(id)

    private suspend fun talk(starting: Boolean): Boolean {
        val previousStage = Quest.stage(bot)
        val needed = Quest.ballsRemaining(bot)
        val finalDelivery = !starting && count(ball) >= needed
        if (!waitFor(15.seconds) { !bot.overlays.hasWindow() || !live() } || !live()) return false
        val fred = bot.world.locator.findNpcs(farm, 12) { it.id == Quest.FRED && it.z == farm.z && it.isAlive }
            .firstOrNull() ?: return false
        if (!handler.interactions.approach(fred, timeout = 90.seconds, allowOutOfView = true, canContinue = ::live)) return false
        if (starting) {
            BotNpcDialogues.respond(bot, fred, listOf(Dialogue.LOOKING_FOR_QUEST, Dialogue.ACCEPT,
                Dialogue.KNOW_SHEARING, Dialogue.KNOW_SPINNING), timeout = 45.seconds, allowed = ::live,
                completed = { Quest.stage(bot) > previousStage && !bot.overlays.hasWindow() }, advanceSpeech = true) {
                handler.interactions.interactLocally(1, fred, allowed = ::live)
            }
            return Quest.stage(bot) > previousStage
        }
        if (Dialogue.hasSeenThing(bot)) {
            if (!BotNpcDialogues.respond(bot, fred, listOf(Dialogue.BACK), allowed = ::live, advanceSpeech = true,
                    completed = {
                        bot.overlays.getOverlay(PlayerDialogue::class.java)?.lines?.joinToString(" ") == Dialogue.BACK
                    }) { handler.interactions.interactLocally(1, fred, allowed = ::live) }) return false
        } else if (!handler.interactions.interactLocally(1, fred, allowed = ::live)) {
            return false
        }
        waitFor(45.seconds) {
            !live() || if (finalDelivery) Quest.stage(bot) == Quest.COMPLETE
            else Quest.stage(bot) > previousStage && !bot.overlays.hasWindow()
        }
        return Quest.stage(bot) > previousStage
    }

    private suspend fun takeShears(): Boolean {
        if (!handler.webWalkTo(farm, radius = 5, attempts = 2, fallback = false) || !live()) return false
        fun target() = bot.world.locator.findItemsOnTile(shearsSpawn) {
            it is PersistentGroundItem && it.id == SHEARS && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
        }.firstOrNull()
        waitFor(35.seconds) { target() != null || !live() }
        val shears = target() ?: return false
        return handler.loot.pickup(shears) { live() && SHEARS !in bot.inventory }
    }

    private suspend fun makeRoom(): Boolean {
        val bank = handler.webWalkToNearestBank(attempts = 2, fallback = false) ?: return false
        if (!live() || !handler.banking.open(bank)) return false
        handler.banking.depositInventory(except = setOf(SHEARS))
        return handler.widgets.clickCloseInterface() && !bot.inventory.isFull
    }

    private suspend fun gather(target: Int): Boolean {
        if (bot.position !in pen.area &&
            !handler.webWalkTo(pen, radius = 1, attempts = 2, fallback = false)) return false
        val initial = count(wool)
        var misses = 0
        while (live() && count(wool) < target && !bot.inventory.isFull && misses < 8) {
            fun sheep() = bot.world.locator.findNpcs(bot.position, 24, true) {
                it.position in pen.area && SheepShearing.canShear(it)
            }
            waitFor(12.seconds) { sheep().isNotEmpty() || !live() }
            val before = count(wool)
            val targets = sheep()
            if (targets.isEmpty()) break
            val next = targets.elementAt(misses % targets.size)
            if (handler.inventory.useItem(SHEARS).onNpc(next) { live() && SheepShearing.canShear(next) }) {
                waitFor(3.seconds) { count(wool) > before || !SheepShearing.canShear(next) || !live() }
            }
            misses = if (count(wool) > before) 0 else misses + 1
        }
        return count(wool) > initial || count(wool) >= target
    }

    private suspend fun spin(amount: Int): Boolean {
        val wheel = bot.world.locator.findObjects(spinningRoom.inside, spinningRoom.area.tileRadius) {
            it.id == CASTLE_WHEEL && it.position in spinningRoom.area
        }.firstOrNull() ?: return false
        if (!handler.interactions.approach(wheel, timeout = 90.seconds, allowOutOfView = true, canContinue = ::live)) return false
        val before = count(ball)
        if (!handler.inventory.useItem(wool).onObject(wheel, ::live) ||
            !waitFor(5.seconds) { bot.overlays.has(MakeItemDialogue::class.java) } || !live()) return false
        handler.widgets.clickMakeItem(0, amount)
        waitFor(45.seconds) {
            count(ball) >= before + amount || !live() ||
                count(ball) > before && bot.actions.first(MakeTextileActionItem::class.java) == null
        }
        return count(ball) > before
    }
}
