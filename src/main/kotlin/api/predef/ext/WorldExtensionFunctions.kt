package api.predef.ext

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import java.time.Duration

/**
 * Spawns an [Npc].
 */
fun World.addNpc(npc: Npc): Npc {
    npcs.add(npc)
    return npc
}

/**
 * Spawns an [Npc].
 */
fun World.addNpc(id: Int, x: Int, y: Int, z: Int = 0): Npc {
    val npc = Npc(ctx, id, Position(x, y, z))
    return addNpc(npc)
}

/**
 * Despawns an [Npc].
 */
fun World.removeNpc(npc: Npc) = npcs.remove(npc)

/**
 * Spawns a [GameObject].
 */
fun World.addObject(obj: GameObject): Boolean {
    return when {
        objects.register(obj) -> true
        else -> false
    }
}

/**
 * Spawns a [GameObject] for [plr].
 */
fun World.addObject(id: Int,
                    x: Int,
                    y: Int,
                    z: Int = 0,
                    type: ObjectType = ObjectType.DEFAULT,
                    direction: ObjectDirection = ObjectDirection.WEST,
                    plr: Player? = null): GameObject {
    val obj = GameObject.createDynamic(ctx, id, Position(x, y, z), type, direction,
                                       if (plr == null) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(
                                           plr))
    addObject(obj)
    return obj
}

/**
 * Spawns a [GameObject] for [plr].
 */
fun World.addObject(id: Int,
                    position: Position,
                    type: ObjectType = ObjectType.DEFAULT,
                    direction: ObjectDirection = ObjectDirection.WEST,
                    plr: Player? = null): GameObject {
    val obj = GameObject.createDynamic(ctx, id, position, type, direction,
                                       if (plr == null) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(
                                           plr))
    addObject(obj)
    return obj
}

/**
 * Despawns a [GameObject].
 */
fun World.removeObject(obj: GameObject): Boolean = objects.unregister(obj)

/**
 * Despawns all [GameObject]s on [pos] that match [filter].
 */
fun World.removeObject(pos: Position, filter: GameObject.() -> Boolean = { true }): Boolean {
    return objects.removeFromPosition(pos, filter)
}

/**
 * Spawns a [GroundItem].
 */
fun World.addItem(item: GroundItem): GroundItem {
    return when {
        items.register(item) -> item
        else -> throw IllegalStateException("$item Could not be spawned!") // change to null
    }
}

/**
 * Spawns a [GroundItem] for [plr].
 */
fun World.addItem(id: Int,
                  amount: Int = 1,
                  x: Int,
                  y: Int,
                  z: Int = 0,
                  plr: Player? = null): GroundItem {
    val item = GroundItem(ctx,
                          id,
                          amount,
                          Position(x, y, z),
                          if (plr == null) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(plr))
    return addItem(item)
}

/**
 * Spawns a [GroundItem] for [plr].
 */
fun World.addItem(id: Int,
                  amount: Int = 1,
                  position: Position,
                  plr: Player? = null): GroundItem {
    val item = GroundItem(ctx,
                          id,
                          amount,
                          position,
                          if (plr == null) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(plr))
    return addItem(item)
}

/**
 * Despawns a [GroundItem].
 */
fun World.removeItem(item: GroundItem): Boolean = items.unregister(item)

/**
 * Despawns all [GroundItem]s on [pos] that match [filter].
 */
fun World.removeItem(pos: Position, filter: GroundItem.() -> Boolean = { true }): Boolean {
    return items.removeFromPosition(pos, filter)
}

/**
 * Schedules a recurring task.
 */
fun World.schedule(delay: Int, instant: Boolean = false, action: (Task) -> Unit) {
    schedule(object : Task(instant, delay) {
        override fun execute() {
            action(this)
        }
    })
}

/**
 * Schedules a recurring task.
 */
fun World.schedule(duration: Duration, instant: Boolean = false, action: (Task) -> Unit) {
    schedule(duration.toTicks(), instant, action)
}

/**
 * Schedules a task that suspends after one execution.
 */
fun World.scheduleOnce(delay: Int, action: (Task) -> Unit) {
    schedule(delay) {
        action(it)
        it.cancel()
    }
}

/**
 * Schedules a task that suspends after one execution.
 */
fun World.scheduleOnce(duration: Duration, action: (Task) -> Unit) {
    scheduleOnce(duration.toTicks(), action)
}