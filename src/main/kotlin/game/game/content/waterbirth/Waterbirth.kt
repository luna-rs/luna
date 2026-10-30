package game.content.waterbirth

import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import java.util.IdentityHashMap

/**
 * Waterbirth's cooperative pressure doors and attackable supports.
 * Positions and assets are from LostCityRS/Content 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50,
 * maps/m38_158.jm2, maps/m39_158.jm2 and the 377 packs. That revision has no gameplay scripts here.
 * The two-player and cross-corridor mechanics are corroborated by later dungeon guides; the 30/60-second
 * reset windows below are Luna policy. Modern pet-rock and Agility bypasses are deliberately excluded.
 *
 * @author lare96
 */
object Waterbirth {
    const val OPEN_DOOR = 8963
    const val BLOCKER = 8967
    const val DOOR_TICKS = 50
    const val SUPPORT_TICKS = 100

    /**
     * The three rotated 1x3 door footprints and their two western pressure pads.
     *
     * @author lare96
     */
    enum class Gate(val id: Int, val position: Position) {
        NORTH(8958, Position(2491, 10162)),
        MIDDLE(8959, Position(2491, 10146)),
        SOUTH(8960, Position(2491, 10130));
        val pads = listOf(position.translate(-1, 0), position.translate(-1, 2))
        val west = position.translate(-1, 1)
        val east = position.translate(1, 1)
        fun occupies(tile: Position): Boolean = tile.z == position.z && tile.x == position.x && tile.y in position.y..position.y + 2
    }

    /**
     * The three NPC props and their matching invisible walk blockers around the central ladder room.
     *
     * @author lare96
     */
    enum class Support(val npc: Int, val position: Position, val outside: Position, val inside: Position) {
        SOUTH(2440, Position(2545, 10141), Position(2545, 10140), Position(2545, 10142)),
        WEST(2443, Position(2543, 10143), Position(2542, 10143), Position(2544, 10143)),
        NORTH(2446, Position(2545, 10145), Position(2545, 10146), Position(2545, 10144))
    }

    /**
     * Retains the original gate shape until its safe timed reset.
     *
     * @author lare96
     */
    private class OpenGate(val closed: GameObject, var opened: GameObject, var remaining: Int = DOOR_TICKS)

    /**
     * Couples a fallen prop's visual with the collision object it must restore.
     *
     * @author lare96
     */
    private class FallenSupport(val blocker: GameObject, val visible: Npc?, var remaining: Int = SUPPORT_TICKS)

    /**
     * Owns the shared game-thread gate and prop timers for one world.
     *
     * @author lare96
     */
    private class Runtime {
        var started = false
        val gates = HashMap<Gate, OpenGate>()
        val supports = HashMap<Support, FallenSupport>()
    }
    private val runtimes = IdentityHashMap<World, Runtime>()

    fun gate(id: Int, position: Position): Gate? = Gate.entries.firstOrNull { it.id == id && it.position == position }
    fun support(npc: Npc): Support? = Support.entries.firstOrNull { it.npc == npc.id && it.position == npc.position }
    fun support(position: Position): Support? = Support.entries.firstOrNull { it.position == position }

    /**
     * Clears automatic retaliation without pursuit, damage, an attack animation or walking off the prop's tile.
     */
    fun idleAttack(npc: Npc, target: Mob): CombatAttack<Npc> =
        object : CombatAttack<Npc>(npc, target, InteractionPolicy.UNSPECIFIED, 1) {
            override fun attack() {}
            override fun calculateDamage(other: Mob): CombatDamage = CombatDamageRequest.zero(npc, other, CombatDamageType.MELEE).resolve()
            override fun onAttack(damage: CombatDamage): CombatDamage? { npc.combat.target = null; return null }
        }

    private fun occupied(world: World, position: Position): Boolean = world.locator.findPlayers(position, 0) {
        it.state == EntityState.ACTIVE && it.isAlive && it.position == position
    }.isNotEmpty()

    fun padsHeld(world: World, gate: Gate): Boolean = gate.pads.all { occupied(world, it) }
    fun open(world: World, gate: Gate): Boolean = world.objects.findAll(gate.position).anyMatch { it.id == OPEN_DOOR }
    fun open(world: World, support: Support): Boolean = world.objects.findAll(support.position).noneMatch { it.id == BLOCKER }

    /**
     * Schedules one game-thread update loop per world. Only actual living players count as pressure-pad occupants.
     */
    fun start(world: World) {
        val runtime = runtimes.getOrPut(world) { Runtime() }
        if (runtime.started) return
        runtime.started = true
        world.schedule(1) { process(world) }
    }

    private fun copy(world: World, original: GameObject, id: Int): GameObject =
        GameObject.createDynamic(world.context, id, original.position, original.objectType, original.direction,
            ChunkUpdatableView.globalView())

    private fun replace(world: World, original: GameObject, id: Int): GameObject? {
        if (!world.objects.unregister(original)) return null
        val replacement = copy(world, original, id)
        if (world.objects.register(replacement)) return replacement
        check(world.objects.register(copy(world, original, original.id))) { "Could not restore Waterbirth object at ${original.position}" }
        return null
    }

    /**
     * Opens a held door; an adventurer leaving a corridor can operate it from the eastern side as a return route.
     * Passage itself is ordinary walking through the temporary open object, never a coordinate jump.
     */
    fun operate(player: Player, door: GameObject): Boolean {
        val gate = gate(door.id, door.position) ?: return false
        if (!player.isAlive || player.state != EntityState.ACTIVE || !player.position.isWithinDistance(door.position, 4)) return false
        if (player.position.x <= gate.position.x && !padsHeld(player.world, gate)) {
            player.sendMessage("You need someone standing on the other pressure pad.")
            return false
        }
        return openGate(player.world, gate, door)
    }

    private fun openGate(world: World, gate: Gate, door: GameObject): Boolean {
        val runtime = runtimes.getOrPut(world) { Runtime() }
        if (runtime.gates.containsKey(gate)) return true
        val opened = replace(world, door, OPEN_DOOR) ?: return false
        runtime.gates[gate] = OpenGate(door, opened)
        return true
    }

    /**
     * Accepts a prop hit from inside the ladder room, from an adjacent corridor with ranged or magic, or a
     * secondary ranged rebound whose original target was another support. Native combat owns ammunition,
     * accuracy and energy; the original direct hit must still satisfy the corridor rules.
     */
    fun canStrike(support: Support, position: Position, type: CombatDamageType, primary: Mob?, victim: Npc): Boolean {
        if (position.z != 0 || !position.isWithinDistance(support.position, 10)) return false
        if (position.x in 2544..2546 && position.y in 10142..10144) return true
        if (type != CombatDamageType.MAGIC && type != CombatDamageType.RANGED) return false
        if (type == CombatDamageType.RANGED && primary is Npc && primary !== victim &&
            Support.entries.any { it.npc == primary.id }) return true
        return when (support) {
            Support.WEST -> position.x >= 2543 && (position.y <= 10140 || position.y >= 10146)
            Support.NORTH, Support.SOUTH -> position.x <= 2542 && position.y in 10142..10144
        }
    }

    /**
     * Removes the collider only after a lethal native attack, then restores both the prop and blocker together.
     */
    fun destroyed(npc: Npc): Boolean {
        val support = support(npc) ?: return false
        val world = npc.world
        val runtime = runtimes.getOrPut(world) { Runtime() }
        if (runtime.supports.containsKey(support)) return false
        val blocker = world.objects.findAll(support.position).filter { it.id == BLOCKER }.findFirst().orElse(null) ?: return false
        if (!world.objects.unregister(blocker)) return false
        if (npc.state == EntityState.ACTIVE) world.npcs.remove(npc)
        val fallen = Npc(npc.context, support.npc + 2, support.position).apply { respawnTicks = -1 }
        val visible = if (world.npcs.add(fallen)) fallen else null
        runtime.supports[support] = FallenSupport(blocker, visible)
        return true
    }

    /**
     * Advances shared gates once per game tick. A reset waits for occupants to leave its collision tile.
     */
    fun process(world: World) {
        val runtime = runtimes.getOrPut(world) { Runtime() }
        for (gate in Gate.entries) {
            if (!runtime.gates.containsKey(gate) && padsHeld(world, gate)) {
                world.objects.findAll(gate.position).filter { it.id == gate.id }.findFirst().orElse(null)?.let {
                    openGate(world, gate, it)
                }
            }
        }
        val gates = runtime.gates.iterator()
        while (gates.hasNext()) {
            val (gate, state) = gates.next()
            if (padsHeld(world, gate)) state.remaining = DOOR_TICKS else state.remaining--
            if (state.remaining > 0 || (0..2).any { occupied(world, gate.position.translate(0, it)) }) continue
            if (state.opened.state != EntityState.ACTIVE || replace(world, state.opened, state.closed.id) != null) gates.remove()
        }
        val supports = runtime.supports.iterator()
        while (supports.hasNext()) {
            val (support, state) = supports.next()
            if (--state.remaining > 0 || occupied(world, support.position)) continue
            if (world.objects.findAll(support.position).noneMatch { it.id == BLOCKER } &&
                !world.objects.register(copy(world, state.blocker, BLOCKER))) continue
            val restored = Npc(world.context, support.npc, support.position).apply { respawnTicks = -1 }
            if (!world.npcs.add(restored)) continue
            state.visible?.takeIf { it.state == EntityState.ACTIVE }?.let { world.npcs.remove(it) }
            supports.remove()
        }
    }
}
