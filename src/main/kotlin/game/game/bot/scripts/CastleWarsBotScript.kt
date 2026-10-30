package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.castleWars.CastleWars
import game.content.castleWars.CastleWarsGame
import game.content.castleWars.CastleWarsMatch.Team
import game.content.castleWars.CastleWarsRoutes
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Plays one Castle Wars match using native packets and collision-checked local movement.
 * Carries standards, uses bandages and fights nearby opponents; interruptions abandon membership safely.
 *
 * @author lare96
 */
class CastleWarsBotScript(bot: Bot, private var entered: Boolean = false,
                          private var started: Boolean = false,
                          private val deadline: Long = System.currentTimeMillis() + 1_800_000) : BotScript(bot) {
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
        override fun load(data: JsonObject) {
            entered = data.get("entered").asBoolean
            started = data.get("started").asBoolean
            deadline = data.get("deadline").asLong
        }
        override fun save(data: JsonObject) {
            data.addProperty("entered", entered)
            data.addProperty("started", started)
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: MatchData) : this(bot, data.entered, data.started, data.deadline)

    override val actionSafety = ActionSafety.COMBAT
    private var failures = 0
    private var waitingUntil = System.currentTimeMillis() + 480_000

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline) return true
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
        val objects = world.locator.findObjects(Position(2400, 3104, bot.position.z), 64) { true }
        if (CastleWarsGame.inSpawnRoom(bot.position)) {
            if (bot.inventory.computeAmountForId(4049) < 6 && bot.inventory.computeRemainingSize() > 2) {
                objects.filter { it.id == 4458 }.minByOrNull { it.position.computeLongestDistance(bot.position) }
                    ?.let { if (click(it)) return false }
            }
            val trapdoor = objects.filter { it.id == if (team == Team.SARADOMIN) 4471 else 4472 }
                .minByOrNull { it.position.computeLongestDistance(bot.position) }
            if (trapdoor != null && click(trapdoor)) return false
        }

        val carried = match.carried(bot)
        if (carried == null && !bot.equipment.occupied(Equipment.WEAPON)) {
            bot.inventory.filterNotNull().firstOrNull {
                it.id !in CastleWarsGame.TOOLS && it.equipDef?.index == Equipment.WEAPON
            }?.let { handler.equipment.equip(it.id) }
        }
        val enemyFlag = match.flag(team.enemy)
        val destination = when {
            carried != null -> team.home
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
        val stair = nextStair(bot.position, destination)
        val target = if (stair != null) objects.firstOrNull { it.id == stair.first && it.position == stair.second }
        else objects.firstOrNull { it.position == destination && it.id in setOf(4377, 4378, 4900, 4901, 4902, 4903) }
        if (target != null && click(target)) {
            failures = 0
        } else {
            // A closed castle gate can block the direct route. Work on the nearest door.
            val door = world.locator.findObjects(bot.position, 12) {
                it.id in setOf(4423, 4424, 4427, 4428, 4465, 4467)
            }.minByOrNull { it.position.computeLongestDistance(bot.position) }
            val ownDoor = door != null && (door.id in setOf(4423, 4424, 4465)) == (team == Team.SARADOMIN)
            if (door == null || !click(door, if (ownDoor) 1 else 2)) failures++
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
    override fun allowsLoot(item: GroundItem): Boolean = false
    override fun snapshot() = MatchData().also { it.entered = entered; it.started = started; it.deadline = deadline }
}
