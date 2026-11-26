package game.content.templeTrekking

import io.luna.game.model.Position
import io.luna.game.model.Region

/**
 * An event room, as tiles of the real map square that each event copies.
 *
 * @property region The map square holding the room.
 * @property start Where the player arrives, just inside the Escape path.
 * @property villager Where the villager arrives.
 * @property spawns Where monsters appear, at the far end of the room.
 * @property evadable Whether the room has Evade-event paths.
 */
class TrekRoom(val region: Region,
               val start: Position,
               val villager: Position,
               val spawns: List<Position>,
               val evadable: Boolean) {

    companion object {

        private fun room(region: Int, start: Position, villager: Position, evadable: Boolean,
                         vararg spawns: Position) =
            TrekRoom(Region(region), start, villager, spawns.toList(), evadable)

        /**
         * The swamp rooms. Each of these map squares holds two rooms with Evade-event paths and, to the south, one
         * without.
         */
        val SWAMP = listOf(
            room(8014, Position(1998, 5032), Position(1999, 5033), true, Position(1999, 5049),
                 Position(1997, 5047), Position(1999, 5047), Position(2001, 5047), Position(1995, 5045)),
            room(8014, Position(2027, 5032), Position(2027, 5033), true, Position(2038, 5046),
                 Position(2039, 5048), Position(2036, 5044), Position(2036, 5046), Position(2034, 5042)),
            room(8014, Position(2011, 5008), Position(2012, 5009), false, Position(2011, 5019),
                 Position(2013, 5019), Position(2015, 5018), Position(2012, 5017), Position(2017, 5017)),
            room(8782, Position(2222, 5032), Position(2222, 5033), true, Position(2230, 5046),
                 Position(2230, 5048), Position(2228, 5044), Position(2226, 5045), Position(2227, 5042)),
            room(8782, Position(2187, 5032), Position(2186, 5031), true, Position(2190, 5049),
                 Position(2192, 5049), Position(2189, 5047), Position(2191, 5047), Position(2193, 5047)),
            room(8782, Position(2201, 5008), Position(2202, 5009), false, Position(2203, 5019),
                 Position(2205, 5019), Position(2207, 5019), Position(2209, 5018), Position(2201, 5018)),
            room(9038, Position(2251, 5032), Position(2250, 5032), true, Position(2254, 5049),
                 Position(2256, 5049), Position(2253, 5047), Position(2255, 5047), Position(2257, 5047)),
            room(9038, Position(2282, 5032), Position(2281, 5033), true, Position(2294, 5046),
                 Position(2294, 5048), Position(2292, 5044), Position(2292, 5046), Position(2290, 5045)),
            room(9038, Position(2265, 5008), Position(2265, 5009), false, Position(2267, 5019),
                 Position(2269, 5019), Position(2271, 5019), Position(2265, 5017), Position(2265, 5019)),
            room(9294, Position(2347, 5032), Position(2347, 5033), true, Position(2358, 5046),
                 Position(2359, 5048), Position(2356, 5044), Position(2356, 5046), Position(2354, 5042)),
            room(9294, Position(2316, 5032), Position(2315, 5032), true, Position(2318, 5049),
                 Position(2320, 5049), Position(2317, 5047), Position(2319, 5047), Position(2321, 5047)),
            room(9294, Position(2329, 5008), Position(2328, 5007), false, Position(2331, 5019),
                 Position(2333, 5019), Position(2335, 5019), Position(2337, 5018), Position(2329, 5020)),
            room(9550, Position(2381, 5029), Position(2380, 5030), true, Position(2383, 5050),
                 Position(2382, 5048), Position(2384, 5048), Position(2380, 5046), Position(2382, 5046)),
            room(9550, Position(2410, 5030), Position(2410, 5031), true, Position(2422, 5048),
                 Position(2420, 5046), Position(2422, 5046), Position(2418, 5044), Position(2418, 5046)),
            room(9550, Position(2394, 5006), Position(2394, 5007), false, Position(2397, 5021),
                 Position(2394, 5020), Position(2399, 5020), Position(2401, 5020), Position(2396, 5019)))

        /**
         * Mort'ton's square, beside the bridge to Burgh de Rott.
         */
        val MORTTON = room(8526, Position(2140, 5003), Position(2139, 5004), true, Position(2132, 5030),
                           Position(2134, 5030), Position(2136, 5030), Position(2138, 5029), Position(2130, 5028))

        /**
         * The bog, south-west of the puzzle square.
         */
        val BOG = room(8270, Position(2060, 5002), Position(2059, 5003), false)

        /**
         * The river with the swamp trees.
         */
        val RIVER = room(8270, Position(2092, 5004), Position(2093, 5004), false)

        /**
         * The broken bridge, with its zombie.
         */
        val BRIDGE = room(8270, Position(2082, 5041), Position(2082, 5042), false, Position(2087, 5045))

        /**
         * The boat on the hard route. The player and villager stand in it.
         */
        val BOAT = room(8270, Position(2062, 5034), Position(2063, 5036), false)
    }
}
