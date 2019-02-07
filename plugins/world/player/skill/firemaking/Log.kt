package world.player.skill.firemaking


// TODO Blocked until stationary entity system is complete
enum class Log(val id: Int, val level: Int, val exp: Double, val ticks: Int) {
    NORMAL(id = 1511,
           level = 1,
           exp = 40.0,
           ticks = 3),
    ACHEY(id = 2862,
          level = 1,
          exp = 40.0,
          ticks = 3),
    OAK(id = 1521,
        level = 15,
        exp = 60.0,
        ticks = 3),
    WILLOW(id = 1519,
           level = 30,
           exp = 90.0,
           ticks = 4),
    TEAK(id = 6333,
         level = 35,
         exp = 105.0,
         ticks = 4),
    MAPLE(id = 1517,
          level = 45,
          exp = 135.0,
          ticks = 4),
    MAHOGANY(id = 6332,
             level = 50,
             exp = 157.5,
             ticks = 5),
    YEW(id = 1515,
        level = 60,
        exp = 202.5,
        ticks = 6),
    MAGIC(id = 1513,
          level = 75,
          exp = 303.8,
          ticks = 7);

    companion object {
        val ID_TO_LOG = values().associateBy { it.id }
    }
}
