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
import game.content.castleWars.CastleWars
import game.content.castleWars.CastleWarsGame
import game.content.castleWars.CastleWarsMatch.Team
import game.content.castleWars.CastleWarsRoutes
import game.content.castleWars.CastleWarsRewards
import game.content.castleWars.CastleWarsRewardInterface
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Plays one Castle Wars match using native packets and collision-checked local movement.
 * Carries standards, uses bandages and fights nearby opponents; interruptions abandon membership safely.
 * Each match retains a personality-selected runner, defender, medic, engineer or raider role. Native supply tables,
 * ally bandages, repairs, barricades, catapults, ropes and tunnel actions share the same travel and match lifecycle.
 * Match combat stays in this script, so its ordinary combat reflex cannot accidentally leave the game.
 * After returning, one affordable owned-ticket reward may be redeemed through Lanthus's native exchange.
 *
 * @author lare96
 */
class CastleWarsBotScript(bot: Bot, private var entered: Boolean = false,
                          private var started: Boolean = false,
                          private val deadline: Long = System.currentTimeMillis() + 1_800_000,
                          private var role: Role? = null,
                          private var rewardAttempted: Boolean = false,
                          private var waitingUntil: Long = System.currentTimeMillis() + 480_000) : BotScript(bot) {

    /**
     * A saved single-match purpose; role changes never discard a carried standard or interrupt a native fight.
     *
     * @author lare96
     */
    enum class Role { RUNNER, DEFENDER, MEDIC, ENGINEER, RAIDER }

    companion object {
        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness() &&
            !bot.preferences.isSkiller && !bot.preferences.scavenging.isIntroductory &&
            bot.controllers.primary.javaClass == PlayerController::class.java &&
            bot.equipment.occupied(Equipment.WEAPON) &&
            !bot.combat.weapon.isRanged &&
            BotCombatActionHandler.allowedStyles(bot, listOf(bot.combat.styleDef)).isNotEmpty()

        /**
         * Chooses a staircase within the current tower before crossing to the destination tower.
         * This uses the content's verified route table rather than independently teleporting a bot.
         */
        internal fun nextStair(position: Position, destination: Position): Pair<Int, Position>? {
            if (position.z == 0 && destination.z == 0) return null
            val sameTower = position.computeLongestDistance(Position(destination.x, destination.y, position.z)) < 25
            val up = position.z == 0 || sameTower && position.z < destination.z
            if (sameTower && position.z == destination.z) return null
            return CastleWarsRoutes.stairs.entries.filter { (key, landing) ->
                key.second.z == position.z && if (up) landing.z > position.z else landing.z < position.z
            }.minByOrNull { (key, _) ->
                val target = if (position.z == 0) destination else position
                key.second.computeLongestDistance(Position(target.x, target.y, position.z))
            }?.key
        }
    }

    /**
     * Persists the single-match attempt and its absolute deadline; restarting cannot grant a second attempt.
     *
     * @author lare96
     */
    class MatchData : BotScriptData() {
        var entered = false
        var started = false
        var deadline = 0L
        /**
         * Fixed purpose selected once for this saved match.
         */
        var role = ""
        /**
         * Prevents a restored completion path from spending tickets a second time.
         */
        var rewardAttempted = false
        /**
         * Absolute waiting-room deadline, retained independently of the full attempt deadline.
         */
        var waitingUntil = 0L
        override fun load(data: JsonObject) {
            entered = data.get("entered").asBoolean
            started = data.get("started").asBoolean
            deadline = data.get("deadline").asLong
            role = data.get("role")?.asString ?: ""
            rewardAttempted = data.get("rewardAttempted")?.asBoolean ?: false
            waitingUntil = data.get("waitingUntil")?.asLong ?: minOf(deadline, System.currentTimeMillis() + 480_000)
        }
        override fun save(data: JsonObject) {
            data.addProperty("entered", entered)
            data.addProperty("started", started)
            data.addProperty("deadline", deadline)
            data.addProperty("role", role)
            data.addProperty("rewardAttempted", rewardAttempted)
            data.addProperty("waitingUntil", waitingUntil)
        }
    }

    constructor(bot: Bot, data: MatchData) : this(bot, data.entered, data.started, data.deadline,
        Role.entries.firstOrNull { it.name == data.role }, data.rewardAttempted, data.waitingUntil)

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse get() = !CastleWars.game.match.isPlaying(bot)
    private var failures = 0

    /**
     * Local retry state belongs to this match; native actions own all supplies, damage and rewards.
     */
    private val support = CastleWarsBotSupport(bot) {
        isActionSafe() && CastleWars.game.match.isPlaying(bot) && System.currentTimeMillis() < deadline
    }

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline) return true
        if (role == null) role = CastleWarsBotSupport.chooseRole(bot)
        if (entered) return CastleWars.game.match.team(bot) == null
        if (!eligible(bot)) return true
        val banked = withTimeoutOrNull(180_000) {
            if (!handler.banking.travelToBankDepositAll()) return@withTimeoutOrNull false
            handler.widgets.clickCloseInterface() && handler.equipment.unequip(Equipment.HEAD) &&
                handler.equipment.unequip(Equipment.CAPE) && handler.banking.travelToBankOpen() &&
                handler.banking.depositInventory() && handler.widgets.clickCloseInterface()
        } ?: false
        if (!banked) return true
        // The walk from the bank can take longer than the banking steps, so it gets its own limit.
        return withTimeoutOrNull(900_000) {
            if (!handler.webWalkTo(CastleWarsGame.LOBBY, radius = 3)) return@withTimeoutOrNull true
            val portal = world.locator.findObjects(CastleWarsGame.LOBBY, 16) { it.id == 4408 }.firstOrNull()
                ?: return@withTimeoutOrNull true
            entered = true
            if (!handler.interactions.interact(1, portal)) return@withTimeoutOrNull true
            delay(2.seconds)
            waitingUntil = System.currentTimeMillis() + 480_000
            CastleWars.game.match.team(bot) == null
        } ?: true
    }

    override suspend fun run(): Boolean {
        val match = CastleWars.game.match
        val team = match.team(bot) ?: return true
        if (System.currentTimeMillis() >= deadline) return true
        if (match.isWaiting(bot)) {
            if (started || System.currentTimeMillis() >= waitingUntil) return true
            delay(3.seconds)
            return false
        }
        started = true
        if (!isActionSafe()) return false
        if (bot.health < bot.hitpoints.staticLevel - 6 && bot.inventory.contains(4049)) {
            handler.inventory.clickItem(1, 4049)
            delay(1.seconds)
            return false
        }
        val objects = world.locator.findObjects(Position(2400, if (bot.position.y >= 6400) 9504 else 3104, bot.position.z), 64) { true }.toList()
        val selectedRole = role ?: Role.RUNNER
        if (support.supplies(selectedRole, team, objects)) { delay(1.seconds); return false }
        if (CastleWarsGame.inSpawnRoom(bot.position)) {
            val trapdoor = objects.filter { it.id == if (team == Team.SARADOMIN) 4471 else 4472 }
                .minByOrNull { it.position.computeLongestDistance(bot.position) }
            if (trapdoor != null && click(trapdoor)) return false
        }

        val carried = match.carried(bot)
        if (carried == null && support.act(selectedRole, team, objects)) { failures = 0; delay(1.seconds); return false }
        if (carried == null && !bot.equipment.occupied(Equipment.WEAPON)) {
            bot.inventory.filterNotNull().firstOrNull {
                it.id !in CastleWarsGame.TOOLS && it.equipDef?.index == Equipment.WEAPON
            }?.let { handler.equipment.equip(it.id) }
        }
        val enemyFlag = match.flag(team.enemy)
        val roleDestination = support.destination(selectedRole, team)
        val destination = when {
            carried != null -> team.home
            roleDestination != null -> roleDestination
            enemyFlag.position != null -> enemyFlag.position
            else -> team.enemy.home
        }
        if (carried == null) {
            val opponent = match.players().keys.filter { CastleWars.game.canFight(bot, it) &&
                it.position.isWithinDistance(bot.position, 1) }.minByOrNull { it.health }
            if (opponent != null && BotCombatActionHandler.allowedStyles(bot, listOf(bot.combat.styleDef)).isNotEmpty()) {
                handler.interactions.interact(PlayerContextMenuOption.ATTACK.index, opponent) {
                    CastleWars.game.canFight(bot, opponent) && match.carried(bot) == null &&
                        System.currentTimeMillis() < deadline
                }
                delay(2.seconds)
                return false
            }
        }
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        val actualDestination = destination ?: team.enemy.home
        val stair = nextStair(bot.position, actualDestination)
        val target = if (stair != null) objects.firstOrNull { it.id == stair.first && it.position == stair.second }
        else objects.firstOrNull { it.position == actualDestination && it.id in setOf(4377, 4378, 4900, 4901, 4902, 4903) }
        if (target != null && click(target)) {
            failures = 0
        } else {
            // A closed castle gate can block the direct route. Work on the nearest door.
            val door = world.locator.findObjects(bot.position, 12) {
                it.id in setOf(4423, 4424, 4427, 4428, 4465, 4467)
            }.minByOrNull { it.position.computeLongestDistance(bot.position) }
            val ownDoor = door != null && (door.id in setOf(4423, 4424, 4465)) == (team == Team.SARADOMIN)
            if (door != null && click(door, if (ownDoor) 1 else 2)) failures = 0
            else {
                val before = bot.position
                val walked = withTimeoutOrNull(12_000) { handler.webWalkTo(actualDestination, radius = 2, bank = BankAccess.NONE) } == true
                failures = if (walked || bot.position != before) 0 else failures + 1
            }
        }
        delay(2.seconds)
        return failures >= 20
    }

    private suspend fun click(obj: GameObject, option: Int = 1): Boolean {
        val interacted = withTimeoutOrNull(12_000) {
            handler.interactions.interact(option, obj) {
                CastleWars.game.match.isPlaying(bot) && !bot.status.isImmobilized() &&
                    isActionSafe() && System.currentTimeMillis() < deadline
            }
        } == true
        if (interacted) delay(1.seconds)
        return interacted
    }

    override fun paused() {
        if (CastleWars.game.match.team(bot) != null) CastleWars.game.exit(bot)
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() { paused() }

    override suspend fun completed() { redeem() }

    /**
     * Reserves one unowned equippable reward goal, letting intelligent bots save for higher tiers.
     * The native chest and Lanthus exchange handle every ticket withdrawal, cost and inventory-space check.
     */
    private suspend fun redeem() {
        if (rewardAttempted || !isActionSafe(ActionSafety.NON_COMBAT) || CastleWars.game.match.team(bot) != null ||
            !bot.position.isWithinDistance(CastleWarsGame.LOBBY, 16)) return
        rewardAttempted = true
        val rewards = CastleWarsRewards.prices.entries.filter {
            !bot.itemTracker.contains(it.key) && Item(it.key).equipDef?.meetsAllRequirements(bot) == true
        }
        val desired = rewards.filter { bot.preferences.needsWantedItem(it.key) }.minByOrNull { it.value }
            ?: (if (bot.personality.isIntelligent) rewards.maxByOrNull { it.value } else rewards.minByOrNull { it.value })
            ?: return
        val id = desired.key
        val cost = desired.value
        if (bot.itemTracker.count(4067) < cost) return
        var ownedShop: CastleWarsRewardInterface? = null
        try {
            withTimeoutOrNull(60_000) {
                if (bot.inventory.computeAmountForId(4067) < cost || !bot.inventory.hasSpaceFor(Item(id))) {
                    val chest = world.locator.findObjects(CastleWarsGame.LOBBY, 16) { it.id == 4483 }.firstOrNull()
                        ?: return@withTimeoutOrNull
                    if (!handler.interactions.interact(1, chest) { isActionSafe(ActionSafety.NON_COMBAT) } ||
                        !waitFor(3.seconds) { bot.bank.isOpen } || !handler.banking.depositInventory(setOf(4067))) return@withTimeoutOrNull
                    handler.banking.clickBankingMode(false)
                    val missing = cost - bot.inventory.computeAmountForId(4067)
                    if (missing > 0 && !handler.banking.withdraw(Item(4067, missing))) return@withTimeoutOrNull
                    if (!handler.widgets.clickCloseInterface()) return@withTimeoutOrNull
                }
                if (!isActionSafe(ActionSafety.NON_COMBAT) || bot.inventory.computeAmountForId(4067) < cost) return@withTimeoutOrNull
                val lanthus = world.locator.findNpcs(CastleWarsGame.LOBBY, 16) { it.id == 1526 }.firstOrNull()
                    ?: return@withTimeoutOrNull
                if (!handler.interactions.interact(2, lanthus) { isActionSafe(ActionSafety.NON_COMBAT) } ||
                    !waitFor(3.seconds) { bot.overlays.has(CastleWarsRewardInterface::class.java) }) return@withTimeoutOrNull
                ownedShop = bot.overlays.getOverlay(CastleWarsRewardInterface::class.java)
                val before = bot.inventory.computeAmountForId(id)
                val tickets = bot.inventory.computeAmountForId(4067)
                if (!isActionSafe(ActionSafety.NON_COMBAT) || bot.overlays.getOverlay(CastleWarsRewardInterface::class.java) !== ownedShop)
                    return@withTimeoutOrNull
                output.sendItemWidgetClick(2, CastleWarsRewards.prices.keys.indexOf(id), 12392, id)
                waitFor(3.seconds) { bot.inventory.computeAmountForId(id) > before && bot.inventory.computeAmountForId(4067) == tickets - cost }
            }
        } finally {
            if (ownedShop != null && bot.overlays.getOverlay(CastleWarsRewardInterface::class.java) === ownedShop)
                bot.overlays.closeWindows(false)
        }
    }

    override fun allowsLoot(item: GroundItem): Boolean = false
    override fun snapshot() = MatchData().also {
        it.entered = entered; it.started = started; it.deadline = deadline; it.role = role?.name ?: ""
        it.rewardAttempted = rewardAttempted; it.waitingUntil = waitingUntil
    }
}
