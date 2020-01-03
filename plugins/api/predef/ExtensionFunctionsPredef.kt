package api.predef

import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.model.chunk.ChunkManager
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.inter.AbstractInterfaceSet
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.game.task.Task
import io.luna.net.msg.out.ConfigMessageWriter
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/*****************************
 *                           *
 *  [Player] ext. functions  *
 *                           *
 ****************************/

/**
 * Queues a [ConfigMessageWriter] message.
 */
fun Player.sendConfig(id: Int, state: Int) = queue(ConfigMessageWriter(id, state))


/********************************
 *                              *
 *  [Attribute] ext. functions  *
 *                              *
 *******************************/

/**
 * An extension property that adds a getter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.getValue(plr: Player, property: KProperty<*>): T = plr.attributes[this]

/**
 * An extension property that adds a setter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.setValue(plr: Player, property: KProperty<*>, value: T) {
    plr.attributes[this] = value
}


/*****************************
 *                           *
 *  [World] ext. functions   *
 *                           *
 ****************************/

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
fun World.addObject(obj: GameObject): GameObject {
    return when {
        objects.register(obj) -> obj
        else -> throw IllegalStateException("$obj Could not be spawned!");
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
    val obj = GameObject(ctx, id, Position(x, y, z), type, direction, Optional.ofNullable(plr))
    return addObject(obj)
}

/**
 * Spawns a [GameObject] for [plr].
 */
fun World.addObject(id: Int,
                    position: Position,
                    type: ObjectType = ObjectType.DEFAULT,
                    direction: ObjectDirection = ObjectDirection.WEST,
                    plr: Player? = null): GameObject {
    val obj = GameObject(ctx, id, position, type, direction, Optional.ofNullable(plr))
    return addObject(obj)
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
    val item = GroundItem(ctx, id, amount, Position(x, y, z), Optional.ofNullable(plr))
    return addItem(item)
}

/**
 * Spawns a [GroundItem] for [plr].
 */
fun World.addItem(id: Int,
                  amount: Int = 1,
                  position: Position,
                  plr: Player? = null): GroundItem {
    val item = GroundItem(ctx, id, amount, position, Optional.ofNullable(plr))
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
 * Schedules a task that suspends after one execution.
 */
fun World.scheduleOnce(delay: Int, action: (Task) -> Unit) {
    schedule(delay) {
        action(it)
        it.cancel()
    }
}


/***********************************
 *                                 *
 *  [ChunkManager] ext. functions  *
 *                                 *
 **********************************/

/**
 * Shortcut to [ChunkManager.getViewableEntities] for [Player]s.
 */
fun ChunkManager.getViewablePlayers(position: Position) = getViewableEntities<Player>(position, TYPE_PLAYER)

/**
 * Shortcut to [ChunkManager.getViewableEntities] for [Npc]s.
 */
fun ChunkManager.getViewableNpcs(position: Position) = getViewableEntities<Npc>(position, TYPE_NPC)

/**
 * Shortcut to [ChunkManager.getViewableEntities] for [GroundItem]s.
 */
fun ChunkManager.getViewableItems(position: Position) = getViewableEntities<GroundItem>(position, TYPE_ITEM)

/**
 * Shortcut to [ChunkManager.getViewableEntities] for [GameObject]s.
 */
fun ChunkManager.getViewableObjects(position: Position) = getViewableEntities<GameObject>(position, TYPE_OBJECT)


/*******************************************
 *                                         *
 *  [AbstractInterfaceSet] ext. functions  *
 *                                         *
 ******************************************/

/**
 * Returns the currently open [StandardInterface] if it matches [interClass].
 */
fun <T : StandardInterface> AbstractInterfaceSet.get(interClass: KClass<T>): T? {
    val jClass = interClass.java
    return currentStandard.filter { jClass.isInstance(it) }.map { jClass.cast(it) }.orElse(null)
}

/**
 * Determines if the currently open [StandardInterface] matches [interClass].
 */
fun <T : StandardInterface> AbstractInterfaceSet.isOpen(interClass: KClass<T>): Boolean {
    val jClass = interClass.java
    return currentStandard.filter { jClass.isInstance(it) }.isPresent
}


/****************************
 *                          *
 *  [Array] ext. functions  *
 *                          *
 ***************************/

/**
 * Randomizes the elements within the array. This function modifies the backing array.
 */
fun <T> Array<T>.shuffle() {
    var i = size - 1
    while (i > 0) {
        val index = rand(i)
        val obj = this[index]
        this[index] = this[i]
        this[i] = obj
        i--
    }
}


/**********************************
 *                                *
 *  [OptionalInt] ext. functions  *
 *                                *
 *********************************/

/**
 * Adds a map function to OptionalInt.
 */
fun <T> OptionalInt.map(mapper: (Int) -> T): Optional<T> {
    if (isPresent) {
        val newValue = mapper(asInt)
        return Optional.of(newValue)
    }
    return Optional.empty()
}

/**
 * Adds a mapToInt function to OptionalInt.
 */
fun OptionalInt.mapToInt(mapper: (Int) -> Int): OptionalInt {
    if (isPresent) {
        val newValue = mapper(asInt)
        return OptionalInt.of(newValue)
    }
    return OptionalInt.empty()
}
