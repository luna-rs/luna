package api.bot.zone

import api.predef.*
import com.google.common.base.Preconditions.checkState
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import engine.bank.Banking
import io.luna.game.model.Entity
import io.luna.game.model.EntityType
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject

/**
 * Defines a large world area that bots can reason about for travel, banking, safety, and activity selection.
 *
 * Map regions provide the default ownership. Exact [parts] refine shared regions, such as Rimmington's mainland
 * coast and Musa Point, without assigning the same default region to two zones. [of] resolves both kinds of geography.
 *
 * Each zone has:
 * - An [anchor] used as a representative destination or center point.
 * - A set of [regions] that belong to the zone.
 * - Optional [bankAnchors] for banking-aware bot behavior.
 * - A [safe] flag used to separate normal zones from dangerous areas like the Wilderness.
 *
 * @property anchor A representative position inside this zone.
 * @property regions Region ids whose default owner is this zone.
 * @property bankAnchors Known bank object or bank-adjacent positions inside this zone, or the spawn tiles of bankers
 * where a bank has no booth.
 * @property safe Whether this zone is considered safe for normal bot activity.
 * @property parts Exact ownership overrides, including their plane policy, within otherwise shared regions.
 *
 * @author lare96
 */
enum class Zone(val price: Int,
                val anchor: Position,
                val regions: Set<Int>,
                val bankAnchors: List<Position> = emptyList(),
                val safe: Boolean = true,
                val parts: List<SimpleBoxArea> = emptyList()) {
    /**
     * Shilo's supplied village footprint, including the gem mine, furnace, river fishing and two native bankers.
     * The village owns its local bank independently of the wider Karamja coast and its mainland banking trips.
     */
    SHILO_VILLAGE(price = 2500,
                  anchor = Position(2852, 2958),
                  regions = setOf(11310),
                  bankAnchors = listOf(Position(2851, 2954), Position(2853, 2955))),

    /**
     * Brimhaven's western Karamja town, northern docks, fruit-tree plot, Shilo cart and dungeon entrance.
     * Dungeon camps retain their explicit Ardougne banking; the town has no registered local bank.
     */
    BRIMHAVEN(price = 2500,
               anchor = Position(2776, 3214),
               regions = setOf(10801, 10802, 11057, 11058)),

    /**
     * Port Sarim's mainland docks and southern peninsula, serving the native passenger ships and coastal activities.
     * Local activities keep Falador banking; no bank is created at this transport hub.
     */
    PORT_SARIM(price = 250,
                anchor = Position(3027, 3208),
                regions = setOf(11824, 12080, 12081, 12082)),

    /**
     * Rimmington's village, bush patch, mine and mainland fishing/tree coast. The two coastal parts distinguish
     * the walkable mainland from Musa Point inside their shared map regions; Falador remains the activity bank.
     */
    RIMMINGTON(price = 250,
                anchor = Position(2957, 3214),
                regions = setOf(11570, 11826),
                parts = listOf(SimpleBoxArea.of(2936, 3192, 3007, 3199, -1),
                               SimpleBoxArea.of(2960, 3136, 3007, 3191, -1))),

    /**
     * The southern desert below Shantay Pass, covering the quarry, lizards, Elid crocodiles and Nardah.
     * Nardah's four native booths are registered for bank discovery. Existing activity-specific Al Kharid
     * banking remains explicit; this geography does not change desert heat, quest admission or encounter rules.
     */
    KHARIDIAN_DESERT(price = 250,
                     anchor = Position(3304, 3115),
                     regions = setOf(12587, 12588, 12589, 12590, 12591, 12592,
                                     12843, 12844, 12845, 12846, 12847, 12848,
                                     13099, 13100, 13101, 13102, 13103, 13104,
                                     13355, 13356, 13357, 13358, 13359, 13360,
                                     13611, 13612, 13613, 13614, 13615, 13616,
                                     13867, 13868, 13869, 13870, 13871, 13872),
                     bankAnchors = listOf(Position(3426, 2889), Position(3426, 2891),
                                          Position(3426, 2893), Position(3426, 2894))),

    /**
     * Port Phasmatys travel hub and bank destinations for the town and its nearby farming plots.
     */
    PORT_PHASMATYS(price = 3500,
                   anchor = Position(3690, 3473),
                   regions = setOf(14646, 14647),
                   bankAnchors = listOf(Position(3687, 3465), Position(3688, 3465),
                                        Position(3690, 3465), Position(3691, 3465))),

    /**
     * Supplied island footprint, arrival and banking destination for Miscellania and Etceteria activities.
     */
    MISCELLANIA_AND_ETCETERIA(price = 5000,
                             anchor = Position(2527, 3859),
                             regions = setOf(9788, 10044, 10300, 10043),
                             bankAnchors = listOf(Position(2620, 3895))),

    /**
     * Supplied Tree Gnome Village travel hub. Its farming visits use ordinary bank discovery;
     * no local bank anchors are configured.
     */
    TREE_GNOME_VILLAGE(price = 2000,
                      anchor = Position(2524, 3168),
                      regions = setOf(9777, 10033)),

    /**
     * Canifis footprint derived from the supplied main-town rectangle, with four native bank booths
     * southeast of the town centre. Travel costs the supplied 5,000 GP fee.
     */
    CANIFIS(price = 5000,
            anchor = Position(3494, 3473),
            regions = SimpleBoxArea.ofRadius(3473, 3461, 47, 57).touchedRegions,
            bankAnchors = listOf(Position(3513, 3479), Position(3513, 3480),
                                 Position(3513, 3481), Position(3513, 3482))),


    /**
     * The Stronghold, river approaches and southern flax fields; bank booths are upstairs in the western bank.
     */
    GNOME_STRONGHOLD(price = 1500,
                     anchor = Position(2459, 3397),
                     regions = setOf(9781, 9524, 9525, 9782, 9526, 9783, 9527),
                     // The booths are upstairs.
                     bankAnchors = listOf(Position(2447, 3424, 1), Position(2447, 3427, 1),
                                          Position(2444, 3424, 1), Position(2444, 3425, 1))),
    /**
     * Draynor village, manor and the coastal Wizards' Tower, with banking at the village booths.
     */
    DRAYNOR(price = 500,
            anchor = Position(3093, 3244),
            regions = setOf(12337, 12338, 12339, 12340),
            bankAnchors = listOf(Position(3091, 3245, 0),
                                 Position(3091, 3242, 0),
                                 Position(3091, 3243, 0))),
    TIRANNWN(price = 2000,
             anchor = Position(2351, 3172),
             regions = setOf(8756, 9012, 8755, 8754, 8753, 9009, 9265, 9010, 9011),
             bankAnchors = listOf(Position(2352, 3166), Position(2354, 3166))),
    ENTRANA(price = 2500,
            anchor = Position(2866, 3336),
            regions = setOf(11316, 11060)),
    BURTHORPE(price = 1000,
              anchor = Position(2918, 3545),
              regions = setOf(11575)),
    EDGEVILLE(price = 500,
              anchor = Position(3087, 3496),
              regions = setOf(12342),
              bankAnchors = listOf(Position(3098, 3493, 0),
                                   Position(3096, 3493, 0),
                                   Position(3095, 3489, 0),
                                   Position(3095, 3491, 0))),
    VARROCK(price = 250,
            anchor = Position(3182, 3440),
            regions = setOf(12596, 12598, 12854, 13110, 12597, 12853, 13109, 13108, 12852),
            bankAnchors = listOf(Position(3186, 3436, 0),
                                 Position(3186, 3438, 0),
                                 Position(3256, 3419, 0),
                                 Position(3254, 3419, 0),
                                 Position(3253, 3419, 0),
                                 Position(3252, 3419, 0),
                                 Position(3186, 3444, 0),
                                 Position(3186, 3446, 0),
                                 Position(3186, 3440, 0),
                                 Position(3186, 3442, 0))),
    /**
     * Falador and its western mines, northern Goblin Village and southern farms. Both city banks are registered.
     */
    FALADOR(price = 250,
            anchor = Position(2964, 3377),
            regions = setOf(11828, 11829, 11830, 12085, 12084, 12083, 11827, 11571, 11572),
            bankAnchors = listOf(Position(3010, 3354, 0),
                                 Position(3011, 3354, 0),
                                 Position(3015, 3354, 0),
                                 Position(3013, 3354, 0),
                                 Position(3012, 3354, 0),
                                 Position(3014, 3354, 0),
                                 Position(2945, 3367), Position(2946, 3367), Position(2947, 3367),
                                 Position(2948, 3367), Position(2949, 3367))),
    TAVERLY(price = 350,
            anchor = Position(2897, 3456),
            regions = setOf(11573, 11574),
            // No banks in Taverly. Falador's west bank is the nearest.
            bankAnchors = listOf(Position(2945, 3367, 0),
                                 Position(2946, 3367, 0),
                                 Position(2947, 3367, 0),
                                 Position(2948, 3367, 0),
                                 Position(2949, 3367, 0))),
    APE_ATOLL(price = 7500,
              anchor = Position(2713, 2798),
              regions = setOf(10795, 11051, 10794, 11050),
              bankAnchors = listOf()), // todo
    /**
     * Musa Point and the wider Karamja island outside Brimhaven and Shilo Village.
     * Rimmington's mainland coastal fragments override the shared northern regions. Mainland bank anchors
     * provide the same Draynor fallback already used by coastal activities; Shilo keeps its own local bankers.
     */
    KARAMJA(price = 2500,
            anchor = Position(2946, 3147),
            regions = setOf(11313,
                            11569,
                            11056,
                            11312,
                            11568,
                            11055,
                            11311,
                            11567,
                            11054,
                            11053,
                            11309,
                            11566,
                            11565,
                            11823,
                            11822,
                            11821,
                            11825),
            bankAnchors = DRAYNOR.bankAnchors),

    /**
     * The TzHaar city under the Karamja volcano, reached through the volcano dungeon. Its banker, TzHaar-Ket-Zuh,
     * has no booth, so his spawn tile is the bank anchor.
     */
    TZHAAR_CITY(price = 2500,
                anchor = Position(2445, 5176),
                regions = setOf(9552, 9808),
                bankAnchors = listOf(Position(2445, 5180))),
    /**
     * Lumbridge castle, swamp and northern mill/farm approaches. Draynor remains its configured bank fallback.
     */
    LUMBRIDGE(price = 250,
              anchor = Position(3222, 3219),
              regions = setOf(12594, 12595, 12850, 12851, 12849, 12593),
              // No bank in Lumbridge. Draynor's is the nearest.
              bankAnchors = DRAYNOR.bankAnchors),
    AL_KHARID(price = 250,
              anchor = Position(3277, 3224),
              regions = setOf(13107, 13106, 13362, 13361, 13105),
              bankAnchors = listOf(Position(3268, 3169, 0),
                                   Position(3268, 3168, 0),
                                   Position(3268, 3164, 0),
                                   Position(3268, 3167, 0),
                                   Position(3268, 3166, 0)),
              // The courtyard north of the pass shares a region with the southern desert.
              parts = listOf(SimpleBoxArea.of(3264, 3116, 3327, 3135, -1))),
    /**
     * Seers' Village, McGrubor's Wood, the northern dairy/maples and Coal Trucks. The exact Camelot castle
     * part shares Catherby's map region, keeping castle visitors and teleport arrivals in the Seers context.
     */
    SEERS_VILLAGE(price = 1250,
                  anchor = Position(2723, 3485),
                  regions = setOf(10806, 10805, 10551, 10807, 10294),
                  bankAnchors = listOf(Position(2728, 3494, 0),
                                       Position(2729, 3494, 0),
                                       Position(2721, 3494, 0),
                                       Position(2722, 3494, 0),
                                       Position(2727, 3494, 0),
                                       Position(2724, 3494, 0)),
                  parts = listOf(SimpleBoxArea.of(2752, 3472, 2795, 3519, -1))),
    PISCATORIS_FISHING_COLONY(price = 7500,
                              anchor = Position(2335, 3687),
                              regions = setOf(9273),
                              bankAnchors = listOf(Position(2330, 3691))),
    /**
     * Catherby's coast, northern trees and farming plots. Camelot castle is assigned to Seers by an exact part.
     */
    CATHERBY(price = 1000,
             anchor = Position(2809, 3435),
             regions = setOf(11061, 11062, 11317),
             bankAnchors = listOf(Position(2809, 3442, 0),
                                  Position(2811, 3442, 0),
                                  Position(2810, 3442, 0),
                                  Position(2807, 3442, 0))),

    /**
     * East and West Ardougne with their farming and southern woodcutting approaches, using both city banks.
     */
    ARDOUGNE(price = 1500,
             anchor = Position(2661, 3306),
             regions = setOf(9778, 9779, 10035, 10291, 10292, 10290, 10546, 10547, 10548, 10803, 10804),
             bankAnchors = listOf(Position(2656, 3283, 0),
                                  Position(2618, 3331, 0),
                                  Position(2619, 3331, 0),
                                  Position(2615, 3331, 0),
                                  Position(2656, 3286, 0))),
    ZANARIS(price = 7500,
            anchor = Position(2388, 4457),
            regions = setOf(9285, 9541, 9797, 9540, 9796),
            bankAnchors = listOf(Position(2382, 4457, 0), Position(2385, 4457, 0),
                                 Position(2385, 4459, 0), Position(2386, 4460, 0))),
    /**
     * Yanille and the existing Port Khazard, southern willow and northern Feldip activity approaches.
     */
    YANILLE(price = 1750,
            anchor = Position(2612, 3101),
            regions = setOf(10288, 10032, 10545, 10287, 10286),
            bankAnchors = listOf(Position(2614, 3094, 0),
                                 Position(2614, 3092, 0),
                                 Position(2614, 3091, 0))),

    WILDERNESS(price = 500,
               anchor = Position(3088, 3544),
               regions = setOf(11831,
                               11832,
                               11833,
                               11834,
                               11835,
                               11836,
                               11837,
                               12087,
                               12088,
                               12089,
                               12090,
                               12091,
                               12092,
                               12093,
                               12343,
                               12344,
                               12345,
                               12346,
                               12347,
                               12348,
                               12349,
                               12599,
                               12600,
                               12601,
                               12602,
                               12603,
                               12604,
                               12605,
                               12855,
                               12856,
                               12857,
                               12858,
                               12859,
                               12860,
                               12861,
                               13111,
                               13112,
                               13113,
                               13114,
                               13115,
                               13116,
                               13117,
                               13367,
                               13368,
                               13369,
                               13370,
                               13371,
                               13372,
                               13373),
               bankAnchors = EDGEVILLE.bankAnchors,
               safe = false),

    RELLEKKA(price = 5000,
             anchor = Position(2666, 3657),
             regions = setOf(10297, 10553, 10554, 10810, 10809, 10296, 10552, 10808),
             bankAnchors = SEERS_VILLAGE.bankAnchors);

    companion object {

        /**
         * All zones marked as safe.
         *
         * Unsafe zones, such as [WILDERNESS], are excluded from this list so callers can choose normal bot destinations
         * without manually filtering dangerous areas.
         */
        val SAFE_ZONES = ImmutableList.copyOf(entries.filter { it.safe })

        /**
         * Default owners of complete map regions. Each region has at most one default owner.
         * Position lookups use [of] so exact ownership parts are applied before this coarse index.
         */
        val REGIONS = run {
            val map = HashMap<Int, Zone>()
            for (zone in entries) {
                zone.regions.forEach {
                    checkState(map.putIfAbsent(it, zone) == null, "Region [$it] already mapped to another zone.")
                }
            }
            ImmutableMap.copyOf(map)
        }

        /**
         * Exact ownership candidates indexed by every region their rectangles cross. Parts belonging to different
         * zones must not overlap on the same plane, so ownership never depends on enum declaration order.
         */
        private val PARTS_BY_REGION: Map<Int, List<Zone>> = run {
            val parts = entries.flatMap { zone -> zone.parts.map { zone to it } }
            for ((index, entry) in parts.withIndex()) {
                val (zone, part) = entry
                checkState(part.plane in -1..3 && part.southWest.x >= 0 && part.southWest.y >= 0 &&
                    part.northEast.x < 16384 && part.northEast.y < 16384, "Invalid ownership part for $zone.")
                for ((other, otherPart) in parts.take(index)) {
                    val samePlane = part.plane == -1 || otherPart.plane == -1 || part.plane == otherPart.plane
                    checkState(zone == other || !samePlane || part.southWest.x > otherPart.northEast.x ||
                        part.northEast.x < otherPart.southWest.x || part.southWest.y > otherPart.northEast.y ||
                        part.northEast.y < otherPart.southWest.y, "Ownership parts overlap: $zone and $other.")
                }
            }
            val candidates = HashMap<Int, MutableSet<Zone>>()
            for ((zone, part) in parts) {
                for (region in regionsOf(part)) candidates.getOrPut(region) { linkedSetOf() }.add(zone)
            }
            candidates.mapValues { (_, zones) -> zones.toList() }
        }

        /**
         * Resolves physical ownership from exact parts first, then a region default. Banking parents and travel
         * destinations are separate: Wilderness activities can still bank in Edgeville without becoming safe areas.
         */
        fun of(locatable: Locatable?): Zone? {
            val position = locatable?.abs() ?: return null
            return PARTS_BY_REGION[position.regionId]?.firstOrNull { zone -> zone.parts.any { it.contains(position) } }
                ?: REGIONS[position.regionId]
        }

        /**
         * Updates ownership after actual movement, including crossing an exact boundary inside the same map region.
         */
        fun updateZone(bot: Bot) {
            bot.zone = of(bot)
        }

        /**
         * Retains the region-change API while resolving the bot's actual position rather than a coarse region hint.
         *
         * @param newRegion The legacy region hint; exact ownership uses the bot's current position.
         */
        @Suppress("UNUSED_PARAMETER")
        fun updateZone(bot: Bot, newRegion: Int) = updateZone(bot)

        /**
         * Enumerates the complete region grid, including intermediate regions of large rectangles.
         */
        private fun regionsOf(part: SimpleBoxArea): Set<Int> = buildSet {
            for (x in (part.southWest.x shr 6)..(part.northEast.x shr 6)) {
                for (y in (part.southWest.y shr 6)..(part.northEast.y shr 6)) add((x shl 8) or y)
            }
        }

        /**
         * Generates a text dump of discovered bank object positions grouped by zone.
         *
         * This scans every region assigned to each zone, loads each unique chunk only once, and records the positions of any
         * objects whose id is registered as a banking object in [Banking.bankingObjects].
         *
         * The output is written to system output in a Kotlin-friendly `Position(x, y, z),` format so the results can be
         * copied back into source code or zone data.
         */
        fun generateBankPositions() {
            val sb = StringBuilder()
            for (zone in entries) {
                if (zone.bankAnchors.isNotEmpty()) {
                    continue
                }
                val checked = HashSet<Chunk>()
                val added = HashSet<Position>()
                val regions = zone.regions + zone.parts.flatMap { regionsOf(it) }
                for (region in regions.map { Region(it) }) {
                    for (position in region.allPositions) {
                        val chunk = position.chunk

                        if (checked.add(chunk)) {
                            val repository = world.chunks.load(chunk)

                            for (obj in repository.getAll<GameObject>(EntityType.OBJECT)) {
                                if (obj.id in Banking.bankingObjects && obj in zone) {
                                    added += obj.position
                                }
                            }
                        }
                    }
                }
                sb.append(zone).append('\n')
                for (position in added) {
                    sb.append("Position(")
                        .append(position.x)
                        .append(',')
                        .append(' ')
                        .append(position.y)
                        .append(',')
                        .append(' ')
                        .append(position.z)
                        .append("),\n")
                }
            }
            println(sb.toString())
        }
    }

    /**
     * A set of cached banks from the [bankAnchors]: the booth on each, or the banker that spawns there. Generated
     * lazily on first invocation.
     */
    val banks = HashSet<Entity>()
        get() {
            if (field.isEmpty()) {
                bankAnchors.map { position ->
                    world.locator.findObjectsOnTile(position) { it.id in Banking.bankingObjects }.firstOrNull()
                        ?: world.locator.findNpcs(position, Position.VIEWING_DISTANCE) {
                            it.basePosition == position && it.def().actions.contains("Bank")
                        }.firstOrNull()
                }.forEach {
                    if (it != null) {
                        field.add(it)
                    }
                }
            }
            return field
        }

    /**
     * Determines if [locatable] is inside this zone.
     *
     * Uses the same indexed physical ownership lookup as movement, including shared-region refinements.
     *
     * @param locatable The locatable entity or position wrapper to check.
     * @return `true` if this zone owns [locatable]'s actual position.
     */
    operator fun contains(locatable: Locatable?) = of(locatable) == this
}
