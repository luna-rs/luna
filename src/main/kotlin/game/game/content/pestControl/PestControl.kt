package game.content.pestControl

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import engine.combat.status.StatusEffectType
import game.player.Animations
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.controller.PlayerController

/**
 * Runs the revision 377 single-boat game on the cache's Pest Control island.
 * All membership, NPC ownership and reward changes run synchronously on the game thread.
 *
 * @author lare96
 */
object PestControl {
    val DOCK = Position(2657, 2639)
    val LANDER = Position(2661, 2639)
    val START = Position(2656, 2614)
    val KNIGHT = Position(2656, 2592)
    val OUTPOST = Position(2659, 2676)
    val SARIM = Position(3041, 3202)
    val PORTALS = listOf(Position(2628, 2591), Position(2680, 2588), Position(2669, 2570), Position(2645, 2569))
    val round = PestControlRound<Player>()
    val defences = PestControlDefences()
    private val owned = linkedSetOf<PestControlNpc>()
    private val portals = arrayOfNulls<PestControlNpc>(4)
    private var knight: PestControlNpc? = null
    private var running = false
    private var clock = 0
    private var nextSpawn = 0
    var Player.commendations by Attr.int().persist("pest_control_points")
    var Player.unclaimedPestCoins by Attr.int().persist("pest_control_coins")

    fun onIsland(pos: Position): Boolean = pos.z == 0 && pos.x in 2624..2687 && pos.y in 2560..2623
    fun onLander(pos: Position): Boolean = pos.z == 0 && pos.x in 2659..2664 && pos.y in 2636..2643
    fun atOutpost(pos: Position): Boolean = pos.z == 0 && pos.x in 2624..2687 && pos.y in 2630..2687
    fun owns(npc: Mob): Boolean = npc is PestControlNpc && npc in owned
    fun canAttack(player: Player, target: Mob): Boolean = round.playing(player) &&
        onIsland(player.position) && owns(target) && target.idForPest() in 3727..3780 && target.isAlive

    private fun Mob.idForPest(): Int = (this as? PestControlNpc)?.id ?: -1

    fun launch() {
        if (running) return
        running = true
        DeathHookHandler.addPlayerHook(PestControlController::class.java) {
            preDeath { victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE) }
            death { }
            postDeath { respawn(victim) }
        }
        world.schedule(1) { tick() }
    }

    fun board(player: Player) {
        if (!player.isAlive || !player.position.isWithinDistance(DOCK, 8) ||
            player.controllers.primary.javaClass != PlayerController::class.java) return
        if (player.combatLevel < 40) {
            player.sendMessage("You need a combat level of 40 to join Pest Control.")
            return
        }
        if (!round.join(player)) return
        player.overlays.closeWindows()
        player.move(LANDER)
        player.controllers.register(PestControlController(player))
        player.sendMessage("You board the lander. It carries 5 to 25 volunteers and departs within five minutes.")
        player.sendMessage("Destroy all four portals or protect the Void Knight for twenty minutes.")
        startReadyRound(false)
    }

    private fun startReadyRound(advance: Boolean = true) {
        val players = round.launch(advance)
        if (players.isEmpty()) return
        defences.reset()
        defences.load()
        knight = spawn(3782, KNIGHT)
        spawn(3781, START.translate(3, 0))
        PORTALS.forEachIndexed { index, position -> portals[index] = spawn(3777 + index, position, index) }
        players.forEach {
            clearCombat(it)
            restore(it)
            it.overlays.closeWindows()
            it.move(START)
            it.sendMessage("The invasion has begun! Deal 50 damage to earn a commendation; each repair counts as 5.")
        }
        if (knight?.isAlive != true || portals.any { it?.isAlive != true }) {
            players.forEach { it.sendMessage("The island could not be prepared. Please board again.") }
            finish(false)
            return
        }
        nextSpawn = clock + 10
    }

    private fun spawn(id: Int, position: Position, portal: Int = -1): PestControlNpc {
        val npc = PestControlNpc(id, position, portal)
        owned += npc
        if (!world.npcs.add(npc)) owned.remove(npc)
        return npc
    }

    fun tick() {
        clock++
        (round.players() + round.waiting()).filter {
            it.state != EntityState.ACTIVE || it.controllers.primary !is PestControlController ||
                (!onIsland(it.position) && !onLander(it.position))
        }.forEach {
            if (it.state == EntityState.ACTIVE && it.controllers.primary is PestControlController) leave(it)
            else detach(it)
        }
        if (!round.active) {
            startReadyRound()
            if (clock % 100 == 0) round.waiting().forEach {
                it.sendMessage("Lander: ${round.waiting().size} waiting; departure in ${(round.departure + 99) / 100} minutes (5 required).")
            }
            return
        }
        // Poison can kill a portal without passing through a direct combat defence hook.
        portals.forEachIndexed { index, portal ->
            if (portal != null && !portal.isAlive && round.destroyPortal(index)) {
                knight?.takeIf { it.isAlive }?.let { it.health = (it.health + 50).coerceAtMost(it.totalHealth) }
            }
        }
        val result = round.tick(knight?.isAlive == true)
        if (result != null) { finish(result); return }
        defences.tick()
        if (clock >= nextSpawn) {
            spawnWave()
            nextSpawn = clock + 10
        }
        if (clock % 5 == 0) owned.toList().filter { it.id < 3777 && it.isAlive }.forEach(::think)
        if (clock % 100 == 0) round.players().forEach {
            it.sendMessage("Pest Control: ${(round.remaining + 99) / 100} min; Knight ${knight?.health ?: 0}; " +
                "portals ${portals.joinToString("/") { portal -> (portal?.health ?: 0).toString() }}; " +
                "your contribution ${round.contribution(it)}.")
        }
    }

    private fun spawnWave() {
        val live = portals.filterNotNull().filter { it.isAlive }
        if (live.isEmpty()) return
        // Bounded population and player-scaled waves; exact historical spawn probabilities are not recoverable.
        val count = (round.players().size + 4) / 5
        repeat(count) {
            if (owned.count { it.id < 3777 } >= 100) return
            val portal = live[rand().nextInt(live.size)]
            val ranges = listOf(3727..3731, 3732..3741, 3742..3746, 3747..3751, 3752..3761, 3762..3771, 3772..3776)
            val ids = ranges[rand().nextInt(ranges.size)]
            val position = (0..8).map { n -> portal.position.translate(n % 3 - 1, n / 3 + 3) }
                .firstOrNull { !world.collisionManager.isBlocked(it, true) } ?: return@repeat
            spawn(rand(ids), position, portal.portal)
        }
    }

    private fun think(npc: PestControlNpc) {
        val defender = knight?.takeIf { it.isAlive } ?: return
        if (npc.id in 3747..3751) {
            val portal = portals[npc.portal]?.takeIf { it.isAlive }
            if (portal != null && portal.health < portal.totalHealth) {
                npc.combat.target = null
                npc.combat.autoRetaliateTarget = null
                if (npc.isWithinDistance(portal, 3)) {
                    npc.animation(Animation(3911))
                    npc.graphic(Graphic(658))
                    portal.health = (portal.health + 10).coerceAtMost(portal.totalHealth)
                } else npc.navigator.navigate(portal, false, false)
                return
            }
        }
        val barrier = defences.nearest(npc.position)
        if (barrier != null && npc.id in 3727..3731) {
            if (npc.position.isWithinDistance(barrier.position, 1)) {
                npc.damage(npc.health)
            } else npc.navigator.navigate(barrier, false, false)
            return
        }
        if (barrier != null && (npc.id in 3742..3746 || npc.combat.target == null)) {
            if (npc.position.isWithinDistance(barrier.position, 1)) {
                npc.animation(Animation(npc.combatDef().attackAnimation))
                defences.damage(barrier.position, if (npc.id in 3742..3746) 20 else 5)
                return
            }
            if (npc.id in 3742..3746) {
                npc.navigator.navigate(barrier, false, false)
                return
            }
        }
        if (npc.id in 3732..3741 && !npc.position.isWithinDistance(KNIGHT, 5) && rand().nextInt(12) == 0) {
            val landing = KNIGHT.translate(rand().nextInt(7) - 3, rand().nextInt(7) - 3)
            if (!world.collisionManager.isBlocked(landing, true)) {
                npc.graphic(Graphic(654))
                npc.move(landing)
            }
        }
        val old = npc.combat.target
        val nearby = round.players().filter { it.isAlive && it.position.isWithinDistance(npc.position, 5) }
            .minByOrNull { it.position.computeLongestDistance(npc.position) }
        val target = if (old != null && old.isAlive && (old == defender || old is Player && round.playing(old))) old
            else nearby ?: defender
        // CombatAction drops targets beyond viewing distance plus five before it starts pursuit.
        if (!npc.isWithinDistance(target, Position.VIEWING_DISTANCE + 5)) {
            npc.combat.target = null
            npc.navigator.navigate(target, false, false)
        } else npc.combat.attack(target)
    }

    fun credit(player: Player, victim: PestControlNpc, amount: Int) {
        if (round.playing(player) && onIsland(player.position) && owns(victim) && victim.id in 3727..3780) {
            round.credit(player, amount)
        }
    }

    fun npcDied(npc: PestControlNpc) {
        if (!owns(npc)) return
        if (npc.id in 3727..3731 && round.active) {
            npc.graphic(Graphic(649 + npc.id - 3727))
            val amount = 5 + (npc.id - 3727) * 2
            round.players().filter { it.isAlive && it.position.isWithinDistance(npc.position, 1) }
                .forEach { it.damage(amount) }
            owned.filter { it !== npc && it.isAlive && it.position.isWithinDistance(npc.position, 1) }
                .toList().forEach { it.damage(amount) }
            defences.blast(npc.position, amount)
        }
    }

    fun remove(npc: PestControlNpc) {
        if (!owned.remove(npc)) return
        npc.combat.target = null
        npc.combat.autoRetaliateTarget = null
        npc.actions.interruptAll()
        world.removeNpc(npc)
    }

    private fun finish(won: Boolean) {
        val settlement = round.finish(won)
        owned.toList().forEach(::remove)
        portals.fill(null)
        knight = null
        defences.reset()
        settlement.forEach { (player, eligible) ->
            if (eligible) {
                player.commendations = (player.commendations + 1).coerceIn(0, PestControlRound.POINT_CAP)
                player.unclaimedPestCoins = (player.unclaimedPestCoins.toLong().coerceAtLeast(0) +
                    player.combatLevel * 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            player.sendMessage(if (!won) "The Void Knight was overwhelmed. The mission failed."
                else if (!eligible) "Victory! You did not contribute enough to earn a reward."
                else "Victory! You have ${player.commendations} commendations (maximum 15).")
            leave(player)
            claimCoins(player)
        }
    }

    fun detach(player: Player) {
        round.leave(player)
        clearCombat(player)
    }

    private fun clearCombat(player: Player) {
        player.combat.target = null
        player.combat.autoRetaliateTarget = null
        player.combat.impacts.clear()
        player.actions.getAll(CombatDamageAction::class.java).forEach { it.interrupt() }
        owned.filter { it.combat.target == player }.forEach { it.combat.target = null; it.combat.autoRetaliateTarget = null }
    }

    private fun restore(player: Player) {
        player.status.remove(StatusEffectType.POISONED)
        player.combat.prayers.deactivateAll()
        player.skills.resetAll()
        player.runEnergy = 100.0
    }

    fun leave(player: Player) {
        val attached = player.controllers.primary is PestControlController
        if (!attached && player !in round.waiting() && !round.playing(player)) return
        detach(player)
        // Preserve the safe-death hook through POST_DEATH if the round ends during a death animation.
        if (!player.isAlive) return
        if (attached) player.controllers.unregister()
        restore(player)
        player.move(DOCK)
    }

    fun respawn(player: Player) {
        restore(player)
        if (round.playing(player)) player.move(START) else leave(player)
    }

    fun login(player: Player) {
        if (onIsland(player.position) || onLander(player.position)) {
            restore(player)
            player.move(DOCK)
        }
        player.commendations = player.commendations.coerceIn(0, PestControlRound.POINT_CAP)
    }

    fun claimCoins(player: Player) {
        val amount = player.unclaimedPestCoins
        if (amount <= 0 || !atOutpost(player.position)) return
        if (player.inventory.add(Item(995, amount))) player.unclaimedPestCoins = 0
        else player.sendMessage("Make room and speak to a Void Knight to collect your coins.")
    }

    fun sail(player: Player, toIsland: Boolean) {
        if (!player.isAlive || player.controllers.primary.javaClass != PlayerController::class.java) return
        if (toIsland && !player.position.isWithinDistance(SARIM, 12)) return
        if (!toIsland && !player.position.isWithinDistance(OUTPOST, 12)) return
        player.overlays.closeWindows()
        player.move(if (toIsland) OUTPOST else SARIM)
    }
}
