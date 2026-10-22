package game.content.barrows

import api.attr.Attr
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Mob
import io.luna.net.msg.out.ClearHintMessageWriter
import io.luna.net.msg.out.MobHintMessageWriter

/**
 * Tracks only live encounters; saved defeat flags belong to [BarrowsProgress].
 *
 * @author lare96
 */
object BarrowsEncounters {
    private var Player.brothers by Attr.map<BarrowsBrother, BarrowsBrotherNpc>()
    private var Player.hintedBrother by Attr.nullableObj(BarrowsBrotherNpc::class)

    /**
     * Finds a nearby tile reachable by walking in a straight line, within the source's two-tile radius.
     */
    fun nearbyTile(player: Player, anchor: Position): Position? {
        val collision = player.world.collisionManager
        val candidates = (-2..2).flatMap { x -> (-2..2).map { y -> anchor.translate(x, y) } }.shuffled()
        return candidates.firstOrNull { destination ->
            if (!BarrowsAreaListener.inside(destination) || collision.isBlocked(destination, false)) {
                false
            } else {
                var tile = anchor
                var reachable = true
                while (tile != destination && reachable) {
                    val direction = Direction.between(tile, destination)
                    reachable = collision.traversable(tile, EntityType.NPC, direction)
                    tile = tile.translate(1, direction)
                }
                reachable
            }
        }
    }

    /**
     * Spawns at most one live instance of each undefeated brother for this player.
     */
    fun spawn(player: Player, brother: BarrowsBrother, speech: String? = null): BarrowsBrotherNpc? {
        if (player.state != EntityState.ACTIVE || !player.isAlive || !BarrowsAreaListener.inside(player.position) ||
            BarrowsProgress.isKilled(player, brother)) return null
        val previous = player.brothers[brother]
        if (previous != null && previous.state == EntityState.ACTIVE) return previous
        val position = nearbyTile(player, player.position) ?: return null
        val npc = BarrowsBrotherNpc(player, brother, position)
        player.brothers[brother] = npc
        if (!player.world.npcs.add(npc)) {
            player.brothers.remove(brother)
            return null
        }
        player.hintedBrother = npc
        player.queue(MobHintMessageWriter(npc))
        if (speech != null) npc.speak(speech)
        npc.combat.attack(player)
        return npc
    }

    /**
     * Drops stale references without clearing another brother's current hint arrow.
     */
    fun finished(npc: BarrowsBrotherNpc) {
        val player = npc.owner
        if (player.brothers[npc.brother] === npc) player.brothers.remove(npc.brother)
        if (player.hintedBrother === npc) {
            player.hintedBrother = null
            if (player.state == EntityState.ACTIVE) player.queue(ClearHintMessageWriter())
        }
    }

    fun clear(player: Player) {
        player.brothers.values.toList().forEach { player.world.npcs.remove(it) }
        player.brothers.clear()
    }

    /**
     * Credits the owning killer and removes the encounter without normal NPC loot or respawn.
     */
    fun defeated(npc: BarrowsBrotherNpc, source: Mob?) {
        if (source === npc.owner && npc.owner.state == EntityState.ACTIVE) {
            BarrowsProgress.recordBrotherKill(npc.owner, npc.brother)
        }
        npc.world.npcs.remove(npc)
    }
}
