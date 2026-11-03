package game.skill.agility.roguesDen

import api.predef.*
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject

/**
 * Where the Rogues' Den maze's traps, blades and regions lie, read from the map's own objects when the server starts.
 *
 * @author TheLining
 */
object RoguesDenMap {

    /**
     * The searchable traps: three floor kinds and three wall kinds, each with its own seqs and message.
     */
    enum class Trap(val id: Int, val wall: Boolean, val trapSeq: Int, val playerSeq: Int) {

        /**
         * AGILITYARENA_FLOORSPIKES_ACTIVATE with AGILITYARENA_PLAYER_SPIKED.
         */
        FLOOR_SPIKES(7227, false, 1111, 1113),

        /**
         * ROGUE_EXPLOSION with ROGUESDEN_PLAYER_EXPLOSION.
         */
        FLOOR_EXPLOSION(7230, false, 2202, 2242),

        /**
         * ROGUE_SPRINGY_DEATH with ROGUESDEN_SPRING_TRAP.
         */
        FLOOR_SPRING(7245, false, 2207, 2239),

        /**
         * ROGUE_WALL_SPIKES with AGILITYARENA_PLAYER_SPIKED.
         */
        WALL_SPIKES(7228, true, 2195, 1113),

        /**
         * ROGUE_POISON_GAS with ROGUESDEN_PLAYER_POISON_CHOKE.
         */
        WALL_GAS(7229, true, 2200, 2241),

        /**
         * ROGUE_WALL_CRUSH with AGILITYARENA_PLAYER_SPIKEDBACK.
         */
        WALL_CRUSHER(7249, true, 2211, 1114);

        companion object {
            val BY_ID = values().associateBy { it.id }
        }
    }

    /**
     * A placed trap and the tiles that set it off.
     */
    class PlacedTrap(val obj: GameObject, val type: Trap, val triggers: List<Position>)

    /**
     * Spinning blades on pedestals (3x3).
     */
    const val SPINNING_BLADES = 7224

    /**
     * The obstacles crossed by walking over them: pendulums, floor blades, wall scythes and wall saws.
     */
    private val JUMPS = setOf(7241, 7252, 7225, 7248)

    /**
     * The eight directions a player can step in.
     */
    private val STEPS = Direction.NESW + Direction.WNES_DIAGONAL

    /**
     * The maze and gallery box.
     */
    fun inBox(p: Position) = p.x in 2944..3071 && p.y in 4992..5119 && p.z in 1..2

    /**
     * Traps by each tile that sets one off.
     */
    val traps = HashMap<Position, MutableList<PlacedTrap>>()

    /**
     * Traps by their object's own tile, for searches.
     */
    val trapsAt = HashMap<Position, PlacedTrap>()

    /**
     * The tiles under the spinning blades.
     */
    val blades = HashSet<Position>()

    /**
     * For each tile of a pendulum, floor blade, wall scythe or wall saw, the crossing it belongs to. Touching
     * pieces of one kind form one crossing, so a row of pendulums or a corridor of blades is paid for once.
     */
    val crossings = HashMap<Position, Int>()

    /**
     * The experience multiplier of each tile, for players leaving from there.
     */
    val regions = HashMap<Position, Double>()

    /**
     * The tiles an object covers on its floor.
     */
    fun footprint(obj: GameObject): List<Position> {
        val def = obj.def()
        val turned = obj.direction.id % 2 == 1
        val width = if (turned) def.sizeY else def.sizeX
        val length = if (turned) def.sizeX else def.sizeY
        return (0 until width).flatMap { dx -> (0 until length).map { dy -> obj.position.translate(dx, dy) } }
    }

    /**
     * Reads the maze from the world's objects.
     */
    fun load() {
        val jumpTiles = HashMap<Position, Int>()
        for (obj in world.objects.staticIterator()) {
            if (!inBox(obj.position) || obj.position.z != 1) continue
            val trap = Trap.BY_ID[obj.id]
            when {
                trap != null -> {
                    // A wall-mounted spike trap fires on the tile its face looks onto; the floor traps and the long
                    // wall traps on their own tiles.
                    val triggers = if (trap == Trap.WALL_SPIKES) {
                        listOf(obj.position.translate(1, obj.direction.toNormalDirection()))
                    } else footprint(obj)
                    val placed = PlacedTrap(obj, trap, triggers)
                    trapsAt[obj.position] = placed
                    triggers.forEach { traps.getOrPut(it) { ArrayList() } += placed }
                }
                obj.id == SPINNING_BLADES -> blades += footprint(obj)
                obj.id in JUMPS -> footprint(obj).forEach { jumpTiles[it] = obj.id }
            }
        }
        var next = 0
        for (tile in jumpTiles.keys) {
            if (tile in crossings) continue
            val kind = jumpTiles[tile]
            val queue = ArrayDeque(listOf(tile))
            crossings[tile] = next
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                for (dx in -1..1) for (dy in -1..1) {
                    val near = at.translate(dx, dy)
                    if (jumpTiles[near] == kind && near !in crossings) {
                        crossings[near] = next
                        queue += near
                    }
                }
            }
            next++
        }
        for (region in RoguesDen.REGIONS) {
            val seen = HashSet<Position>()
            val queue = ArrayDeque(listOf(region.seed))
            seen += region.seed
            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                if (at !in regions) regions[at] = region.multiplier(at)
                for (dir in STEPS) {
                    val near = at.translate(1, dir)
                    if (near in seen || !inBox(near) || !world.collisionManager.traversable(at, EntityType.PLAYER, dir))
                        continue
                    seen += near
                    queue += near
                }
            }
        }
    }
}
