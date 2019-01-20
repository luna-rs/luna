package world.player.skill.mining

/**
 *
 *
 * @author lare96 <http://github.com/lare96>
 */
// TODO check if swapping objects can be done simply (id's 450/451)
/*enum class Ore(val rocks: IntArray,
               val emptyRocks: IntArray,
               val level: Int,
               val item: Int,
               val respawnTicks: Int?,
               val frequency: Rational,
               val exp: Double) {
    CLAY(rocks = intArrayOf(2108, 2109),
         emptyRocks = intArrayOf(450, 451),
         level = 1,
         item = 434,
         respawnTicks = 1,
         frequency = COMMON,
         exp = 5.0),
    RUNE_ESSENCE(obj = "",
                 level = 1,
                 item = 1436,
                 respawnTicks = null,
                 frequency = COMMON,
                 exp = 5.0),
    TIN(obj = "",
        level = 1,
        item = 438,
        respawnTicks = 5,
        frequency = COMMON,
        exp = 17.5),
    COPPER(obj = "",
           level = 1,
           item = 438,
           respawnTicks = 5,
           frequency = COMMON,
           exp = 17.5),
    IRON,
    COAL,
    GOLD,
    SILVER,
    MITHRIL,
    ADAMANT,
    RUNITE
    TIN( 1, 438, 5, 1, 17),
    COPPER(1, 436, 5, 1, 17),
    IRON( 15, 440, 10, 1, 35),
    SILVER( 20, 443, 120, 3, 40),
    COAL(30, 453, 60, 4, 50),
    GOLD( 40, 444, 120, 5, 65),
    MITHRIL( 55, 447, 180, 6, 80),
    ADAMANTITE( 70, 449, 420, 7, 95),
    RUNITE( 85, 451, 840, 10, 125);


    /** The level needed to mine this type of rock. */
    private int level;

    /** The item id of the rock. */
    private int itemId;

    /** The time it takes for this rock to respawn. */
    private int respawnTime;

    /** The rate at which this rock can be mined. */
    private int mineRate;

    /** The experience gained from mining this rock. */
    private int experience;

    /** A map of rocks mapped to another map of their ore objects. */
    private static Map<Rock, HashMap<Integer, OreObject>> rockMap = new HashMap<Rock, HashMap<Integer, OreObject>>();

    /** A map of object id's mapped to their respective rock instances. */
    private static Map<Integer, Rock> objectMap = new HashMap<Integer, Rock>();

    /** Load the data into the map. */
    static
    {
        for (Rock rock : Rock.values()) {
        rockMap.put(rock, new HashMap < Integer, OreObject > ());

        for (OreObject ore : rock.getOre()) {
        rockMap.get(rock).put(ore.getRock(), ore);
        objectMap.put(ore.getRock(), rock);
    }
    }
    }

    /**
     * Create a new {@link Rock}.
     *
     * @param ore
     *        all of the rocks and their empty rock replacements.
     * @param level
     *        the level needed to mine this type of rock.
     * @param itemId
     *        the item id of the rock.
     * @param respawnTime
     *        the time it takes for this rock to respawn.
     * @param mineRate
     *        the rate at which this rock can be mined.
     * @param experience
     *        the experience gained from mining this rock.
     */
    private Rock(OreObject[] ore, int level, int itemId, int respawnTime, int mineRate, int experience)
    {
        this.ore = ore;
        this.level = level;
        this.itemId = itemId;
        this.respawnTime = respawnTime;
        this.mineRate = mineRate;
        this.experience = experience;
    }

    /**
     * Gets all of the rocks and their empty rock replacements.
     *
     * @return the ore.
     */
    public OreObject[] getOre()
    {
        return ore;
    }

    /**
     * Gets the level needed to mine this type of rock.
     *
     * @return the level.
     */
    public int getLevel()
    {
        return level;
    }

    /**
     * Gets the item id of the rock.
     *
     * @return the item id.
     */
    public int getItemId()
    {
        return itemId;
    }

    /**
     * Gets the time it takes for this rock to respawn.
     *
     * @return the respawn time.
     */
    public int getRespawnTime()
    {
        return respawnTime;
    }

    /**
     * Gets the rate at which this rock can be mined.
     *
     * @return the speed.
     */
    public int getMineRate()
    {
        return mineRate;
    }

    /**
     * Gets the experience gained from mining this rock.
     *
     * @return the experience.
     */
    public int getExperience()
    {
        return experience;
    }

    /**
     * Gets the correct {@link OreObject} for the specified {@link Rock} and
     * object id.
     *
     * @param rock
     *        the rock to get the ore object for.
     * @param objectId
     *        the object id to get the ore object for.
     * @return the correct object ore.
     */
    public static OreObject getOre(Rock rock, int objectId)
    {
        return rockMap.get(rock).get(objectId);
    }

    /**
     * Gets the correct rock instance for the specified object id.
     *
     * @param objectId
     *        the specified object id.
     * @return the correct rock instance.
     */
    public static Rock getRock(int objectId)
    {
        return objectMap.get(objectId);
    }
}*/