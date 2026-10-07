package game.obj.doors

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*
import java.util.*

/**
 * Opens and closes doors by replacing the clicked object with its other state.
 *
 * There are four kinds of door, each loaded from its own file under `data/game/world/doors/`:
 *
 * - Single doors, which are straight or diagonal walls that move one tile and turn a quarter turn.
 * - Double doors, whose two leaves swing apart from each other.
 * - Gates, whose two leaves swing together as one 2x1 fence around the corner of the hinge leaf.
 * - Curtains, which are replaced in place without moving.
 *
 * Doors that are away from their home state revert on their own after a while.
 *
 * @author Hydrozoa
 */
object Doors {

    /**
     * The most ticks a diagonal door waits for a player to walk off the tile it moves onto, before it is placed anyway.
     */
    private const val MAX_PUSH_WAIT = 5

    /**
     * Maps both the closed id and the open id of every door to its [DoorType]. An open id that is shared by several
     * doors maps to the first of them. That is only used for an open door whose origin is not in [origins], such as one
     * that the map placed open.
     */
    private val byId: HashMap<Int, DoorType> = HashMap()

    /**
     * Every loaded [DoorType].
     */
    var all: List<DoorType> = emptyList()
        private set

    /**
     * The [DoorType]s that are leaves of a gate rather than a double door. Both have a [DoorType.side].
     */
    private val gateTypes: MutableSet<DoorType> = Collections.newSetFromMap(IdentityHashMap())

    /**
     * The [DoorType]s that are curtains, which are replaced in place.
     */
    private val curtainTypes: MutableSet<DoorType> = Collections.newSetFromMap(IdentityHashMap())

    /**
     * The doors that are waiting for a player to walk out of the way of their swing. Compared by identity.
     */
    private val swinging: MutableSet<GameObject> = Collections.newSetFromMap(IdentityHashMap())

    /**
     * Doors that are currently away from their home state, mapped to the object they replaced. A door only reverts
     * on its own if it is still in this map when its timer expires.
     */
    private val displaced: IdentityHashMap<GameObject, GameObject> = IdentityHashMap()

    /**
     * The open ids that are shared by more than one closed door.
     */
    private val sharedOpenIds: MutableSet<Int> = HashSet()

    /**
     * The [DoorType] that each open door was opened from, for the open doors that have a shared open id. Their id cannot
     * tell which closed door they came from, so this is what lets them close back to it. Open doors with their own open
     * id are not kept here, and neither are closed doors. Compared by identity.
     */
    private val origins: IdentityHashMap<GameObject, DoorType> = IdentityHashMap()

    /**
     * A single object replacement made while opening or closing a door.
     *
     * @param old The object being replaced.
     * @param id The id of the replacement.
     * @param position The position of the replacement.
     * @param direction The direction of the replacement.
     */
    private class Swap(val old: GameObject, val id: Int, val position: Position, val direction: ObjectDirection)

    /**
     * The kinds of door, each of which is loaded from its own file.
     *
     * @param hasSide Whether doors of this kind must have a [DoorType.side].
     */
    enum class Kind(val hasSide: Boolean) {

        /**
         * A single door.
         */
        SINGLE(false),

        /**
         * One leaf of a double door.
         */
        DOUBLE(true),

        /**
         * One leaf of a gate, where the [DoorSide.LEFT] leaf is the hinge.
         */
        GATE(true),

        /**
         * A curtain.
         */
        CURTAIN(false)
    }

    /**
     * Adds door definitions of a single [Kind] to the ones already loaded. Must be called on the game thread.
     *
     * @param kind The kind of door being added.
     * @param types The doors to add.
     * @throws IllegalArgumentException If a door has a side when it should not (or the reverse), or if a closed id is used by
     * more than one door, or an open id is shared with a door of the other side or a different open id.
     */
    fun add(kind: Kind, types: List<DoorType>) {
        for (type in types) {
            require((type.side != null) == kind.hasSide) {
                "Door ${type.closed} must ${if (type.side != null) "not " else ""}have a side."
            }
            require(byId.putIfAbsent(type.closed, type) == null) { "Duplicate door id ${type.closed} in door files." }
            // An open id may be shared by several closed doors that look the same. Each one closes back to the door
            // that it was opened from, see [origins].
            val sharing = byId.putIfAbsent(type.open, type)
            require(sharing == null || (sharing.open == type.open && sharing.side == type.side)) {
                "Duplicate door id ${type.open} in door files."
            }
            if (sharing != null) {
                sharedOpenIds += type.open
            }
            when (kind) {
                Kind.GATE -> gateTypes += type
                Kind.CURTAIN -> curtainTypes += type
                else -> {}
            }
        }
        all += types
    }

    /**
     * @return The [DoorType] that [id] belongs to, or `null` if it is not a known door.
     */
    fun typeOf(id: Int): DoorType? = byId[id]

    /**
     * @return The [DoorType] that [obj] is, or `null` if it is not a known door. An open door is the type that it was
     * opened from, which is not always the type that its id maps to when the open id is shared.
     */
    private fun typeOf(obj: GameObject): DoorType? = origins[obj] ?: typeOf(obj.id)

    /**
     * Opens [door] if it is closed, or closes it if it is open.
     *
     * What is replaced depends on the kind of door:
     *
     * - Single doors can be straight or diagonal walls. An opening door moves one tile and turns a quarter turn
     * clockwise. A closing door does the exact inverse, so the position and direction of the original door can always
     * be recovered from the door that was clicked, see [singleSwaps].
     * - Double doors (straight walls only) swing both leaves apart together, see [doubleSwaps].
     * - Gates (straight walls only) swing both leaves together as one fence around the hinge leaf, see [gateSwaps].
     * - Curtains are replaced in place, see [curtainSwaps].
     *
     * Doors that were moved away from their home state revert on their own after [DoorType.durationOrDefault] ticks.
     *
     * @param world The world.
     * @param plr The player that clicked the door.
     * @param door The door that was clicked.
     */
    fun toggle(world: World, plr: Player, door: GameObject) {
        val type = typeOf(door) ?: return
        val opening = door.id == type.closed
        val curtain = type in curtainTypes
        val swaps = when {
            curtain -> curtainSwaps(door, type, opening)
            type.side == null -> singleSwaps(door, opening)
            type in gateTypes -> gateSwaps(world, door, type, opening)
            else -> doubleSwaps(world, door, type, opening)
        }
        if (swaps.isEmpty()) {
            return
        }

        // A door that is waiting for a player to walk out of its way cannot be clicked again.
        if (swinging.contains(door)) {
            return
        }

        /*
         * A diagonal door swings across the tile the player is standing on, so the player walks around the door to a
         * free tile beside it. The door isn't swapped until the player is out of the way, so it never appears to
         * vanish.
         */
        val self = swaps.first()
        val blocking = !curtain && door.objectType == ObjectType.DIAGONAL_WALL && plr.position == self.position
        val stepped = blocking && stepAround(plr, door.position)
        var selfRemoved = false

        if (!stepped) {
            // The clicked door is always first. Fails if it was already replaced, so a stale click cannot spawn a duplicate.
            if (!world.removeObject(self.old)) {
                return
            }
            selfRemoved = true
            if (blocking) {
                // There's no way around, so the player is moved onto the tile that the door leaves.
                plr.move(door.position)
            }
        }
        plr.playSound(if (opening) type.openSoundOrDefault else type.closeSoundOrDefault)

        // Leaves that were away from home are home again, and have nothing left to revert.
        val wasAwayFromHome = swaps.count { displaced.remove(it.old) != null } > 0
        val place = {
            swinging.remove(door)
            val replaced = ArrayList<Pair<GameObject, GameObject>>()
            for (swap in swaps) {
                if (!(swap === self && selfRemoved) && !world.removeObject(swap.old)) {
                    continue
                }
                val from = typeOf(swap.old)
                origins.remove(swap.old)
                val new = world.addObject(swap.id, swap.position, swap.old.objectType, swap.direction)
                if (from != null && swap.id == from.open && from.open in sharedOpenIds) {
                    origins[new] = from
                }
                replaced += Pair(new, swap.old)
            }
            if (!wasAwayFromHome) {
                for ((new, old) in replaced) {
                    displaced[new] = old
                    world.scheduleOnce(type.durationOrDefault) {
                        if (displaced.remove(new) != null && world.removeObject(new)) {
                            val from = typeOf(new)
                            origins.remove(new)
                            val restored = world.addObject(old.id, old.position, old.objectType, old.direction)
                            if (from != null && old.id == from.open && from.open in sharedOpenIds) {
                                origins[restored] = from
                            }
                        }
                    }
                }
            }
        }
        if (blocking) {
            // The client won't draw an object on a tile that the player is still standing on when it spawns, so the door
            // is placed once the player has left it.
            swinging.add(door)
            var waited = 0
            world.schedule(1) {
                if (plr.position != self.position || ++waited >= MAX_PUSH_WAIT) {
                    it.cancel()
                    place()
                }
            }
        } else {
            place()
        }
    }

    /**
     * Queues a single step that takes [player] off the tile it is standing on, without passing through [door].
     *
     * The player prefers to walk directly away from the door, and otherwise to either side of it.
     *
     * @param player The player to move.
     * @param door The tile that the player must not walk onto.
     * @return `true` if a step was queued, or `false` if there is no free tile to walk to.
     */
    private fun stepAround(player: Player, door: Position): Boolean {
        val toDoor = Direction.between(player.position, door)
        val sides = Direction.NESW.filter { it != toDoor && it != toDoor.opposite() }
        return (listOf(toDoor.opposite()) + sides).any { player.navigator.step(it) }
    }

    /**
     * Computes the replacement for a curtain, which is swapped for its other state in the same position and direction.
     */
    private fun curtainSwaps(curtain: GameObject, type: DoorType, opening: Boolean): List<Swap> {
        return listOf(Swap(curtain, if (opening) type.open else type.closed, curtain.position, curtain.direction))
    }

    /**
     * Computes the replacement for a single door, which can be a straight or diagonal wall.
     *
     * @return The replacement, or an empty list if [door] is not a wall.
     */
    private fun singleSwaps(door: GameObject, opening: Boolean): List<Swap> {
        val diagonal = when (door.objectType) {
            ObjectType.STRAIGHT_WALL -> false
            ObjectType.DIAGONAL_WALL -> true
            else -> return emptyList()
        }
        val type = typeOf(door)!!
        val offset = if (opening) openOffset(door.direction, diagonal) else closeOffset(door.direction, diagonal)
        return listOf(Swap(door,
                           if (opening) type.open else type.closed,
                           door.position.translate(offset.first, offset.second),
                           rotate(door.direction, if (opening) 1 else 3)))
    }

    /**
     * Computes the replacements for one leaf of a double door and, if it is standing there, its partner leaf.
     *
     * The left leaf swings a quarter turn counter-clockwise and the right leaf a quarter turn clockwise, so that they
     * open away from each other. Both leaves are replaced together.
     *
     * @return The replacements with the clicked leaf first, or an empty list if [door] is not a straight wall.
     */
    private fun doubleSwaps(world: World, door: GameObject, type: DoorType, opening: Boolean): List<Swap> {
        if (door.objectType != ObjectType.STRAIGHT_WALL) {
            return emptyList()
        }
        val swaps = arrayListOf(doubleSwap(door, type, opening))

        // The partner stands on the opposite side of the clicked leaf. Which side that is depends on the state.
        val side = type.side!!
        val away = if (opening) {
            val offset = closeOffset(door.direction, false)
            if (side == DoorSide.LEFT) offset else Pair(-offset.first, -offset.second)
        } else {
            val offset = openOffset(door.direction, false)
            Pair(-offset.first, -offset.second)
        }
        val partner = world.objects.findAll(door.position.translate(away.first, away.second))
            .filter { isPartner(it, side, opening) }
            .findFirst().orElse(null)
        if (partner != null) {
            swaps += doubleSwap(partner, typeOf(partner)!!, opening)
        }
        return swaps
    }

    /**
     * Computes the replacements for both leaves of a gate.
     *
     * A gate is two leaves that stand in a line. The hinge leaf ([DoorSide.LEFT]) swings a quarter turn around its
     * own corner exactly like a leaf of a double door, and the other leaf follows it so that the whole fence stays in
     * one line. The leaves stand in the same relative position in both states, so the partner is found the same way
     * when opening and closing.
     *
     * @return The replacements with the clicked leaf first, or an empty list if [door] is not a straight wall or its
     * partner leaf cannot be found.
     */
    private fun gateSwaps(world: World, door: GameObject, type: DoorType, opening: Boolean): List<Swap> {
        if (door.objectType != ObjectType.STRAIGHT_WALL) {
            return emptyList()
        }
        val side = type.side!!
        val toRight = closeOffset(door.direction, false)
        val away = if (side == DoorSide.LEFT) toRight else Pair(-toRight.first, -toRight.second)
        val partner = world.objects.findAll(door.position.translate(away.first, away.second))
            .filter { isPartner(it, side, opening) }
            .findFirst().orElse(null) ?: return emptyList()

        val hinge = if (side == DoorSide.LEFT) door else partner
        val far = if (side == DoorSide.LEFT) partner else door
        val hingeSwap = doubleSwap(hinge, typeOf(hinge)!!, opening)

        // The far leaf lines up with the hinge leaf: one tile further along the swing when opening, and back on the
        // right side of the hinge leaf when closing.
        val offset = if (opening) openOffset(hinge.direction, false) else closeOffset(hingeSwap.direction, false)
        val farSwap = Swap(far,
                           if (opening) typeOf(far)!!.open else typeOf(far)!!.closed,
                           hingeSwap.position.translate(offset.first, offset.second),
                           hingeSwap.direction)
        return if (door === hinge) listOf(hingeSwap, farSwap) else listOf(farSwap, hingeSwap)
    }

    /**
     * Computes the replacement for a single leaf of a double door.
     */
    private fun doubleSwap(leaf: GameObject, type: DoorType, opening: Boolean): Swap {
        val left = type.side == DoorSide.LEFT
        val offset = if (opening) {
            openOffset(leaf.direction, false)
        } else {
            val offset = closeOffset(leaf.direction, false)
            if (left) Pair(-offset.first, -offset.second) else offset
        }
        val turns = if (opening == left) 3 else 1
        return Swap(leaf,
                    if (opening) type.open else type.closed,
                    leaf.position.translate(offset.first, offset.second),
                    rotate(leaf.direction, turns))
    }

    /**
     * Determines if [obj] is a leaf of the opposite [side] that is in the same state as the clicked leaf.
     */
    private fun isPartner(obj: GameObject, side: DoorSide, opening: Boolean): Boolean {
        val type = typeOf(obj)
        return obj.objectType == ObjectType.STRAIGHT_WALL &&
                type != null &&
                type.side != null &&
                type.side != side &&
                obj.id == (if (opening) type.closed else type.open)
    }

    /**
     * The tile offset applied when a door opens. Diagonal doors use a different table than straight ones.
     */
    private fun openOffset(direction: ObjectDirection, diagonal: Boolean): Pair<Int, Int> {
        return if (diagonal) {
            when (direction) {
                ObjectDirection.WEST -> Pair(0, 1)
                ObjectDirection.NORTH -> Pair(1, 0)
                ObjectDirection.EAST -> Pair(0, -1)
                ObjectDirection.SOUTH -> Pair(-1, 0)
            }
        } else {
            when (direction) {
                ObjectDirection.WEST -> Pair(-1, 0)
                ObjectDirection.NORTH -> Pair(0, 1)
                ObjectDirection.EAST -> Pair(1, 0)
                ObjectDirection.SOUTH -> Pair(0, -1)
            }
        }
    }

    /**
     * The tile offset applied when a door closes. This is the inverse of [openOffset] after the direction has been
     * rotated.
     */
    private fun closeOffset(direction: ObjectDirection, diagonal: Boolean): Pair<Int, Int> {
        return if (diagonal) {
            when (direction) {
                ObjectDirection.WEST -> Pair(1, 0)
                ObjectDirection.NORTH -> Pair(0, -1)
                ObjectDirection.EAST -> Pair(-1, 0)
                ObjectDirection.SOUTH -> Pair(0, 1)
            }
        } else {
            when (direction) {
                ObjectDirection.WEST -> Pair(0, 1)
                ObjectDirection.NORTH -> Pair(1, 0)
                ObjectDirection.EAST -> Pair(0, -1)
                ObjectDirection.SOUTH -> Pair(-1, 0)
            }
        }
    }

    /**
     * Rotates [direction] by [quarterTurns] quarter turns, in the order west, north, east, south.
     */
    private fun rotate(direction: ObjectDirection, quarterTurns: Int): ObjectDirection {
        return ObjectDirection.ALL[(direction.id + quarterTurns) % 4]!!
    }
}
