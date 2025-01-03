package world.player.skill.crafting.glassMaking

/**
 * Represents a material that can be made on the [GlassBlowingInterface].
 */
enum class GlassMaterial(val id: Int, val make1Id: Int, val level: Int, val exp: Double) {
    BEER_GLASS(id = 1919,
               make1Id = 12400,
               level = 1,
               exp = 17.5),
    CANDLE_LANTERN(id = 4527,
                   make1Id = 12404,
                   level = 4,
                   exp = 19.0),
    OIL_LAMP(id = 4522,
             make1Id = 12408,
             level = 12,
             exp = 25.0),
    VIAL(id = 229,
         make1Id = 11474,
         level = 33,
         exp = 35.0),
    FISHBOWL(id = 6667,
             make1Id = 6203,
             level = 42,
             exp = 17.5),
    UNPOWERED_ORB(id = 567,
                  make1Id = 12396,
                  level = 46,
                  exp = 52.5),
    LANTERN_LENS(id = 4542,
                 make1Id = 12412,
                 level = 49,
                 exp = 55.0);

    // The secondary button IDs. Based off of the first value.
    val make5Id = make1Id - 1
    val make10Id = make5Id - 1
    val makeXId = make10Id - 1
}
