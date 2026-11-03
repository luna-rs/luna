package game.skill.agility.roguesDen

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.skill.agility.roguesDen.RoguesDenMap.PlacedTrap
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation

/**
 * Brian O'Richard's maze in the Rogues' Den under Burthorpe. Each obstacle spends Agility or Thieving levels as
 * points, levels don't come back until the player leaves, and leaving pays both skills for the points spent, more
 * the further in the player got.
 *
 * @author TheLining
 */
object RoguesDen {

    /**
     * Brian O'Richard, head of the maze.
     */
    const val BRIAN = 2266

    /**
     * The Mystic jewel, the only item a player may take in.
     */
    const val JEWEL = 5561

    /**
     * Flash powder, picked up beside the guards.
     */
    const val FLASH_POWDER = 5559

    /**
     * The mosaic tile that completes the puzzle door's picture, and the three that don't.
     */
    const val RIGHT_TILE = 5568
    val TILES = listOf(RIGHT_TILE, 5569, 5570, 5571)

    /**
     * The five pieces of rogue armour: mask, top, trousers, gloves and boots.
     */
    val ROGUE_PIECES = listOf(5554, 5553, 5555, 5556, 5557)

    /**
     * Items that belong to the maze and are taken back when a run ends.
     */
    private val MAZE_ITEMS = TILES + listOf(JEWEL, FLASH_POWDER)

    /**
     * Where a run starts and ends: the doorway's maze side, and the lobby tile south of the doorway.
     */
    val INSIDE = Position(3056, 4992, 1)
    val START = Position(3056, 4990, 1)

    /**
     * The room round the centre safes.
     */
    private fun inSafeRoom(p: Position) = p.x in 3010..3022 && p.y in 5041..5054 && p.z == 1

    /**
     * Brian's lines when a player comes out.
     */
    private val NOT_FAR = arrayOf("Oh dear, you really didn't get very far did you?")
    private val NOT_BAD = arrayOf("Well you didn't do too badly, but you didn't make it", "nearly as far as others have.")
    private val SO_FAR = arrayOf("Amazing, you made it so far!")

    /**
     * What a player does to end a run.
     */
    enum class Exit { FAIL, JEWEL, SAFE, LOGIN }

    /**
     * A region of the maze: every tile that walking from [seed] reaches without crossing an obstacle, with
     * [multiplier] deciding each tile's experience multiplier.
     */
    class Region(val seed: Position, val multiplier: (Position) -> Double) {
        constructor(x: Int, y: Int, value: Double) : this(Position(x, y, 1), { value })
    }

    /**
     * The regions, earliest first. The first eight values follow the route to the passage by the shortcut, the chest
     * room is 4.2 and the safes pay 12 per point; the rest rise along each route toward the centre.
     */
    val REGIONS = listOf(
        // The first room: nothing until past its pendulum.
        Region(Position(3056, 4993, 1)) { if (it.x >= 3040) 0.0 else 0.2 },
        Region(3020, 4996, 0.2),
        // The second room: the first grill, past the hanging ledge, past the floor blades, past the pendulum row.
        Region(Position(2975, 5010, 1)) {
            when {
                it.x >= 2989 -> 0.3
                it.y >= 5028 -> 1.2
                it.y >= 5020 -> 1.0
                else -> 0.4
            }
        },
        Region(2968, 5011, 0.4),
        // Across the beam, then past the west corridor's wall traps.
        Region(Position(2962, 5053, 1)) { if (it.y >= 5056) 5.0 else 3.0 },
        Region(2971, 5064, 6.0),
        // Through the passage by the shortcut and past its floor blade.
        Region(Position(2956, 5080, 1)) { if (it.y >= 5076) 6.8 else 5.0 },
        Region(2962, 5105, 7.5),
        Region(2972, 5094, 7.5),
        Region(2970, 5085, 8.0),
        Region(2968, 5079, 8.0),
        Region(3010, 5088, 9.0),
        Region(3027, 5080, 10.0),
        // The grill room's cells.
        Region(3033, 5079, 10.5), Region(3035, 5068, 10.5), Region(3033, 5073, 10.5), Region(3035, 5073, 10.5),
        Region(3038, 5070, 10.5), Region(3038, 5073, 10.5), Region(3038, 5077, 10.5), Region(3042, 5067, 10.5),
        Region(3041, 5070, 10.5), Region(3041, 5073, 10.5), Region(3041, 5077, 10.5), Region(3044, 5073, 10.5),
        Region(3044, 5079, 10.5),
        // The north and east.
        Region(Position(3000, 5105, 1)) {
            when {
                it.y >= 5095 -> 8.0
                it.y >= 5031 -> 8.5
                else -> 9.0
            }
        },
        Region(3025, 5021, 9.0), Region(3036, 5021, 9.0), Region(3044, 5022, 9.0),
        Region(3030, 5012, 9.0), Region(3038, 5013, 9.0), Region(3045, 5014, 9.0),
        Region(3000, 5020, 9.5), Region(3005, 5018, 9.5), Region(3005, 5023, 9.5), Region(3016, 5021, 9.5),
        Region(2980, 5045, 10.0),
        // The chest room.
        Region(3053, 5043, 4.2), Region(3054, 5065, 4.2), Region(3057, 5030, 4.2),
        Region(3047, 5037, 11.0), Region(3040, 5040, 11.0), Region(3040, 5056, 11.0),
        Region(3012, 5033, 11.2),
        // Round the centre, and the safe room.
        Region(2992, 5060, 11.5), Region(3018, 5066, 11.5), Region(2995, 5050, 11.5)
    )

    /**
     * A player's run through the maze.
     */
    class Run(var agility: Int, var thieving: Int) {

        /**
         * Points spent so far.
         */
        var agilitySpent = 0
        var thievingSpent = 0

        /**
         * The experience multiplier of the last region the player stood in.
         */
        var multiplier = 0.0

        /**
         * Set once the player goes through a gallery door: the run then pays nothing.
         */
        var forfeited = false

        /**
         * Set when the run is ending, so nothing else fires.
         */
        var leaving = false

        /**
         * Traps disarmed for this player, by their object's tile, with the tiles that set them off.
         */
        val disarmed = HashMap<Position, List<Position>>()

        /**
         * The trap the player last clicked to search, which doesn't fire while they walk up to it.
         */
        var searching: Position? = null

        /**
         * The tiles the player stepped onto this tick, and the crossing they stood on at the end of the last.
         */
        val walked = ArrayList<Position>()
        var crossing = -1

        /**
         * The rotation door's nine dials, scrambled on the first opening of the run.
         */
        var dials: IntArray? = null
    }

    /**
     * The player's run, while they're in the maze.
     */
    var Player.denRun by Attr.nullableObj(Run::class)

    /**
     * Whether Brian has given this player a jewel before.
     */
    var Player.denAccess by Attr.boolean().persist("rogues_den_access")

    /**
     * Starts a run as the player goes through the doorway: Prayer drops to nothing, Agility and Thieving go to
     * their normal levels.
     */
    fun enter(plr: Player) {
        plr.agility.level = plr.agility.staticLevel
        plr.thieving.level = plr.thieving.staticLevel
        plr.prayer.level = 0
        plr.denRun = Run(plr.agility.level, plr.thieving.level)
        plr.controllers.register(RoguesDenController(plr))
    }

    /**
     * Spends [points] of [skill] for an obstacle. Returns `false`, spending nothing, when the player hasn't enough.
     */
    fun spend(plr: Player, skill: Int, points: Int): Boolean {
        val run = plr.denRun ?: return false
        val level = plr.skill(skill)
        if (level.level < points) return false
        level.level -= points
        if (skill == Skill.AGILITY) {
            run.agilitySpent += points
            run.agility = level.level
        } else {
            run.thievingSpent += points
            run.thieving = level.level
        }
        return true
    }

    /**
     * Ends the player's run with a trap: [message] and [trapSeq] on [trap] now, [playerSeq] on the player, and the
     * player out of the maze 3 ticks later.
     */
    fun fail(plr: Player, message: String? = null, playerSeq: Int = -1, trap: PlacedTrap? = null) {
        val run = plr.denRun ?: return
        if (run.leaving) return
        run.leaving = true
        message?.let { plr.sendMessage(it) }
        trap?.let { it.obj.animate(it.type.trapSeq) }
        plr.submitAction(object : LockedAction(plr) {
            var ticks = 0

            override fun onLock() {
                mob.walking.clear()
                if (playerSeq != -1) mob.animation(Animation(playerSeq))
            }

            override fun run(): Boolean {
                if (++ticks < 3) return false
                leave(mob, Exit.FAIL)
                return true
            }
        })
    }

    /**
     * Takes the player out of the maze to the lobby, puts their levels back, takes back the maze's items and pays the
     * run's experience, with Brian's line.
     */
    fun leave(plr: Player, exit: Exit) {
        val run = plr.denRun
        plr.denRun = null
        if (plr.controllers.primary is RoguesDenController) plr.controllers.unregister()
        val from = plr.position
        plr.walking.clear()
        plr.move(START)
        plr.animation(Animation(-1))
        plr.agility.level = plr.agility.staticLevel
        plr.thieving.level = plr.thieving.staticLevel
        plr.prayer.level = plr.prayer.staticLevel
        for (id in MAZE_ITEMS) {
            val amount = plr.inventory.computeAmountForId(id)
            if (amount > 0) plr.inventory.remove(Item(id, amount))
        }
        if (run == null || exit == Exit.LOGIN) return
        val multiplier = when {
            run.forfeited -> 0.0
            exit == Exit.SAFE -> 12.0
            else -> run.multiplier
        }
        val agilityXp = (run.agilitySpent * multiplier).toInt()
        val thievingXp = (run.thievingSpent * multiplier).toInt()
        if (agilityXp > 0) plr.agility.addExperience(agilityXp.toDouble())
        if (thievingXp > 0) plr.thieving.addExperience(thievingXp.toDouble())
        val line = when {
            multiplier == 0.0 -> NOT_FAR
            exit == Exit.SAFE || inSafeRoom(from) -> SO_FAR
            else -> NOT_BAD
        }
        val dialogue = plr.newDialogue().npc(BRIAN, *line)
        if (multiplier > 0.0) {
            dialogue.text("You gained:", "$thievingXp Thieving Experience.", "$agilityXp Agility Experience.")
        }
        dialogue.open()
    }

    /**
     * Runs the maze's checks at the end of each of the player's ticks: levels held, regions, blades, guards, traps
     * and the crossings that cost points.
     */
    fun tick(plr: Player) {
        val run = plr.denRun ?: return
        val walked = ArrayList(run.walked)
        run.walked.clear()
        if (run.leaving) return
        // Drained levels don't come back while the player is inside.
        if (plr.agility.level != run.agility) plr.agility.level = run.agility
        if (plr.thieving.level != run.thieving) plr.thieving.level = run.thieving
        val here = plr.position
        if (here.z != 1) return
        if (!run.forfeited) RoguesDenMap.regions[here]?.let { run.multiplier = it }
        run.disarmed.values.removeIf { triggers -> triggers.none { it.isWithinDistance(here, 1) } }
        if (here in RoguesDenMap.blades || walked.any { it in RoguesDenMap.blades } ||
            RoguesDenNpcs.bladeTouches(here, walked)) {
            fail(plr, playerSeq = 1114)
            return
        }
        if (RoguesDenNpcs.guardCatches(here)) {
            fail(plr, playerSeq = 1114)
            return
        }
        val trap = RoguesDenMap.traps[here]?.firstOrNull { it.obj.position !in run.disarmed &&
                it.obj.position != run.searching }
        if (trap != null) {
            fail(plr, if (trap.type.wall) "You triggered a wall trap!" else "You triggered a floor trap!",
                 trap.type.playerSeq, trap)
            return
        }
        var crossing = run.crossing
        for (tile in walked) {
            val next = RoguesDenMap.crossings[tile] ?: -1
            if (next != -1 && next != crossing && !spend(plr, Skill.AGILITY, 3)) {
                fail(plr)
                return
            }
            crossing = next
        }
        run.crossing = crossing
    }

    /**
     * Puts back a player who logged out inside the maze, as their run can't go on.
     */
    fun login(plr: Player) {
        if (plr.denRun == null && RoguesDenMap.inBox(plr.position)) leave(plr, Exit.LOGIN)
    }
}
