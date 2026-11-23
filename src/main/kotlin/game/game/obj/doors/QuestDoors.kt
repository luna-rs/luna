package game.obj.doors

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.util.concurrent.CompletableFuture

/**
 * Doors and gates that a quest keeps shut. Those quests don't exist yet, so for now each one lets every player
 * through: the clicked door swings open, the player steps across and it shuts again behind them. A leaf standing
 * beside a partner leaf opens together with it.
 *
 * Each group in [doors] names its quest and the check to restore once that quest is added.
 *
 * @author TheLining
 */
object QuestDoors {

    /**
     * The invisible wall that keeps other players out of an open doorway.
     */
    private const val BLOCKER = 83

    /**
     * The ticks that a door stays open for.
     */
    private const val OPEN_TICKS = 3

    /**
     * The most ticks that a player is given to reach a door and for it to be free, before they are given up on.
     */
    private const val MAX_TICKS = 30

    /**
     * Every quest door, mapped to the object that is shown while it stands open. A door mapped to itself has no
     * separate open object, so the same one is shown turned.
     */
    val doors: Map<Int, Int> = mapOf(
        // TODO Dwarf Cannon: the Captain's task.
        4 to 2527,
        // TODO Holy Grail: the magic whistles and Merlin's workshop stage.
        22 to 1535, 24 to 1535,
        // TODO Clock Tower: the rat cage gate.
        37 to 1540,
        // TODO Fishing Contest: the fishing pass.
        47 to 49, 48 to 50,
        // TODO Fight Arena: the Khazard armour and cell keys.
        81 to 1532,
        // TODO Temple of Ikov: the levers, shiny key and Lucien's stage.
        89 to 1562, 90 to 1563, 92 to 1532, 93 to 1532, 94 to 1562, 95 to 1563, 99 to 1532, 102 to 1538,
        // TODO Draynor Manor: the front doors lock behind the player.
        134 to 1522, 135 to 1523,
        // TODO Sheep Herder: the plague jacket and trousers.
        166 to 166, 167 to 167,
        // TODO Tai Bwo Wannai Trio: the village hut doors.
        779 to 780,
        // TODO Waterfall Quest: Golrie's key and Glarial's amulet.
        1991 to 1543, 2002 to 2003,
        // TODO Biohazard: Guidor's wife, the mourner stew and the training camp.
        2032 to 2033, 2034 to 2035, 2039 to 2040, 2041 to 2042, 2050 to 2050, 2051 to 2051, 2058 to 2059,
        2060 to 2061,
        // TODO Plague City: Elena's key and Bravek's warrant.
        2054 to 2055, 2526 to 2527, 2528 to 2529, 2535 to 2536, 2537 to 2538,
        // TODO Pirate's Treasure: Wydin's white apron.
        2069 to 2070,
        // TODO Druidic Ritual: the suits of armour that attack at the cauldron doors.
        2143 to 1541, 2144 to 1546,
        // TODO Tree Gnome Village: Khazard's stronghold.
        2184 to 1532,
        // TODO Observatory Quest: the keep key.
        2199 to 2199, 2200 to 2200,
        // TODO Shilo Village: Rashiliyia's tomb, the bone key and village entry.
        2246 to 2246, 2247 to 2247, 2255 to 2255, 2256 to 2256, 2259 to 2259, 2260 to 2260, 2261 to 2261,
        2262 to 2262,
        // TODO Black Knights' Fortress: the guard disguise.
        2337 to 1535, 2338 to 1532, 2339 to 1535,
        // TODO Shield of Arrav: gang membership.
        2397 to 1534, 2398 to 1534, 2399 to 1534,
        // TODO Family Crest: the lever puzzle.
        2427 to 2428, 2429 to 2428, 2430 to 2428, 2431 to 2428,
        // TODO The Grand Tree: the shipyard password.
        2438 to 2438, 2439 to 2439,
        // TODO Dragon Slayer: the maze keys, the magic door and Elvarg's gate.
        2586 to 1535, 2595 to 1535, 2596 to 9464, 2597 to 9465, 2598 to 9466, 2599 to 9467, 2600 to 9468,
        2601 to 9469, 2602 to 1535, 2606 to 1588, 2607 to 1563, 2608 to 1562,
        // TODO Heroes' Quest: the gang hideouts and Scarface Pete's keys.
        2621 to 1534, 2622 to 1534, 2626 to 1534, 2627 to 1534, 2628 to 1534,
        // TODO The Tourist Trap: the mining camp disguise and cell key.
        2673 to 1562, 2674 to 1563, 2685 to 1562, 2686 to 1563, 2687 to 1562, 2688 to 1563,
        // TODO Tribal Totem: the combination lock.
        2705 to 1535, 2706 to 1535,
        // TODO Watchtower: the ogre guards and the shamans' gate.
        2786 to 1562, 2787 to 1563, 2788 to 1562, 2789 to 1563, 2814 to 2814, 2815 to 2815,
        // TODO Witch's House: the door key, the gloves and the shed key.
        2861 to 1534, 2862 to 1535, 2863 to 1532, 2865 to 1562, 2866 to 1563,
        // TODO Prince Ali Rescue: the bronze key.
        2881 to 1541,
        // TODO Legends' Quest: the lock-pick and strength trials.
        2912 to 2914, 2913 to 2915, 2922 to 2924, 2923 to 2925,
        // TODO Underground Pass: Kardia's cat.
        3270 to 1531,
        // TODO Elemental Workshop: the battered key.
        3390 to 3392, 3391 to 3393,
        // TODO Priest in Peril: Drezel's cell key.
        3463 to 3464,
        // TODO Nature Spirit: Drezel's warning.
        3506 to 1562, 3507 to 1563,
        // TODO Death Plateau: the combination lock and Harold.
        3743 to 3744, 3747 to 1535,
        // TODO Troll Stronghold: the storeroom key.
        3810 to 3764,
        // TODO Shades of Mort'ton: the shade keys.
        4107 to 4107, 4108 to 4108, 4109 to 4109,
        // TODO The Fremennik Trials: Olaf's and Peer's trials.
        4148 to 4248, 4165 to 4248, 4166 to 4248,
        // TODO Throne of Miscellania: the throne room.
        4672 to 1515,
        // TODO Monkey Madness: the Ape Atoll huts.
        4710 to 4711, 4807 to 4711,
        // TODO Haunted Mine: the crystal mine key.
        4962 to 1531, 4963 to 4963, 4964 to 4964,
        // TODO Creature of Fenkenstrain: the tower and shed keys.
        5172 to 1543, 5174 to 1514,
        // TODO The Feud: the villa.
        6238 to 6238, 6240 to 6240,
        // TODO Forgettable Tale of a Drunken Dwarf: the metal gate.
        6451 to 6451, 6452 to 6452,
        // TODO Icthlarin's Little Helper: Sophanem's gate and the pyramid doorway.
        6615 to 6616, 6643 to 6643,
        // TODO Zogre Flesh Eaters: the ogre tomb.
        6871 to 6873, 6872 to 6874,
        // TODO Rum Deal: the brewery gate.
        10172 to 10173,
        // TODO A Soul's Bane: the rift gates.
        10318 to 6616,
        // TODO Spirits of the Elid: the golem doors and the robe.
        10419 to 10422, 10420 to 10422, 10421 to 10422, 10423 to 10424, 10425 to 10426, 10427 to 10428,
        10429 to 10430, 10431 to 10432,
        // TODO Enakhra's Lament: the sigils and the body-part doors.
        11051 to 11052, 11053 to 11054, 11055 to 11056, 11057 to 11058, 11064 to 11065, 11066 to 11067,
        11068 to 11069, 11070 to 11071, 11151 to 11151, 11152 to 11152,
        // TODO Recipe for Disaster: the Lumbridge dining room and Evil Dave's pen.
        12348 to 7260, 12349 to 12349, 12350 to 12350, 12467 to 12469, 12468 to 12470,
        // TODO In Aid of the Myreque: the Burgh de Rott gate.
        12816 to 12818, 12817 to 12819)

    /**
     * The doors that sound like a metal grate.
     */
    private val grates = setOf(
        89, 90, 94, 95, 2143, 2144, 2199, 2200, 2255, 2256, 2259, 2260, 2607, 2608, 2673, 2674, 2685, 2686, 2687,
        2688, 2786, 2787, 2788, 2789, 2814, 2815, 2865, 2866, 2881, 2912, 2913, 2922, 2923, 3463, 3506, 3507, 3810,
        6451, 6452)

    /**
     * The doors that sound like a wooden gate.
     */
    private val gates = setOf(
        47, 48, 166, 167, 2050, 2051, 2261, 2262, 2438, 2439, 12816, 12817)

    /**
     * The tiles of two-leaf doors that are open, or about to be, for someone.
     */
    private val busy = HashSet<Position>()

    /**
     * The tiles that hold an open leaf. A leaf shown as its own closed object must not be opened a second time.
     */
    private val swung = HashSet<Position>()

    /**
     * Takes [plr] through [door], together with its partner leaf if it has one.
     */
    fun cross(plr: Player, door: GameObject) {
        val open = doors[door.id] ?: return
        if (door.objectType != ObjectType.STRAIGHT_WALL || door.position in swung) {
            return
        }
        val partner = partnerOf(door)
        if (partner == null) {
            if (plr.actions.first(Single::class.java) == null) {
                plr.submitAction(Single(plr, door, DoorType(door.id, open, null, soundOf(door.id), null, OPEN_TICKS)))
            }
        } else if (plr.actions.first(Double::class.java) == null) {
            plr.submitAction(Double(plr, door, partner.first, partner.second))
        }
    }

    /**
     * The sound that [id] opens with, or `null` for the ordinary door sound.
     */
    private fun soundOf(id: Int): Sound? = when (id) {
        in grates -> Sound.GRATE_OPEN
        in gates -> Sound.GATE_OPEN
        else -> null
    }

    /**
     * The tile on the far side of [door], which is also where its open leaf stands.
     */
    private fun across(door: GameObject): Position = door.position.translate(1, door.direction.toNormalDirection())

    /**
     * Finds the other leaf of [door] and which leaf [door] itself is. The left leaf's partner stands one tile along
     * the wall in the direction that the door files use for double doors.
     *
     * @return The partner and the side of [door], or `null` if it stands alone.
     */
    private fun partnerOf(door: GameObject): Pair<GameObject, DoorSide>? {
        val along = when (door.direction) {
            ObjectDirection.WEST -> Pair(0, 1)
            ObjectDirection.NORTH -> Pair(1, 0)
            ObjectDirection.EAST -> Pair(0, -1)
            ObjectDirection.SOUTH -> Pair(-1, 0)
        }
        for ((sign, side) in listOf(Pair(1, DoorSide.LEFT), Pair(-1, DoorSide.RIGHT))) {
            val leaf = world.objects.findAll(door.position.translate(along.first * sign, along.second * sign))
                .filter {
                    it.id in doors && it.objectType == ObjectType.STRAIGHT_WALL && it.direction == door.direction
                }
                .findFirst().orElse(null)
            if (leaf != null) {
                return Pair(leaf, side)
            }
        }
        return null
    }

    /**
     * Swings both leaves open and keeps the doorway shut to everyone else until they close again. Everything is
     * checked before anything is changed, so a door that cannot be opened is left as it was.
     *
     * @return `true` if the door is now open.
     */
    private fun open(plr: Player, door: GameObject, partner: GameObject, side: DoorSide): Boolean {
        val otherSide = if (side == DoorSide.LEFT) DoorSide.RIGHT else DoorSide.LEFT
        val leaves = listOf(Pair(door, side), Pair(partner, otherSide))
        val closed = leaves.map { (leaf, _) ->
            world.objects.findAll(leaf.position)
                .filter { it.id == leaf.id && it.direction == leaf.direction }
                .findFirst().orElse(null) ?: return false
        }
        val swings = closed.mapIndexed { index, leaf ->
            val type = DoorType(leaf.id, doors.getValue(leaf.id), leaves[index].second, null, null, OPEN_TICKS)
            Doors.doubleSwap(leaf, type, true)
        }
        val removed = closed.filter { world.removeObject(it) }
        if (removed.size != closed.size) {
            removed.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            return false
        }

        val placed = ArrayList<GameObject>(4)
        swings.forEach { placed += world.addObject(it.id, it.position, it.old.objectType, it.direction) }
        closed.forEach { placed += world.addObject(BLOCKER, it.position, ObjectType.STRAIGHT_WALL, it.direction) }
        val doorway = closed.map { it.position }
        val leafTiles = swings.map { it.position }
        busy += doorway
        swung += leafTiles
        plr.playSound(soundOf(door.id) ?: Sound.DOOR_OPEN)
        world.scheduleOnce(OPEN_TICKS) {
            try {
                placed.forEach { world.removeObject(it) }
                closed.forEach { world.addObject(it.id, it.position, it.objectType, it.direction) }
            } finally {
                busy -= doorway.toSet()
                swung -= leafTiles.toSet()
            }
        }
        return true
    }

    /**
     * Takes the player through a door that stands alone, using the shared passage.
     */
    private class Single(plr: Player, door: GameObject, type: DoorType) : DoorPassageAction(plr, door, type) {

        private val leafTile = across(door)

        // TODO Check the door's quest here.
        override fun checkAccess(entering: Boolean): Boolean = true

        override fun onPassage(entering: Boolean) {
            swung += leafTile
            world.scheduleOnce(OPEN_TICKS) { swung -= leafTile }
        }
    }

    /**
     * Walks the player to the clicked leaf of a two-leaf door and sends them through. This is a weak action, so
     * walking away cancels it.
     */
    private class Double(plr: Player, private val door: GameObject, private val partner: GameObject,
                         private val side: DoorSide) : Action<Player>(plr, ActionType.WEAK) {

        /**
         * If the player stands on the door's own tile, as opposed to the tile across from it.
         */
        private val entering = Doors.isEntering(door.position, door.direction, plr.position)

        /**
         * The tile that the player goes through the door from.
         */
        private val start = if (entering) door.position else across(door)

        /**
         * The tile that the player ends up on.
         */
        private val destination = if (entering) across(door) else door.position

        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > MAX_TICKS) {
                mob.sendMessage("You can't get through the door right now.")
                return true
            }
            val pending = walking
            if (pending != null && !pending.isDone) {
                return false
            }
            if (mob.position != start) {
                if (pending != null) {
                    mob.sendMessage("You can't reach the door from here.")
                    return true
                }
                walking = mob.navigator.navigate(start, false)
                return false
            }
            if (door.position in busy || partner.position in busy) {
                return false
            }
            // TODO Check the door's quest here.
            if (open(mob, door, partner, side)) {
                mob.walking.addForcedStep(Direction.between(start, destination))
            }
            return true
        }
    }
}
