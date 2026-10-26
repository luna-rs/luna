package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.pestControl.PestControl
import game.content.pestControl.PestControl.commendations
import game.content.pestControl.PestControl.unclaimedPestCoins
import game.content.pestControl.PestControlController
import game.content.pestControl.PestControlTraining
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Takes one Pest Control voyage using native sailing, boarding, attacks, repairs and training buttons.
 * Melee volunteers protect the knight, kill healing Spinners and destroy portals; deaths remain in the match.
 *
 * @author lare96
 */
class PestControlBotScript(bot: Bot, private var boarded: Boolean = false,
                           private var started: Boolean = false,
                           private var waitingUntil: Long = 0,
                           private val deadline: Long = System.currentTimeMillis() + 2_100_000) : BotScript(bot) {
    companion object {
        private val CLOSED_GATES = setOf(14233, 14235, 14237, 14239, 14241, 14243)
        private val DAMAGED_DEFENCES = (14216..14221).toSet() + (14227..14232) + (14237..14248)

        private fun meleeReady(bot: Bot): Boolean = !bot.preferences.isSkiller &&
            !bot.preferences.scavenging.isIntroductory && bot.equipment.occupied(Equipment.WEAPON) &&
            !bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting &&
            bot.combat.magic.selectedSpell == CombatSpellDefinition.NONE &&
            BotCombatActionHandler.allowedStyles(bot, listOf(bot.combat.styleDef)).isNotEmpty()

        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) &&
            bot.state == EntityState.ACTIVE && bot.combatLevel >= 40 && !bot.inWilderness() && meleeReady(bot) &&
            bot.controllers.primary.javaClass == PlayerController::class.java && !bot.overlays.hasWindow()
    }

    /**
     * Persists the single boarding attempt and absolute deadlines across pauses and saved sessions.
     *
     * @author lare96
     */
    class PestControlData : BotScriptData() {
        var boarded = false
        var started = false
        var waitingUntil = 0L
        var deadline = 0L

        override fun load(data: JsonObject) {
            boarded = data.get("boarded").asBoolean
            started = data.get("started").asBoolean
            waitingUntil = data.get("waitingUntil").asLong
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("boarded", boarded)
            data.addProperty("started", started)
            data.addProperty("waitingUntil", waitingUntil)
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: PestControlData) : this(bot, data.boarded, data.started, data.waitingUntil, data.deadline)

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse: Boolean get() = !member()
    private var cleaned = false
    private var failures = 0
    private var watched: Npc? = null
    private var watchedHealth = -1
    private var progressAt = 0L
    private val gateRetries = mutableMapOf<Position, Long>()

    private fun member(): Boolean = PestControl.round.playing(bot) || bot in PestControl.round.waiting()
    private fun fighting(): Boolean = !cleaned && System.currentTimeMillis() < deadline && isActionSafe() &&
        PestControl.round.playing(bot) && PestControl.onIsland(bot.position) && meleeReady(bot)

    override suspend fun init(resumed: Boolean): Boolean {
        if (cleaned || System.currentTimeMillis() >= deadline) return true
        if (boarded) return !member()
        if (!eligible(bot)) return true
        return withTimeoutOrNull(180_000) {
            if (!PestControl.atOutpost(bot.position)) {
                fun owned(id: Int) = bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)
                val supplies = mutableListOf<Item>()
                val logs = owned(1511).coerceAtMost(12).toInt()
                if (owned(2347) > 0 && logs > 0) supplies += listOf(Item(2347), Item(1511, logs))
                Food.ID_TO_FOOD.entries.filter { it.value.heal > 0 && owned(it.key) > 0 }
                    .maxByOrNull { it.value.heal }?.let { supplies += Item(it.key, owned(it.key).coerceAtMost(8).toInt()) }
                if (!handler.banking.travelToBankDepositAll()) return@withTimeoutOrNull true
                if (supplies.isNotEmpty() && !handler.retrieveAll(supplies))
                    return@withTimeoutOrNull true
                if (!handler.widgets.clickCloseInterface() ||
                    !handler.webWalkTo(PestControl.SARIM, radius = 3)) return@withTimeoutOrNull true
                val squire = world.locator.findNpcs(PestControl.SARIM, 12) { it.id == 3801 }.firstOrNull()
                    ?: return@withTimeoutOrNull true
                if (!handler.interactions.interact(1, squire) { eligible(bot) } ||
                    !waitFor(10.seconds) { OptionDialogue::class in bot.overlays } ||
                    !isActionSafe() || !handler.widgets.clickDialogueOption("Take me to the outpost.") ||
                    !waitFor(10.seconds) { PestControl.atOutpost(bot.position) }) return@withTimeoutOrNull true
            }
            // Spend saved points before another win can encounter the original fifteen-point cap.
            redeem()
            if (!eligible(bot) || !handler.webWalkTo(PestControl.DOCK, radius = 2, bank = BankAccess.NONE))
                return@withTimeoutOrNull true
            val plank = world.locator.findObjects(PestControl.DOCK, 8) { it.id == 14315 }.firstOrNull()
                ?: return@withTimeoutOrNull true
            boarded = true
            waitingUntil = System.currentTimeMillis() + 480_000
            // Sailing/boarding moves the player away from the click target, so membership is the acknowledgement.
            handler.interactions.interact(1, plank) { eligible(bot) && System.currentTimeMillis() < deadline }
            !waitFor(5.seconds) { member() }
        } ?: true
    }

    override suspend fun run(): Boolean {
        if (cleaned || System.currentTimeMillis() >= deadline) return true
        if (bot in PestControl.round.waiting()) {
            if (started || System.currentTimeMillis() >= waitingUntil) return true
            delay(3.seconds)
            return false
        }
        if (!PestControl.round.playing(bot)) return true
        started = true
        if (!meleeReady(bot)) return true
        if (!fighting()) { delay(1.seconds); return false }
        if (bot.emotions.isNervousAboutHp) {
            val food = bot.inventory.withIndex().firstOrNull { it.value?.id in Food.ID_TO_FOOD }
            if (food?.value != null) {
                handler.inventory.eat(food.index, food.value!!.id)
                return false
            }
        }
        if (repair()) return false
        if (!fighting()) return false

        val pests = world.locator.findNpcs(PestControl.KNIGHT, 40) { PestControl.canAttack(bot, it) }
        val knight = world.locator.findNpcs(PestControl.KNIGHT, 3) { it.id == 3782 && PestControl.owns(it) }.firstOrNull()
        val threat = if (knight != null && knight.health <= 100) pests.filter {
            it.id < 3777 && it.position.isWithinDistance(PestControl.KNIGHT, 6)
        }.minByOrNull { it.position.computeLongestDistance(bot.position) } else null
        // Spread volunteers between portals while retaining the same assignment until a portal dies.
        val portal = pests.filter { it.id in 3777..3780 }
            .minByOrNull { (it.id - 3777 - bot.index % 4 + 4) % 4 }
        val spinner = pests.filter { it.id in 3747..3751 &&
            (it.position.isWithinDistance(bot.position, 8) ||
                portal != null && it.position.isWithinDistance(portal.position, 8))
        }.minByOrNull { it.position.computeLongestDistance(bot.position) }
        val target = threat ?: spinner ?: portal ?: pests.minByOrNull { it.position.computeLongestDistance(bot.position) }
        if (target == null) { delay(2.seconds); return false }

        val now = System.currentTimeMillis()
        if (watched !== target || watchedHealth != target.health) {
            watched = target
            watchedHealth = target.health
            progressAt = now
            failures = 0
        }
        if (bot.combat.target === target && now - progressAt < 12_000) {
            delay(2.seconds)
            return false
        }
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        val before = bot.position
        val attacked = withTimeoutOrNull(20_000) {
            val allowed = { fighting() && PestControl.canAttack(bot, target) }
            handler.interactions.approach(target, allowOutOfView = true, canContinue = allowed) &&
                handler.interactions.interact(3, target, allowed)
        } == true
        if (attacked) {
            progressAt = System.currentTimeMillis()
            failures = 0
        } else if (fighting() && PestControl.canAttack(bot, target)) {
            failures = if (bot.position != before) 0 else failures + 1
            if (openGate(target)) failures = 0
        }
        delay(2.seconds)
        return failures >= 8
    }

    /**
     * Repairs only visibly damaged sections with carried supplies, verifying that a log was consumed.
     */
    private suspend fun repair(): Boolean {
        if (!bot.inventory.contains(2347) || !bot.inventory.contains(1511)) return false
        val damaged = world.locator.findObjects(bot.position, 6) { it.id in DAMAGED_DEFENCES }
            .minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false
        val logs = bot.inventory.computeAmountForId(1511)
        val repaired = withTimeoutOrNull(8_000) {
            handler.interactions.interact(3, damaged) { fighting() } &&
                waitFor(3.seconds) { bot.inventory.computeAmountForId(1511) < logs }
        } == true
        if (repaired) delay(3.seconds)
        return repaired
    }

    /**
     * Approaches a closed gate on the route when the direct path is blocked, including from the respawn boat.
     * Damaged open leaves are never toggled shut.
     */
    private suspend fun openGate(target: Npc): Boolean {
        val now = System.currentTimeMillis()
        val gate = world.locator.findObjects(PestControl.KNIGHT, 40) {
            it.id in CLOSED_GATES && now >= (gateRetries[it.position] ?: 0)
        }
            .minByOrNull { it.position.computeLongestDistance(bot.position) +
                it.position.computeLongestDistance(target.position) } ?: return false
        gateRetries[gate.position] = now + 30_000
        return withTimeoutOrNull(20_000) {
            handler.interactions.interact(1, gate) { fighting() } &&
                waitFor(3.seconds) { gate.state != EntityState.ACTIVE }
        } == true
    }

    /**
     * Redeems earned points through the Void Knight interface, rechecking the account build for every purchase.
     */
    private suspend fun redeem() {
        if (!PestControl.atOutpost(bot.position) || member() || !isActionSafe(ActionSafety.NON_COMBAT) ||
            bot.commendations <= 0 && bot.unclaimedPestCoins <= 0) return
        withTimeoutOrNull(60_000) {
            if (bot.unclaimedPestCoins > 0 && bot.inventory.isFull &&
                (!handler.banking.travelToBankDepositAll() || !handler.widgets.clickCloseInterface()))
                return@withTimeoutOrNull
            // Knights wander around the outpost; approach the village before requiring a viewable NPC.
            if (!handler.webWalkTo(PestControl.OUTPOST.translate(0, -15), radius = 4, bank = BankAccess.NONE))
                return@withTimeoutOrNull
            val knight = world.locator.findNpcs(bot.position, 16) { it.id in 3786..3789 }
                .minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return@withTimeoutOrNull
            if (!handler.interactions.interact(1, knight) { PestControl.atOutpost(bot.position) && isActionSafe() } ||
                !waitFor(5.seconds) { PestControlTraining::class in bot.overlays }) return@withTimeoutOrNull
            repeat(bot.commendations.coerceIn(0, 15)) {
                val skill = PestControlTraining.SKILLS.filter {
                    bot.skill(it).staticLevel >= 25 && BotCombatActionHandler.wantsToTrain(bot, it)
                }.minByOrNull { bot.skill(it).staticLevel } ?: return@withTimeoutOrNull
                if (!isActionSafe() || !PestControl.atOutpost(bot.position) ||
                    !handler.widgets.clickButton(18691, 18760 + PestControlTraining.SKILLS.indexOf(skill)))
                    return@withTimeoutOrNull
                delay(1.seconds)
                val points = bot.commendations
                if (!isActionSafe() || !BotCombatActionHandler.wantsToTrain(bot, skill) ||
                    !handler.widgets.clickButton(18691, 18779) ||
                    !waitFor(5.seconds) { bot.commendations < points }) return@withTimeoutOrNull
            }
        }
        handler.widgets.clickCloseInterface()
    }

    override fun paused() {
        if (cleaned) return
        cleaned = true
        if (member() || bot.controllers.primary is PestControlController) PestControl.leave(bot)
        if (PestControlTraining::class in bot.overlays) bot.overlays.closeWindows()
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() { paused() }

    override suspend fun completed() {
        if (!bot.isAlive && bot.controllers.primary is PestControlController) {
            waitFor(10.seconds) { bot.isAlive && bot.controllers.primary !is PestControlController }
        }
        if (!PestControl.atOutpost(bot.position) || !isActionSafe(ActionSafety.NON_COMBAT)) return
        redeem()
        withTimeoutOrNull(30_000) {
            if (!handler.widgets.clickCloseInterface() ||
                !handler.webWalkTo(PestControl.OUTPOST, radius = 3, bank = BankAccess.NONE)) return@withTimeoutOrNull
            val squire = world.locator.findNpcs(PestControl.OUTPOST, 12) { it.id == 3800 }.firstOrNull()
                ?: return@withTimeoutOrNull
            handler.interactions.interact(1, squire) {
                PestControl.atOutpost(bot.position) && isActionSafe(ActionSafety.NON_COMBAT) &&
                    bot.controllers.primary.javaClass == PlayerController::class.java
            }
            waitFor(5.seconds) { bot.position.isWithinDistance(PestControl.SARIM, 8) }
        }
    }

    override fun allowsLoot(item: GroundItem): Boolean = false

    override fun snapshot() = PestControlData().also {
        it.boarded = boarded
        it.started = started
        it.waitingUntil = waitingUntil
        it.deadline = deadline
    }
}
