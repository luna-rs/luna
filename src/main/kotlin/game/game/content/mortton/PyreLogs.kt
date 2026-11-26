package game.content.mortton

/**
 * The revision 377 pyre log, its wood, and the Firemaking requirements and rewards.
 *
 * @author lare96
 */
enum class PyreLogs(val wood: Int, val item: Int, val level: Int, val doses: Int, val mixXp: Double,
                    val burnXp: Double, val prayerBonus: Double) {
    NORMAL(1511, 3438, 5, 2, 20.0, 50.0, 0.0),
    OAK(1521, 3440, 20, 2, 20.0, 70.0, 8.0),
    WILLOW(1519, 3442, 35, 3, 40.0, 100.0, 8.5),
    MAPLE(1517, 3444, 50, 3, 40.0, 175.0, 9.0),
    YEW(1515, 3446, 65, 4, 60.0, 255.0, 9.5),
    MAGIC(1513, 3448, 80, 4, 60.0, 404.5, 10.0);

    val loadedObject: Int get() = 4094 + ordinal
    val readyObject: Int get() = 4100 + ordinal
}
