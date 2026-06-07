package api.bot.zone

import api.bot.zone.Zone.Companion.REGIONS
import api.predef.*
import com.google.common.base.Preconditions.checkState
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import engine.bank.Banking
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.Luna
import io.luna.game.model.EntityType
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.Region
import io.luna.game.model.chunk.Chunk
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject

/**
 * Defines a large world area that bots can reason about for travel, banking, safety, and activity selection.
 *
 * A zone is made up of one or more map regions. Regions should not overlap between zones, because region ownership is
 * used as a cheap lookup for determining a bot's current zone.
 *
 * Each zone has:
 * - An [anchor] used as a representative destination or center point.
 * - A set of [regions] that belong to the zone.
 * - Optional [bankAnchors] for banking-aware bot behavior.
 * - Ordered [travel] strategies that bots can try when moving to the zone.
 * - A [safe] flag used to separate normal zones from dangerous areas like the Wilderness.
 *
 * @property anchor A representative position inside this zone.
 * @property regions The region ids that belong to this zone.
 * @property bankAnchors Known bank object or bank-adjacent positions inside this zone.
 * @property travel Ordered travel strategies that can be used to reach this zone.
 * @property safe Whether this zone is considered safe for normal bot activity.
 *
 * @author lare96
 */
enum class Zone(val anchor: Position,
                val regions: Set<Int>,
                val bankAnchors: List<Position> = emptyList(),
                val travel: List<TravelStrategy> = emptyList(),
                val safe: Boolean = true) {
    DRAYNOR(anchor = Position(3093, 3244),
            regions = setOf(12338, 12339, 12340),
            bankAnchors = listOf(Position(3091, 3245, 0),
                                 Position(3091, 3242, 0),
                                 Position(3091, 3243, 0)),
            travel = listOf(JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 3),
                            TeleportTravelStrategy(TeleportSpell.LUMBRIDGE),
                            HomeTravelStrategy,
                            WalkingTravelStrategy)),

    EDGEVILLE(anchor = Position(3087, 3496),
              regions = setOf(12342),
              bankAnchors = listOf(Position(3098, 3493, 0),
                                   Position(3096, 3493, 0),
                                   Position(3095, 3489, 0),
                                   Position(3095, 3491, 0)),
              travel = listOf(JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                              HomeTravelStrategy,
                              WalkingTravelStrategy)),

    VARROCK(anchor = Luna.settings().game().startingPosition(),
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
                                 Position(3186, 3442, 0)),
            travel = listOf(HomeTravelStrategy,
                            WalkingTravelStrategy)),

    FALADOR(anchor = Position(2964, 3377),
            regions = setOf(11829, 12085, 12084, 12083, 11827, 11571),
            bankAnchors = listOf(Position(3010, 3354, 0),
                                 Position(3011, 3354, 0),
                                 Position(3015, 3354, 0),
                                 Position(3013, 3354, 0),
                                 Position(3012, 3354, 0),
                                 Position(3014, 3354, 0)),
            travel = listOf(TeleportTravelStrategy(TeleportSpell.FALADOR),
                            JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 3),
                            HomeTravelStrategy,
                            WalkingTravelStrategy)),

    LUMBRIDGE(anchor = Position(3222, 3219),
              regions = setOf(12594, 12850, 12849, 12593),
              bankAnchors = listOf(),
              travel = listOf(TeleportTravelStrategy(TeleportSpell.LUMBRIDGE),
                              JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 3),
                              HomeTravelStrategy,
                              WalkingTravelStrategy)),

    AL_KHARID(anchor = Position(3277, 3224),
              regions = setOf(13107, 13106, 13362, 13361, 13105),
              bankAnchors = listOf(Position(3268, 3169, 0),
                                   Position(3268, 3168, 0),
                                   Position(3268, 3164, 0),
                                   Position(3268, 3167, 0),
                                   Position(3268, 3166, 0)),
              travel = listOf(JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 4),
                              JewelleryTravelStrategy(TeleportJewellery.DUELING_RING, 1),
                              TeleportTravelStrategy(TeleportSpell.LUMBRIDGE),
                              HomeTravelStrategy,
                              WalkingTravelStrategy)),

    SEERS_VILLAGE(anchor = Position(2723, 3485),
                  regions = setOf(10806),
                  bankAnchors = listOf(Position(2728, 3494, 0),
                                       Position(2729, 3494, 0),
                                       Position(2721, 3494, 0),
                                       Position(2722, 3494, 0),
                                       Position(2727, 3494, 0),
                                       Position(2724, 3494, 0)),
                  travel = listOf(TeleportTravelStrategy(TeleportSpell.CAMELOT),
                                  JewelleryTravelStrategy(TeleportJewellery.GAMES_NECKLACE, 1),
                                  JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                                  JewelleryTravelStrategy(TeleportJewellery.DUELING_RING, 2),
                                  HomeTravelStrategy,
                                  WalkingTravelStrategy)),

    CATHERBY(anchor = Position(2809, 3435),
             regions = setOf(11061, 11317),
             bankAnchors = listOf(Position(2809, 3442, 0),
                                  Position(2811, 3442, 0),
                                  Position(2810, 3442, 0),
                                  Position(2807, 3442, 0)),
             travel = listOf(TeleportTravelStrategy(TeleportSpell.CAMELOT),
                             JewelleryTravelStrategy(TeleportJewellery.DUELING_RING, 2),
                             HomeTravelStrategy,
                             WalkingTravelStrategy)),

    ARDOUGNE(anchor = Position(2661, 3306),
             regions = setOf(9779, 10035, 10291, 10292, 10290, 10547, 10548, 10803, 10804),
             bankAnchors = listOf(Position(2656, 3283, 0),
                                  Position(2618, 3331, 0),
                                  Position(2619, 3331, 0),
                                  Position(2615, 3331, 0),
                                  Position(2656, 3286, 0)),
             travel = listOf(TeleportTravelStrategy(TeleportSpell.ARDOUGNE),
                             TeleportTravelStrategy(TeleportSpell.CAMELOT),
                             JewelleryTravelStrategy(TeleportJewellery.DUELING_RING, 2),
                             HomeTravelStrategy,
                             WalkingTravelStrategy)),

    YANILLE(anchor = Position(2612, 3101),
            regions = setOf(10288, 10032),
            bankAnchors = listOf(Position(2614, 3094, 0),
                                 Position(2614, 3092, 0),
                                 Position(2614, 3091, 0)),
            travel = listOf(TeleportTravelStrategy(TeleportSpell.WATCHTOWER),
                            TeleportTravelStrategy(TeleportSpell.ARDOUGNE),
                            JewelleryTravelStrategy(TeleportJewellery.DUELING_RING, 2),
                            TeleportTravelStrategy(TeleportSpell.CAMELOT),
                            HomeTravelStrategy,
                            WalkingTravelStrategy)),

    WILDERNESS(anchor = Position(3088, 3544),
               regions = setOf(11831, 11832, 11833, 11834, 11835, 11836, 11837, 12087, 12088, 12089, 12090, 12091,
                               12092, 12093, 12343, 12344, 12345, 12346, 12347, 12348, 12349, 12599, 12600, 12601,
                               12602, 12603, 12604, 12605, 12855, 12856, 12857, 12858, 12859, 12860, 12861, 13111,
                               13112, 13113, 13114, 13115, 13116, 13117, 13367, 13368, 13369, 13370, 13371, 13372,
                               13373),
               bankAnchors = EDGEVILLE.bankAnchors,
               travel = listOf(JewelleryTravelStrategy(TeleportJewellery.AMULET_OF_GLORY, 1),
                               TeleportTravelStrategy(TeleportSpell.VARROCK),
                               TeleportTravelStrategy(TeleportSpell.DAREEYAK),
                               TeleportTravelStrategy(TeleportSpell.CARRALLANGER),
                               TeleportTravelStrategy(TeleportSpell.ANNAKARL),
                               TeleportTravelStrategy(TeleportSpell.GHORROCK),
                               HomeTravelStrategy,
                               WalkingTravelStrategy),
               safe = false),

    RELLEKKA(anchor = Position(2666, 3641),
             regions = setOf(10297, 10553, 10554, 10810, 10809, 10296, 10552, 10808),
             bankAnchors = SEERS_VILLAGE.bankAnchors,
             travel = listOf(TeleportTravelStrategy(TeleportSpell.CAMELOT),
                             HomeTravelStrategy,
                             WalkingTravelStrategy));

    companion object {

        /**
         * All zones marked as safe.
         *
         * Unsafe zones, such as [WILDERNESS], are excluded from this list so callers can choose normal bot destinations
         * without manually filtering dangerous areas.
         */
        val SAFE_ZONES = ImmutableList.copyOf(entries.filter { it.safe })

        /**
         * A region-to-zone lookup table.
         *
         * This map allows zone checks to be resolved from a region id instead of scanning every [Zone]. Each region id should
         * belong to only one zone.
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
         * Updates a bot's current zone from its latest region id.
         *
         * If [newRegion] is not registered in [REGIONS], the bot's zone is set to `null`.
         *
         * @param bot The bot whose zone should be updated.
         * @param newRegion The region id the bot is currently inside.
         */
        fun updateZone(bot: Bot, newRegion: Int) {
            bot.zone = REGIONS[newRegion]
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
                for (region in zone.regions.map { Region(it) }) {
                    for (position in region.allPositions) {
                        val chunk = position.chunk

                        if (checked.add(chunk)) {
                            val repository = world.chunks.load(chunk)

                            for (obj in repository.getAll<GameObject>(EntityType.OBJECT)) {
                                if (obj.id in Banking.bankingObjects) {
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
     * A set of cached bank [GameObject]s from the [bankAnchors]. Generated lazily on first invocation.
     */
    val banks = HashSet<GameObject>()
        get() {
            if (field.isEmpty()) {
                field.addAll(bankAnchors.map { position ->
                    world.locator.findObjectsOnTile(position) { it.id in Banking.bankingObjects }.first()
                }.toSet())
            }
            return field
        }

    /**
     * Determines if [locatable] is inside this zone.
     *
     * This check is region-based, so it is cheap and intended for broad zone membership rather than exact rectangular or
     * polygonal area checks.
     *
     * @param locatable The locatable entity or position wrapper to check.
     * @return `true` if [locatable] is inside one of this zone's regions.
     */
    operator fun contains(locatable: Locatable?) = if (locatable == null) false else
        regions.contains(locatable.abs().regionId)
}