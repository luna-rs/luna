package game.content.castleWars

import api.predef.*
import api.predef.ext.*
import game.content.castleWars.CastleWarsMatch.Team
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task

/**
 * Runs the shared Castle Wars match and publishes its standards and client overlays.
 *
 * @author lare96
 */
object CastleWars {
    val game = CastleWarsGame()
    val siege = CastleWarsSiege(game)
    private var running = false
    private val dropped = mutableMapOf<Team, GameObject>()
    private var ticks = 0

    fun launch() {
        if (running) return
        running = true
        CastleWarsGame.registerDeathHook()
        world.schedule(object : Task(1) {
            override fun execute() {
                ticks++
                val members = game.match.players() + game.match.waitingPlayers()
                members.keys.filter { it.state != EntityState.ACTIVE }.forEach(game::detach)
                val update = game.tick()
                if (update.started.isNotEmpty() || update.ended) siege.reset()
                siege.tick()
                if (ticks % 100 == 0) game.match.waitingPlayers().keys.forEach { game.joinInProgress(it) }
                renderFlags()
                if (ticks % 5 == 0) renderPlayers()
            }
        })
    }

    fun enter(player: Player, preferred: Team?) {
        if (game.enter(player, preferred)) renderPlayer(player)
    }

    fun flag(player: Player, obj: GameObject) {
        val team = when (obj.id) {
            4377, 4900, 4902 -> Team.SARADOMIN
            4378, 4901, 4903 -> Team.ZAMORAK
            else -> return
        }
        if (obj.position == team.home && game.match.team(player) == team) game.capture(player)
        else if (game.match.flag(team).position == obj.position) game.pickup(player, team)
        renderFlags()
    }

    fun route(player: Player, obj: GameObject) {
        val team = game.match.team(player) ?: return
        if (!game.match.isPlaying(player)) return
        val destination = CastleWarsRoutes.destination(obj.id, obj.position, player.position,
            team, game.match.carried(player)) ?: return
        if (CastleWarsGame.inArena(destination)) player.move(destination)
    }

    private fun renderFlags() {
        for (team in Team.entries) {
            val flag = game.match.flag(team)
            val oldDrop = dropped[team]
            if (oldDrop != null && (oldDrop.position != flag.position || !game.match.active)) {
                world.removeObject(oldDrop)
                dropped.remove(team)
            }
            val id = if (!game.match.active) 4906 + team.ordinal
                else if (flag.position == team.home) 4902 + team.ordinal else 4377 + team.ordinal
            val stand = world.locator.findObjectsOnTile(team.home) {
                it.id in setOf(4377, 4378, 4902, 4903, 4906, 4907)
            }.firstOrNull()
            if (stand != null && stand.id != id) world.addObject(id, team.home, stand.objectType, stand.direction)
            val position = flag.position
            if (game.match.active && position != null && position != team.home && team !in dropped) {
                dropped[team] = world.addObject(4900 + team.ordinal, position)
            }
        }
    }

    private fun renderPlayers() {
        (game.match.players().keys + game.match.waitingPlayers().keys).forEach(::renderPlayer)
    }

    private fun renderPlayer(player: Player) {
        val team = game.match.team(player) ?: return
        val waiting = game.match.isWaiting(player)
        val widget = if (waiting) 11479 else if (team == Team.SARADOMIN) 11146 else 11344
        if (player.overlays.getOverlay(WalkableInterface::class.java)?.id != widget) {
            player.overlays.open(WalkableInterface(widget))
        }
        val minutes = if (waiting) {
            if (game.match.active) (game.match.remaining + 99) / 100 + 5 else (game.match.warmup + 99) / 100
        } else (game.match.remaining + 99) / 100
        player.sendVarp(Varp(380, minutes))
        player.sendVarp(Varp(376, team.ordinal))
        for (owner in Team.entries) {
            val flag = game.match.flag(owner)
            val status = if (flag.carrier != null) 1 else if (flag.position != owner.home) 2 else 0
            val bits = siege.bits(owner) or (status shl 21) or (game.match.score(owner).coerceAtMost(255) shl 24)
            player.sendVarp(Varp(if (owner == Team.SARADOMIN) 378 else 377, bits))
        }
    }
}
