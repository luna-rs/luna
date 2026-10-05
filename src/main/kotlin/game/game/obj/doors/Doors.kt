package game.obj.doors

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*
import java.util.*

object Doors {

    /**
     * Maps both the closed id and the open id of every door to its [DoorType].
     */
    private val byId: HashMap<Int, DoorType> = HashMap()

    /**
     * Every loaded [DoorType].
     */
    var all: List<DoorType> = emptyList()
        private set

    /**
     * The [DoorType]s that are leaves of a gate rather than a double door.
     */
    private val gateTypes: MutableSet<DoorType> = Collections.newSetFromMap(IdentityHashMap())

    /**
     * The [DoorType]s that are curtains, which are replaced in place.
     */
    private val curtainTypes: MutableSet<DoorType> = Collections.newSetFromMap(IdentityHashMap())

    /**
     * Doors that are currently away from their home state, mapped to the object they replaced. A door only reverts
     * on its own if it is still in this map when its timer expires.
     */
    private val displaced: IdentityHashMap<GameObject, GameObject> = IdentityHashMap()

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
     * Loads all door definitions, replacing any previously loaded ones.
     *
     * @param singles The single door pairs to load.
     * @param doubles The double door leaves to load.
     * @param gates The gate leaves to load. The [DoorSide.LEFT] leaf of a gate is its hinge.
     * @param curtains The curtain pairs to load.
     */
    fun load(singles: Array<DoorType>, doubles: Array<DoorType>, gates: Array<DoorType>, curtains: Array<DoorType>) {
        byId.clear()
        gateTypes.clear()
        gateTypes.addAll(gates)
        curtainTypes.clear()
        curtainTypes.addAll(curtains)
        for (type in singles + doubles + gates + curtains) {
            require((type.side != null) == (type in doubles || type in gates)) {
                "Door ${type.closed} must ${if (type.side != null) "not " else ""}have a side."
            }
            require(byId.putIfAbsent(type.closed, type) == null) { "Duplicate door id ${type.closed} in door files." }
            require(byId.putIfAbsent(type.open, type) == null) { "Duplicate door id ${type.open} in door files." }
        }
        all = (singles + doubles + gates + curtains).toList()
    }

    /**
     * @return The [DoorType] that [id] belongs to, or `null` if it is not a known door.
     */
    fun typeOf(id: Int): DoorType? = byId[id]

    /**
     * Opens [door] if it is closed, or closes it if it is open.
     *
     * Single doors can be straight or diagonal walls. An opening door moves one tile and turns a quarter turn
     * clockwise. A closing door does the exact inverse, so the position and direction of the original door can always
     * be recovered from the door that was clicked. Double doors (straight walls only) swing both leaves together, see
     * [doubleSwaps]. Doors that were moved away from their home state revert on their own after
     * [DoorType.durationOrDefault] ticks.
     *
     * @param world The world.
     * @param plr The player that clicked the door.
     * @param door The door that was clicked.
     */
    fun toggle(world: World, plr: Player, door: GameObject) {
        val type = typeOf(door.id) ?: return
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

        // The clicked door is always first. Fails if it was already replaced, so a stale click cannot spawn a duplicate.
        val self = swaps.first()
        if (!world.removeObject(self.old)) {
            return
        }
        if (!curtain && door.objectType == ObjectType.DIAGONAL_WALL && plr.position == self.position) {
            // A diagonal door swings across the tile the player is standing on, so move them out of the way.
            val away = if (opening) openPlayerOffset(door.direction) else closePlayerOffset(door.direction)
            plr.move(self.position.translate(away.first, away.second))
        }
        plr.playSound(if (opening) type.openSoundOrDefault else type.closeSoundOrDefault)

        val replaced = ArrayList<Pair<GameObject, GameObject>>()
        for (swap in swaps) {
            if (swap !== self && !world.removeObject(swap.old)) {
                continue
            }
            val new = world.addObject(swap.id, swap.position, swap.old.objectType, swap.direction)
            replaced += Pair(new, swap.old)
        }

        // Leaves that were away from home are home again, and have nothing left to revert.
        val wasAwayFromHome = swaps.count { displaced.remove(it.old) != null } > 0
        if (wasAwayFromHome) {
            return
        }
        for ((new, old) in replaced) {
            displaced[new] = old
            world.scheduleOnce(type.durationOrDefault) {
                if (displaced.remove(new) != null && world.removeObject(new)) {
                    world.addObject(old.id, old.position, old.objectType, old.direction)
                }
            }
        }
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
        val type = typeOf(door.id)!!
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
            swaps += doubleSwap(partner, typeOf(partner.id)!!, opening)
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
        val hingeSwap = doubleSwap(hinge, typeOf(hinge.id)!!, opening)

        // The far leaf lines up with the hinge leaf: one tile further along the swing when opening, and back on the
        // right side of the hinge leaf when closing.
        val offset = if (opening) openOffset(hinge.direction, false) else closeOffset(hingeSwap.direction, false)
        val farSwap = Swap(far,
                           if (opening) typeOf(far.id)!!.open else typeOf(far.id)!!.closed,
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
        val type = typeOf(obj.id)
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
     * How far a player standing on the destination tile of an opening diagonal door is moved.
     */
    private fun openPlayerOffset(direction: ObjectDirection): Pair<Int, Int> {
        return when (direction) {
            ObjectDirection.WEST -> Pair(-1, 0)
            ObjectDirection.NORTH -> Pair(0, 1)
            ObjectDirection.EAST -> Pair(1, 0)
            ObjectDirection.SOUTH -> Pair(0, -1)
        }
    }

    /**
     * How far a player standing on the destination tile of a closing diagonal door is moved.
     */
    private fun closePlayerOffset(direction: ObjectDirection): Pair<Int, Int> {
        return when (direction) {
            ObjectDirection.WEST -> Pair(1, 1)
            ObjectDirection.NORTH -> Pair(1, -1)
            ObjectDirection.EAST -> Pair(-1, -1)
            ObjectDirection.SOUTH -> Pair(-1, 1)
        }
    }

    /**
     * Rotates [direction] by [quarterTurns] quarter turns, in the order west, north, east, south.
     */
    private fun rotate(direction: ObjectDirection, quarterTurns: Int): ObjectDirection {
        return ObjectDirection.ALL[(direction.id + quarterTurns) % 4]!!
    }
}
