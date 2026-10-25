package game.content.castleWars

import api.predef.*
import api.predef.ext.*
import game.content.castleWars.CastleWarsMatch.Team
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorSide
import game.obj.doors.DoorType
import game.obj.doors.Doors
import game.skill.mining.Pickaxe
import game.skill.Skills
import io.luna.game.model.Position
import io.luna.game.model.EntityState
import io.luna.game.model.Direction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType
import java.util.Optional

/**
 * Owns siege objects for one shared match. All mutations and expiry callbacks run on the game thread.
 * Original map objects are retained so a new match never inherits damaged equipment.
 *
 * @author lare96
 */
class CastleWarsSiege(private val game: CastleWarsGame) {
    companion object {
        val tunnels = listOf(Position(2401, 9494), Position(2409, 9503), Position(2391, 9501), Position(2400, 9512))
        val mainDoors = listOf(
            DoorType(4423, 4425, DoorSide.RIGHT, null, null, 500),
            DoorType(4424, 4426, DoorSide.LEFT, null, null, 500),
            DoorType(4427, 4429, DoorSide.RIGHT, null, null, 500),
            DoorType(4428, 4430, DoorSide.LEFT, null, null, 500))
    }

    private val originals = linkedMapOf<Pair<Position, ObjectType>, GameObject>()
    private val replacements = linkedSetOf<GameObject>()
    private val barricades = linkedMapOf<Npc, Team>()
    private val burning = mutableMapOf<Npc, Int>()
    private val blockers = mutableMapOf<Npc, GameObject>()
    private val cooldowns = mutableMapOf<Player, Int>()
    private val doorHealth = intArrayOf(100, 100)
    private val brokenCatapult = BooleanArray(2)
    private val openSideDoor = BooleanArray(2)
    private var clock = 0
    private var generation = 0

    fun bits(team: Team): Int {
        var bits = doorHealth[team.ordinal]
        if (openSideDoor[team.ordinal]) bits = bits or (1 shl 7)
        if (brokenCatapult[team.ordinal]) bits = bits or (1 shl 10)
        for (i in 0..1) {
            val pos = tunnels[team.ordinal * 2 + i]
            if (world.locator.findObjectsOnTile(pos) { it.id == 4439 }.isNotEmpty()) bits = bits or (1 shl (8 + i))
        }
        return bits
    }

    fun reset() {
        generation++
        barricades.keys.toList().forEach(::removeBarricade)
        replacements.forEach { world.removeObject(it) }
        originals.values.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
        replacements.clear()
        originals.clear()
        cooldowns.clear()
        doorHealth.fill(100)
        brokenCatapult.fill(false)
        openSideDoor.fill(false)
    }

    fun tick() {
        clock++
        barricades.keys.toList().filter {
            it.state != EntityState.ACTIVE || !it.isAlive || (burning[it] ?: Int.MAX_VALUE) <= clock
        }.forEach(::removeBarricade)
        cooldowns.entries.removeIf { it.value <= clock }
    }

    private fun allowed(player: Player, position: Position, range: Int = 2): Boolean =
        game.match.isPlaying(player) && player.isAlive && CastleWarsGame.inArena(position) &&
            player.position.isWithinDistance(position, range)

    private fun ready(player: Player, ticks: Int): Boolean {
        if ((cooldowns[player] ?: 0) > clock) return false
        cooldowns[player] = clock + ticks
        return true
    }

    private fun remember(obj: GameObject) {
        originals.putIfAbsent(obj.position to obj.objectType, obj)
    }

    private fun replace(obj: GameObject, id: Int): GameObject {
        remember(obj)
        return world.addObject(id, obj.position, obj.objectType, obj.direction).also { replacements += it }
    }

    private fun later(ticks: Int, action: () -> Unit) {
        val version = generation
        world.scheduleOnce(ticks) { if (generation == version) action() }
    }

    private fun current(obj: GameObject): Boolean =
        world.locator.findObjectsOnTile(obj.position) { it === obj }.isNotEmpty()

    fun spawnDoor(player: Player, door: GameObject) {
        val owner = if (door.id == 4469) Team.SARADOMIN else Team.ZAMORAK
        if (!allowed(player, door.position)) return
        player.submitAction(object : DoorPassageAction(player, door, DoorType(door.id, door.id, null, null, null, 1)) {
            override fun checkAccess(entering: Boolean): Boolean =
                game.match.isPlaying(player) && game.match.team(player) == owner && game.match.carried(player) == null
        })
    }

    fun mainDoor(player: Player, obj: GameObject, attack: Boolean = false, repair: Boolean = false) {
        if (!allowed(player, obj.position)) return
        val owner = if (obj.id in 4423..4426 || obj.id in 4431..4432) Team.SARADOMIN else Team.ZAMORAK
        val own = game.match.team(player) == owner
        val types = mainDoors.filter { (it.closed <= 4424) == (owner == Team.SARADOMIN) }
        val leaves = world.locator.findObjects(obj.position, 5) { candidate ->
            candidate.id in types.flatMap { listOf(it.closed, it.open) } ||
                candidate.id in (if (owner == Team.SARADOMIN) 4431..4432 else 4433..4434)
        }
        if (repair) {
            if (!own || !player.inventory.contains(4051)) return
            doorHealth[owner.ordinal] = 100
            // Restore both original leaves, including their closed positions and orientations.
            val homes = originals.values.filter { old -> types.any { it.closed == old.id } }
            leaves.forEach { world.removeObject(it) }
            homes.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            player.sendMessage("You repair your castle doors.")
            return
        }
        if (attack) {
            if (own || !Doors.isEntering(obj.position, obj.direction, player.position)) return
            if (player.combat.attackDelay > 0 || !ready(player, player.combat.weapon.speed.coerceAtLeast(1))) return
            player.combat.attackDelay = player.combat.weapon.speed.coerceAtLeast(1)
            val ranged = player.combat.weapon.isRanged
            val max = CombatFormula.calculatePhysicalMaxHit(player,
                if (ranged) CombatFormula.PhysicalType.RANGED else CombatFormula.PhysicalType.MELEE)
            val scale = when (player.combat.styleDef.bonus) {
                EquipmentBonus.STAB_ATTACK -> 25
                EquipmentBonus.SLASH_ATTACK -> 50
                else -> 35
            }
            val damage = rand().nextInt(max + 1) * scale / 100
            doorHealth[owner.ordinal] = (doorHealth[owner.ordinal] - damage).coerceAtLeast(0)
            player.animation(io.luna.game.model.mob.block.Animation(player.combat.getAttackAnimation(
                if (ranged) CombatDamageType.RANGED else CombatDamageType.MELEE)))
            if (doorHealth[owner.ordinal] > 0) return
        } else if (!own && Doors.isEntering(obj.position, obj.direction, player.position)) {
            player.sendMessage("It's shut; you'll have to break it down.")
            return
        }
        val closed = leaves.filter { leaf -> types.any { it.closed == leaf.id } }
        for (leaf in closed) {
            remember(leaf)
            val type = types.first { it.closed == leaf.id }
            val swap = Doors.doubleSwap(leaf, type, true)
            world.removeObject(leaf)
            val id = if (!attack) swap.id else if (owner == Team.SARADOMIN) {
                if (type.side == DoorSide.LEFT) 4431 else 4432
            } else if (type.side == DoorSide.LEFT) 4433 else 4434
            val open = world.addObject(id, swap.position, leaf.objectType, swap.direction)
            replacements += open
            if (!attack) later(500) {
                if (current(open) && world.removeObject(open)) world.addObject(leaf.id, leaf.position, leaf.objectType, leaf.direction)
            }
        }
    }

    fun sideDoor(player: Player, obj: GameObject, pickLock: Boolean = false) {
        if (!allowed(player, obj.position) || !ready(player, 4)) return
        val owner = if (obj.id <= 4466) Team.SARADOMIN else Team.ZAMORAK
        val closed = if (owner == Team.SARADOMIN) 4465 else 4467
        if (obj.id == closed + 1) {
            val original = originals.values.firstOrNull { it.id == closed && it.position.isWithinDistance(obj.position, 2) }
                ?: return
            world.removeObject(obj)
            world.addObject(original.id, original.position, original.objectType, original.direction)
            openSideDoor[owner.ordinal] = false
            return
        }
        if (game.match.team(player) != owner && (!pickLock || !Skills.success(15, 255, player.thieving.level))) {
            player.sendMessage("The door is locked against you.")
            return
        }
        val swap = Doors.singleSwap(obj, DoorType(closed, closed + 1, null, null, null, 2000), true) ?: return
        remember(obj)
        world.removeObject(obj)
        val open = world.addObject(swap.id, swap.position, obj.objectType, swap.direction)
        replacements += open
        openSideDoor[owner.ordinal] = true
        later(2000) {
            if (current(open)) {
                world.removeObject(open)
                world.addObject(obj.id, obj.position, obj.objectType, obj.direction)
                openSideDoor[owner.ordinal] = false
            }
        }
    }

    fun placeBarricade(player: Player) {
        val position = player.position
        val team = game.match.team(player) ?: return
        if (!allowed(player, position) || CastleWarsGame.inSpawnRoom(position) ||
            barricades.values.count { it == team } >= 10 || blockers.values.any { it.position == position } ||
            world.locator.findObjectsOnTile(position) { it.objectType == ObjectType.DEFAULT }.isNotEmpty() ||
            world.collisionManager.isBlocked(position, false)) return
        if (!player.inventory.remove(4053)) return
        val npc = world.addNpc(1532 + team.ordinal * 2, position.x, position.y, position.z)
        npc.defaultDirection = Optional.of(Direction.SOUTH)
        npc.respawnTicks = -1
        npc.combat.isDisabled = true
        barricades[npc] = team
        // NPCs do not obstruct player route finding in Luna. A colocated invisible wall supplies clipping.
        blockers[npc] = world.addObject(83, position)
    }

    fun isBarricade(npc: Npc): Boolean = npc in barricades

    fun barricade(player: Player, npc: Npc, item: Int) {
        if (!allowed(player, npc.position) || npc !in barricades || !player.inventory.contains(item)) return
        when (item) {
            4045 -> if (player.inventory.remove(item)) removeBarricade(npc)
            590 -> { burning[npc] = clock + 10; npc.transform(1533 + barricades.getValue(npc).ordinal * 2) }
            1929 -> if (burning.remove(npc) != null) {
                player.inventory.replace(1929, 1925)
                npc.transform(1532 + barricades.getValue(npc).ordinal * 2)
            }
        }
    }

    private fun removeBarricade(npc: Npc) {
        barricades.remove(npc)
        burning.remove(npc)
        blockers.remove(npc)?.let { world.removeObject(it) }
        world.removeNpc(npc)
    }

    fun rope(player: Player, wall: GameObject) {
        if (!allowed(player, wall.position) || Doors.isEntering(wall.position, wall.direction, player.position) ||
            world.locator.findObjectsOnTile(player.position) { it.id == 4444 }.isNotEmpty() ||
            !player.inventory.remove(954)) return
        val rope = world.addObject(4444, player.position, ObjectType.STRAIGHT_INSIDE_WALL_DECORATION,
            io.luna.game.model.`object`.ObjectDirection.ALL[(wall.direction.id + 2) % 4]!!)
        replacements += rope
        later(500) { if (current(rope)) world.removeObject(rope); replacements.remove(rope) }
    }

    fun climbRope(player: Player, rope: GameObject) {
        if (!allowed(player, rope.position, 1) || player.status.isImmobilized()) return
        val direction = io.luna.game.model.`object`.ObjectDirection.ALL[(rope.direction.id + 3) % 4]!!
        val destination = player.position.translate(1, direction.toNormalDirection())
        if (CastleWarsGame.inArena(destination)) player.move(destination)
    }

    fun rocks(player: Player, obj: GameObject, explosive: Boolean = false) {
        if (!allowed(player, obj.position) || !ready(player, 4)) return
        if (explosive) {
            if (!player.inventory.remove(4045)) return
        } else {
            val pick = Pickaxe.computePickType(player) ?: return Pickaxe.sendNoPickaxeMessage(player)
            player.animation(pick.animation)
            if (!Skills.success(15, 255, player.mining.level)) return
        }
        if (obj.id == 4448) {
            val pos = tunnels.minBy { it.computeLongestDistance(obj.position) }
            val tunnel = world.locator.findObjectsOnTile(pos) { it.id in 4437..4439 }.firstOrNull() ?: return
            if (tunnel.id != 4439) return
            replace(tunnel, 4437)
            game.match.players().keys.filter { it.position.isWithinDistance(pos, 1) }.forEach { it.damage(255) }
        } else if (obj.id in 4437..4438) replace(obj, obj.id + 1)
    }

    fun catapult(player: Player, obj: GameObject, item: Int = -1) {
        if (!allowed(player, obj.position, 4)) return
        val owner = if (obj.id in setOf(4382, 4386, 4905)) Team.SARADOMIN else Team.ZAMORAK
        val own = game.match.team(player) == owner
        when {
            obj.id in 4385..4386 && own && player.inventory.contains(4051) -> {
                replace(obj, if (owner == Team.SARADOMIN) 4382 else 4381)
                brokenCatapult[owner.ordinal] = false
            }
            obj.id in 4904..4905 && item == 1929 && player.inventory.replace(1929, 1925) ->
                replace(obj, if (owner == Team.SARADOMIN) 4382 else 4381)
            obj.id in 4381..4382 && !own && item == 4045 && player.inventory.remove(4045) -> {
                replace(obj, if (owner == Team.SARADOMIN) 4386 else 4385)
                brokenCatapult[owner.ordinal] = true
            }
            obj.id in 4381..4382 && !own && item == 590 && player.inventory.contains(590) -> {
                val fire = replace(obj, if (owner == Team.SARADOMIN) 4905 else 4904)
                later(10) {
                    if (world.locator.findObjectsOnTile(fire.position) { it === fire }.isNotEmpty()) {
                        replace(fire, if (owner == Team.SARADOMIN) 4386 else 4385)
                        brokenCatapult[owner.ordinal] = true
                    }
                }
            }
            obj.id in 4381..4382 && own && player.inventory.contains(4043) -> player.overlays.open(CatapultInterface(obj))
        }
    }

    fun fire(player: Player, aim: CatapultInterface) {
        if (!allowed(player, aim.catapult.position, 4) || !ready(player, 5) ||
            world.locator.findObjectsOnTile(aim.catapult.position) { it === aim.catapult }.isEmpty() ||
            !player.inventory.remove(4043)) return
        val team = game.match.team(player) ?: return
        val origin = if (team == Team.SARADOMIN) Position(2411, 3092) else Position(2388, 3115)
        val target = origin.translate(aim.x + rand().nextInt(-2, 3), aim.y + rand().nextInt(-2, 3))
        player.overlays.closeWindows()
        later(3) {
            game.match.players().keys.filter { it.position.isWithinDistance(target, 2) && !CastleWarsGame.inSpawnRoom(it.position) }
                .forEach { it.damage(rand().nextInt(15, 31)) }
        }
    }
}

/**
 * Keeps a catapult's aiming state bound to the open interface and the original world object.
 *
 * @author lare96
 */
class CatapultInterface(val catapult: GameObject) : StandardInterface(11169) {
    var x = 0
    var y = 0

    override fun onOpen(plr: Player) { refresh(plr) }

    fun refresh(player: Player) {
        val digits = listOf(kotlin.math.abs(y) / 10, kotlin.math.abs(y) % 10,
            kotlin.math.abs(x) / 10, kotlin.math.abs(x) % 10)
        digits.forEachIndexed { index, digit ->
            player.queue(io.luna.net.msg.out.WidgetItemModelMessageWriter(11317 + index, 100, 4056 + digit))
        }
        val sara = CastleWars.game.match.team(player) == Team.SARADOMIN
        player.queue(io.luna.net.msg.out.WidgetPositionMessageWriter(11332,
            if (sara) 100 - 2 * x else 2 * x, if (sara) 100 - 2 * y else 2 * y))
    }
}
