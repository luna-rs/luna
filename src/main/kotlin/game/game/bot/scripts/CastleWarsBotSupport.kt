package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.predef.*
import game.bot.scripts.CastleWarsBotScript.Role
import game.content.castleWars.CastleWars
import game.content.castleWars.CastleWarsGame
import game.content.castleWars.CastleWarsMatch.Team
import game.content.castleWars.CastleWarsSiege
import game.content.castleWars.CatapultInterface
import game.skill.mining.Pickaxe
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Native Castle Wars support actions shared by the existing single-match bot script.
 * Supplies come only from actual tables and visible ground spawns. Team and carried-standard checks survive approaches;
 * deliberate fire and collapses exclude teammates from their current native blast footprints.
 * Each target has a short retry delay and every action has a deadline. Match rules, costs and damage remain native.
 *
 * @property bot The current match participant.
 * @property playing Live match, lifecycle and deadline permission supplied by the owning script.
 * @author lare96
 */
internal class CastleWarsBotSupport(private val bot: Bot, private val playing: () -> Boolean) {
    companion object {
        private val CATAPULTS = setOf(4381, 4382, 4385, 4386, 4904, 4905)

        /**
         * Gives kind bots a healing role, adept bots machinery, bold dexterous bots raids and hesitant bots defence.
         */
        fun chooseRole(bot: Bot): Role = when {
            bot.personality.isKind -> Role.MEDIC
            bot.personality.isIntelligent -> Role.ENGINEER
            bot.personality.isConfident && bot.personality.isDextrous -> Role.RAIDER
            bot.personality.isUncertain -> Role.DEFENDER
            else -> Role.RUNNER
        }

        /**
         * Native catapult variants retain their original team while damaged or burning.
         */
        private fun catapultTeam(id: Int): Team = if (id in setOf(4382, 4386, 4905)) Team.SARADOMIN else Team.ZAMORAK

        /**
         * Ground-floor catapult anchors verified in the selected 377 cache, including their replacement variants.
         */
        private fun catapult(team: Team) = if (team == Team.SARADOMIN) Position(2413, 3088) else Position(2384, 3117)
        private fun tunnel(team: Team, underground: Boolean) = if (team == Team.SARADOMIN)
            Position(2430, if (underground) 9482 else 3082) else Position(2369, if (underground) 9525 else 3125)
    }

    /**
     * Failed and successful interactions are spaced without mutating the native siege cooldowns.
     */
    private val retryAt = mutableMapOf<Any, Long>()
    private var nextActionAt = 0L

    /**
     * Rechecks the owning match and leaves standard carriers focused on their delivery.
     */
    private fun allowed(team: Team): Boolean = playing() && CastleWars.game.match.team(bot) == team &&
        CastleWars.game.match.carried(bot) == null && !bot.status.isImmobilized()

    /**
     * Geometric team halves are used only for safe supply collection and defensive deployment, never crowd prediction.
     */
    private fun ownHalf(position: Position, team: Team): Boolean {
        val offset = if (position.y >= 6400) 6400 else 0
        fun home(owner: Team) = Position(owner.home.x, owner.home.y + offset, position.z)
        return position.computeLongestDistance(home(team)) < position.computeLongestDistance(home(team.enemy))
    }

    /**
     * Preserves room for the two hands displaced by a captured standard, and limits each role to a small trip's stock.
     */
    suspend fun supplies(role: Role, team: Team, objects: List<GameObject>): Boolean {
        if (!allowed(team) || bot.inventory.computeRemainingSize() <= 2) return false
        val plan = when (role) {
            Role.MEDIC -> linkedMapOf(4049 to 12, 4051 to 1, 4053 to 1)
            Role.ENGINEER -> linkedMapOf(4049 to 6, 4051 to 1, 4043 to 4, 4053 to 2, 1265 to 1)
            Role.RAIDER -> linkedMapOf(4049 to 6, 4045 to 3, 954 to 1, 1265 to 1)
            Role.DEFENDER -> linkedMapOf(4049 to 8, 4051 to 1, 4053 to 2, 4045 to 2)
            Role.RUNNER -> linkedMapOf(4049 to 6, 4045 to 2, 954 to 1)
        }
        if (CastleWarsGame.inSpawnRoom(bot.position)) {
            for ((id, amount) in plan) {
                if (bot.inventory.computeAmountForId(id) >= amount) continue
                val tableId = CastleWarsGame.TABLES.entries.firstOrNull { it.value == id }?.key ?: continue
                val table = objects.filter { it.id == tableId && ownHalf(it.position, team) }
                    .minByOrNull { bot.position.computeLongestDistance(it.position) } ?: continue
                val before = bot.inventory.computeAmountForId(id)
                if (attempt(table, { bot.inventory.computeAmountForId(id) > before }) {
                        bot.actionHandler.interactions.interact(1, table) { allowed(team) && bot.inventory.computeRemainingSize() > 2 }
                    }) return true
            }
        }
        val groundIds = when (role) {
            Role.RAIDER -> setOf(590)
            Role.ENGINEER, Role.DEFENDER, Role.MEDIC -> if (bot.inventory.contains(1929)) emptySet() else setOf(1925)
            else -> emptySet()
        }.filter { !bot.inventory.contains(it) }
        val ground = world.locator.findItems(bot, 12) {
            it.id in groundIds && it.isVisibleTo(bot) && ownHalf(it.position, team)
        }.minByOrNull { bot.position.computeLongestDistance(it.position) } ?: return false
        val before = bot.inventory.computeAmountForId(ground.id)
        return attempt(ground, { bot.inventory.computeAmountForId(ground.id) > before }) {
            bot.actionHandler.interactions.interact(1, ground) {
                allowed(team) && ground.state == EntityState.ACTIVE && ground.isVisibleTo(bot) &&
                    bot.inventory.computeRemainingSize() > 2 && bot.inventory.hasSpaceFor(ground.toItem())
            }
        }
    }

    /**
     * Supplies role destinations to the existing stair-and-door traversal instead of creating another movement loop.
     */
    fun destination(role: Role, team: Team): Position? {
        if (bot.position.y >= 6400) return tunnel(team.enemy, true)
        return when (role) {
            Role.ENGINEER -> catapult(team)
            Role.RAIDER -> if ((CastleWars.siege.bits(team.enemy) and (1 shl 10)) != 0 ||
                !bot.inventory.contains(4045) && !bot.inventory.contains(590)) null
                else if (ownHalf(bot.position, team) && (Pickaxe.computePickType(bot) != null || bot.inventory.contains(4045)))
                    tunnel(team, false) else catapult(team.enemy)
            Role.DEFENDER -> CastleWars.game.match.flag(team).position ?: team.home
            Role.MEDIC -> CastleWars.game.match.players().keys.filter {
                it !== bot && CastleWars.game.match.team(it) == team && it.isViewableFrom(bot) &&
                    (CastleWars.game.match.carried(it) != null || it.combat.inCombat() || it.health < it.hitpoints.staticLevel - 3)
            }.minWithOrNull(compareBy<Player> { CastleWars.game.match.carried(it) == null }.thenBy { it.health })?.position
                ?: Position(team.home.x, team.home.y, 0)
            else -> null
        }
    }

    /**
     * Performs useful visible support before ordinary standard movement, with no own-team sabotage.
     */
    suspend fun act(role: Role, team: Team, objects: List<GameObject>): Boolean {
        if (!allowed(team) || System.currentTimeMillis() < nextActionAt) return false
        if ((role == Role.MEDIC || bot.personality.isKind) && heal(team)) return true
        if (role in setOf(Role.RUNNER, Role.RAIDER) && takeStandard(team)) return true
        if (bot.combat.inCombat()) return false
        if (bot.inventory.contains(1925)) {
            val tap = objects.filter { it.id == 4482 && ownHalf(it.position, team) }
                .minByOrNull { bot.position.computeLongestDistance(it.position) }
            if (tap != null && use(1925, tap, team) { bot.inventory.contains(1929) }) return true
        }
        if (role in setOf(Role.ENGINEER, Role.DEFENDER, Role.MEDIC) && repair(team, objects)) return true
        if (barricades(team)) return true
        if (role == Role.RAIDER && raid(team, objects)) return true
        if (bot.position.y >= 6400 && underground(team, objects)) return true
        if (role == Role.ENGINEER && shoot(team, objects)) return true
        if (role in setOf(Role.DEFENDER, Role.ENGINEER) && placeBarricade(team)) return true
        return false
    }

    /**
     * Native bandages can heal and restore run energy to a nearby teammate, particularly a standard carrier.
     */
    private suspend fun heal(team: Team): Boolean {
        if (!bot.inventory.contains(4049)) return false
        val ally = CastleWars.game.match.players().keys.filter {
            it !== bot && it.isAlive && CastleWars.game.match.team(it) == team &&
                it.position.isWithinDistance(bot.position, 4) &&
                (it.health < it.hitpoints.staticLevel - 3 || CastleWars.game.match.carried(it) != null && it.runEnergy < 70)
        }.minWithOrNull(compareBy<Player> { CastleWars.game.match.carried(it) == null }.thenBy { it.health }) ?: return false
        val before = bot.inventory.computeAmountForId(4049)
        return attempt(ally, { bot.inventory.computeAmountForId(4049) < before }) {
            bot.actionHandler.inventory.useItem(4049).onPlayer(ally) {
                allowed(team) && CastleWars.game.match.team(ally) == team && CastleWars.game.match.isPlaying(ally)
            }
        }
    }

    /**
     * Relieves a stationary teammate only after the native five-minute carrier rule permits handing over the standard.
     */
    private suspend fun takeStandard(team: Team): Boolean {
        val carrier = CastleWars.game.match.players().keys.firstOrNull {
            CastleWars.game.match.canTakeFrom(bot, it) && it.position.isWithinDistance(bot, 4) &&
                it.walking.isEmpty && !it.combat.inCombat()
        } ?: return false
        return attempt(carrier, { CastleWars.game.match.carried(bot) != null }) {
            bot.actionHandler.interactions.interact(CastleWarsGame.TAKE_FROM.index, carrier) {
                allowed(team) && CastleWars.game.match.canTakeFrom(bot, carrier) && carrier.walking.isEmpty
            }
        }
    }

    /**
     * Only the owning team's damaged doors or catapults can receive a toolkit; water is reserved for friendly fire.
     */
    private suspend fun repair(team: Team, objects: List<GameObject>): Boolean {
        for (obj in objects.sortedBy { bot.position.computeLongestDistance(it.position) }) {
            if (obj.id in CATAPULTS && catapultTeam(obj.id) == team) {
                if (obj.id in 4904..4905 && bot.inventory.contains(1929) && use(1929, obj, team) { replaced(obj, setOf(4381, 4382)) }) return true
                if (obj.id in 4385..4386 && bot.inventory.contains(4051) && use(4051, obj, team) { replaced(obj, setOf(4381, 4382)) }) return true
            }
            val ownDoor = obj.id in 4423..4434 && (obj.id in 4423..4426 || obj.id in 4431..4432) == (team == Team.SARADOMIN)
            if (ownDoor && bot.inventory.contains(4051) && (CastleWars.siege.bits(team) and 127) < 100 &&
                use(4051, obj, team) { (CastleWars.siege.bits(team) and 127) == 100 }) return true
        }
        return false
    }

    /**
     * Extinguishes friendly barricades and clears enemy barricades with a real explosive or tinderbox.
     */
    private suspend fun barricades(team: Team): Boolean {
        val options = world.locator.findNpcs(bot, 8) { CastleWars.siege.barricadeOwner(it) != null }
        for (npc in options.sortedBy { bot.position.computeLongestDistance(it.position) }) {
            val owner = CastleWars.siege.barricadeOwner(npc) ?: continue
            val burning = CastleWars.siege.isBurningBarricade(npc)
            val item = if (owner == team && burning && bot.inventory.contains(1929)) 1929
                else if (owner != team && !burning && bot.inventory.contains(4045)) 4045
                else if (owner != team && !burning && bot.inventory.contains(590)) 590 else continue
            if (attempt(npc, { npc.state != EntityState.ACTIVE || CastleWars.siege.isBurningBarricade(npc) != burning }) {
                    bot.actionHandler.inventory.useItem(item).onNpc(npc) {
                        allowed(team) && CastleWars.siege.barricadeOwner(npc) == owner &&
                            (if (item == 1929) owner == team else owner != team)
                    }
                }) return true
        }
        return false
    }

    /**
     * Raiders use existing cellar ladders and ropes and sabotage only enemy catapults.
     */
    private suspend fun raid(team: Team, objects: List<GameObject>): Boolean {
        val ladder = objects.firstOrNull { it.id == 4912 && it.position == tunnel(team, false) }
        if (bot.position.y < 6400 && ownHalf(bot.position, team) && ladder != null && ladder.position.isWithinDistance(bot, 8)) {
            val before = bot.position
            if (attempt(ladder, { bot.position != before }) { bot.actionHandler.interactions.interact(1, ladder) { allowed(team) } }) return true
        }
        val enemy = objects.filter { it.id in CATAPULTS && catapultTeam(it.id) != team }
            .minByOrNull { bot.position.computeLongestDistance(it.position) }
        if (enemy != null && enemy.id in 4381..4382) {
            val item = if (bot.inventory.contains(4045)) 4045 else if (bot.inventory.contains(590)) 590 else -1
            if (item != -1 && use(item, enemy, team) { replaced(enemy, setOf(4385, 4386, 4904, 4905)) }) return true
        }
        if (bot.inventory.contains(954)) {
            val wall = objects.filter { it.id in setOf(4446, 4447) && !ownHalf(it.position, team) && it.position.isWithinDistance(bot, 8) }
                .minByOrNull { bot.position.computeLongestDistance(it.position) }
            if (wall != null && use(954, wall, team) { !bot.inventory.contains(954) }) return true
        }
        val rope = objects.filter { it.id == 4444 && !ownHalf(it.position, team) && it.position.isWithinDistance(bot, 2) }.firstOrNull()
        if (rope != null) {
            val before = bot.position
            if (attempt(rope, { bot.position != before }) { bot.actionHandler.interactions.interact(1, rope) { allowed(team) } }) return true
        }
        return false
    }

    /**
     * Clears source tunnel obstructions, or collapses a clear tunnel only around visible enemies without allies.
     */
    private suspend fun underground(team: Team, objects: List<GameObject>): Boolean {
        val exit = objects.firstOrNull { it.id == 1757 && it.position == tunnel(team.enemy, true) }
        if (exit != null && exit.position.isWithinDistance(bot, 8)) {
            if (attempt(exit, { bot.position.y < 6400 }) { bot.actionHandler.interactions.interact(1, exit) { allowed(team) } }) return true
        }
        for (obj in objects.filter { it.id in setOf(4437, 4438, 4448) && it.position.isWithinDistance(bot, 10) }) {
            val blast = CastleWarsSiege.tunnels.minByOrNull { it.computeLongestDistance(obj.position) } ?: continue
            fun permitted(): Boolean = allowed(team) && (obj.id != 4448 ||
                !bot.position.isWithinDistance(blast, 1) && CastleWars.game.match.players().none { (player, owner) ->
                    owner == team && player.position.isWithinDistance(blast, 1)
                } && CastleWars.game.match.players().any { (player, owner) -> owner != team && player.isViewableFrom(bot) &&
                    player.position.isWithinDistance(blast, 1) })
            if (!permitted()) continue
            if (bot.inventory.contains(4045) && use(4045, obj, team, ::permitted) { !current(obj) }) return true
            if (Pickaxe.computePickType(bot) != null && attempt(obj, { !current(obj) }) {
                    bot.actionHandler.interactions.interact(1, obj, ::permitted)
                }) return true
        }
        return false
    }

    /**
     * Aims through actual panel buttons at a visible enemy; scatter can reach four tiles, so allies exclude that area.
     */
    private suspend fun shoot(team: Team, objects: List<GameObject>): Boolean {
        if (!bot.inventory.contains(4043)) return false
        val engine = objects.firstOrNull { it.id in 4381..4382 && catapultTeam(it.id) == team } ?: return false
        val origin = if (team == Team.SARADOMIN) Position(2411, 3092) else Position(2388, 3115)
        fun safeTarget(target: Position): Boolean = allowed(team) && target.z == 0 &&
            CastleWars.game.match.players().none { (player, owner) -> owner == team && player.position.isWithinDistance(target, 4) }
        val target = CastleWars.game.match.players().keys.filter {
            CastleWars.game.match.team(it) != team && it.isAlive && it.isViewableFrom(bot) &&
                !CastleWarsGame.inSpawnRoom(it.position) && kotlin.math.abs(it.position.x - origin.x) <= 30 &&
                kotlin.math.abs(it.position.y - origin.y) <= 30 && safeTarget(it.position)
        }.minByOrNull { it.health }?.position ?: return false
        return attempt(engine, { !bot.inventory.contains(4043) || !bot.overlays.has(CatapultInterface::class.java) }, timeout = 45_000) {
            if (!bot.actionHandler.interactions.interact(1, engine) { allowed(team) && safeTarget(target) } ||
                !waitFor(3.seconds) { bot.overlays.has(CatapultInterface::class.java) }) return@attempt false
            val aim = bot.overlays.getOverlay(CatapultInterface::class.java) ?: return@attempt false
            if (aim.catapult !== engine) return@attempt false
            val x = target.x - origin.x
            val y = target.y - origin.y
            while (aim.x != x || aim.y != y) {
                if (!safeTarget(target) || bot.overlays.getOverlay(CatapultInterface::class.java) !== aim) return@attempt false
                val oldX = aim.x
                val oldY = aim.y
                val sign = if (team == Team.SARADOMIN) 1 else -1
                val button = when {
                    aim.x < x -> if (sign == 1) 11324 else 11323
                    aim.x > x -> if (sign == 1) 11323 else 11324
                    aim.y < y -> if (sign == 1) 11321 else 11322
                    else -> if (sign == 1) 11322 else 11321
                }
                bot.output.clickButton(button)
                if (!waitFor(2.seconds) { aim.x != oldX || aim.y != oldY }) return@attempt false
            }
            if (!safeTarget(target) || !current(engine)) return@attempt false
            val before = bot.inventory.computeAmountForId(4043)
            bot.output.clickButton(11329)
            waitFor(3.seconds) { bot.inventory.computeAmountForId(4043) < before }
        }.also {
            if (bot.overlays.getOverlay(CatapultInterface::class.java)?.catapult === engine) bot.overlays.closeWindows(false)
        }
    }

    /**
     * Defends an own-castle approach only when enemies are visible, without blocking a teammate or the standard stand.
     */
    private suspend fun placeBarricade(team: Team): Boolean {
        if (!bot.inventory.contains(4053) || bot.position.z != 0 || bot.position.y >= 6400 ||
            !ownHalf(bot.position, team) || CastleWars.siege.barricadeCount(team) >= 10 ||
            CastleWars.game.match.players().any { (player, owner) -> player !== bot && owner == team && player.position.isWithinDistance(bot, 2) } ||
            CastleWars.game.match.players().none { (player, owner) -> owner != team && player.position.isWithinDistance(bot, 8) } ||
            world.locator.findObjectsOnTile(bot.position) { it.objectType == ObjectType.DEFAULT }.isNotEmpty()) return false
        val before = bot.inventory.computeAmountForId(4053)
        return attempt(bot.position, { bot.inventory.computeAmountForId(4053) < before }) {
            allowed(team) && bot.actionHandler.inventory.clickItem(1, 4053)
        }
    }

    /**
     * Checks the exact current object and replacement form instead of treating arrival as a completed siege action.
     */
    private fun current(obj: GameObject): Boolean = world.locator.findObjectsOnTile(obj.position) { it === obj }.isNotEmpty()
    private fun replaced(obj: GameObject, ids: Set<Int>): Boolean = !current(obj) &&
        world.locator.findObjectsOnTile(obj.position) { it.id in ids }.isNotEmpty()

    /**
     * Uses an owned item with live activity and hazard permission throughout native approach and packet submission.
     */
    private suspend fun use(id: Int, obj: GameObject, team: Team, extra: () -> Boolean = { true }, result: () -> Boolean): Boolean =
        attempt(obj, result) { bot.actionHandler.inventory.useItem(id).onObject(obj) { allowed(team) && extra() && current(obj) } }

    /**
     * Confirms a native effect and limits retries without inventing success for an accepted click.
     */
    private suspend fun attempt(target: Any, result: () -> Boolean, timeout: Long = 12_000, start: suspend () -> Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (!playing() || now < nextActionAt || now < (retryAt[target] ?: 0)) return false
        retryAt[target] = now + 15_000
        val success = withTimeoutOrNull(timeout) { start() && waitFor(3.seconds, result) } == true
        retryAt[target] = System.currentTimeMillis() + if (success) 1_200 else 15_000
        if (success) {
            nextActionAt = System.currentTimeMillis() + 1_200
            retryAt[target] = nextActionAt
        }
        return success
    }
}
