package api.bot.zone

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.zone.SubZone.Companion.updateSubZone
import api.bot.zone.Zone.*
import api.predef.*
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableSetMultimap
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.future.await

/**
 * A smaller rectangular bot activity area within a broader [Zone].
 *
 * A [SubZone] gives bots a precise local area to work inside after broad travel has already chosen a parent [Zone].
 * The parent zone is still used for long-distance routing, banking fallback, and general area selection, while the
 * subzone is used for local activity decisions such as fishing, mining, woodcutting, thieving, or combat training.
 *
 * Subzone [area] values should not overlap. Overlaps can make [updateSubZone] assign whichever matching subzone appears
 * first in enum order, which may cause bots to pick the wrong script target or local anchor.
 *
 * Each subzone has:
 * - [inside], the preferred local anchor used after the bot is inside or near the subzone.
 * - [outside], an optional dynamic external anchor for entrances, ladders, gates, portals, levers, or boats.
 * - [area], the rectangular tile bounds used for containment checks and region indexing.
 * - [parent], the dynamic broader travel zone used for routing and fallback behavior.
 *
 * @author lare96
 */
enum class SubZone(val inside: Position,
                   val outside: Bot.() -> Position? = { null },
                   val area: SimpleBoxArea,
                   val parent: Bot.() -> Zone) {

    /**
     * The area east of Draynor where the trapdoor is located. Intended for money-making yew/low-level woodcutters
     * and low-level combat bots.
     * - 3 yew trees
     * - Normal trees
     * - Oak trees
     * - Low level goblins
     */
    EAST_DRAYNOR_YEWS(inside = Position(3166, 3238),
                      area = SimpleBoxArea.of(3138, 3202, 3197, 3259),
                      parent = { DRAYNOR }),

    /**
     * The Al-Kharid palace. Used for low-level combat training.
     */
    AL_KHARID_PALACE(inside = Position(3292, 3176),
                     area = SimpleBoxArea.of(3282, 3159, 3303, 3177),
                     parent = { AL_KHARID }),

    /**
     * The Edgeville monastery. Used for low-level -> mid-level combat training.
     */
    EDGEVILLE_MONASTERY(inside = Position(3051, 3482),
                        area = SimpleBoxArea.of(3041, 3480, 3062, 3509),
                        parent = { EDGEVILLE }),

    /**
     * The wizards' tower area. Intended for low-level combat training.
     * - Low level wizards
     */
    WIZARDS_TOWER(inside = Position(3113, 3195),
                  area = SimpleBoxArea.of(3090, 3143, 3127, 3177),
                  parent = { DRAYNOR }),

    /**
     * The home area, currently Varrock west bank. Intended for smithers, pickpockets, dumb cookers, and low -> avg. intelligence
     * firemakers.
     */
    HOME(inside = VARROCK.anchor,
         area = SimpleBoxArea.of(3171, 3425, 3198, 3451),
         parent = { VARROCK }),

    /**
     * The deep wilderness area where the 10-coin chest is contained.
     */
    TEN_COIN_CHEST_HUT(inside = Position(3190, 3959),
                       area = SimpleBoxArea.of(3184, 3953, 3198, 3965),
                       parent = { WILDERNESS }),

    /**
     * The room with the steel arrowtip chest in Hemenster, south-west of seers' village.
     */
    HEMENSTER_CHEST_ROOM(inside = Position(2642, 3447),
                         area = SimpleBoxArea.of(2625, 3410, 2643, 3455),
                         parent = { SEERS_VILLAGE }),


    /**
     * The chaos druid tower north of Ardougne.
     */
    CHAOS_DRUID_TOWER(inside = Position(2565, 3355),
                      area = SimpleBoxArea.of(2557, 3349, 2573, 3362),
                      parent = { ARDOUGNE }),

    /**
     * The rock crabs north of Rellekka. Intended for intelligent mid-level combat training.
     */
    ROCK_CRABS(inside = Position(2672, 3714),
               area = SimpleBoxArea.of(2648, 3711, 2687, 3742),
               parent = { RELLEKKA }),

    /**
     * The chaos druid tower dungeon.
     * - Ogres
     * - Blood rune thieving chest
     */
    CHAOS_DRUID_TOWER_DUNGEON(inside = Position(2565, 9753),
                              outside = { Position(2565, 3355) },
                              area = SimpleBoxArea.of(2559, 9729, 2594, 9761),
                              parent = { ARDOUGNE }) {
        private val upstairsLadder =
            lazyVal { world.locator.findObjectsOnTile(Position(2562, 3356)) { it.id == 1754 }.first() }
        private val downstairsLadder =
            lazyVal { world.locator.findObjectsOnTile(Position(2562, 9756)) { it.id == 1755 }.first() }

        override suspend fun enter(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            return bot.actionHandler.interactions.interact(1, upstairsLadder.value)
        }

        override suspend fun leave(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            return bot.actionHandler.interactions.interact(1, downstairsLadder.value)
        }
    },

    /**
     * The Varrock east-bank. Contains ranges nearby which make it a valid cooking area.
     */
    VARROCK_EAST_BANK(inside = Position(3253, 3427),
                      area = SimpleBoxArea.of(3234, 3400, 3261, 3428),
                      parent = { VARROCK }),

    /**
     * The rune essence mine. Intended for low-level and low-confidence money makers.
     */
    ESSENCE_MINE(inside = Position(2910, 4832),
                 outside = { Position(3253, 3401) },
                 area = SimpleBoxArea.of(2882, 4805, 2939, 4860),
                 parent = { VARROCK }) {
        private val auburyNpc =
            lazyVal { world.locator.findViewableNpcs(Position(3253, 3401)) { it.id == 553 }.first() }

        override suspend fun enter(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            bot.log("Entering essence mine. Travelling to Aubury.")

            if (!bot.actionHandler.travelTo(VARROCK)) {
                bot.log("Failed to travel to Varrock for essence mine.")
                return false
            }

            val aubury = auburyNpc.value
            bot.log("Attempting Aubury teleport. aubury=${aubury.position}, bot=${bot.position}")

            while (!area.contains(bot)) {
                if (!bot.isViewableFrom(aubury)) {
                    bot.log(
                        "Aubury is no longer viewable but bot is not inside essence mine. " +
                                "bot=${bot.position}, subZone=${bot.subZone}"
                    )
                    break
                }

                if (bot.walking.isEmpty) {
                    bot.log("Clicking Aubury essence teleport. bot=${bot.position}, subZone=${bot.subZone}")
                    bot.actionHandler.interactions.interact(4, aubury)
                }

                bot.naturalDecisionDelay()
            }

            val entered = waitFor { area.contains(bot) }
            bot.log("Essence mine enter result=$entered, bot=${bot.position}, subZone=${bot.subZone}")
            return entered
        }

        override suspend fun leave(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            bot.log("Locating a portal to leave the area.")
            bot.naturalDelay()
            val portals = world.locator.findObjects(inside, area.tileRadius) { it.def().name.equals("Portal") }
            for (nextPortal in portals) {
                if (bot.actionHandler.interactions.interact(1, nextPortal)) {
                    return true
                }
                bot.naturalMicroDelay()
            }
            bot.log("Could not interact with any of the portals to leave.")
            return false
        }
    },

    /**
     * The Draynor bank and surrounding areas. Intended mainly for low-level fishers and dexterous/low-level woodcutters.
     * Low-level dumb bots may also train combat here.
     * - Bank booths
     * - 5 willow trees
     * - Normal trees
     * - Oak trees
     * - Net and bait fishing spots
     * - Guards at the jail
     */
    DRAYNOR_MAIN(inside = DRAYNOR.anchor,
                 area = SimpleBoxArea.of(3073, 3221, 3134, 3262),
                 parent =
                     { DRAYNOR }),

    /**
     * A low-level mining area near Varrock. Intended for low-level miners.
     * - 3 clay rocks
     * - 3 iron rocks
     * - 3 silver rocks
     * - 8 tin rocks
     */
    VARROCK_SW_MINE(inside = Position(3187, 3373),
                    area = SimpleBoxArea.of(3166, 3360, 3187, 3380),
                    parent =
                        { VARROCK }),

    /**
     * A low-level mining area near Varrock. Intended for low-level miners.
     * - 9 copper rocks
     * - 6 tin rocks
     * - 4 iron rocks
     */
    VARROCK_SE_MINE(inside = Position(3284, 3372),
                    area = SimpleBoxArea.of(3274, 3354, 3300, 3376),
                    parent =
                        { VARROCK }),

    /**
     * The yew trees around the Lumber Yard north-east of Varrock. Intended for low-level and money making woodcutters.
     * - 3 yew trees
     * - Normal trees
     * - Oak trees
     */
    LUMBER_YARD_YEWS(inside = Position(3287, 3459),
                     area = SimpleBoxArea.of(3265, 3457, 3309, 3513),
                     parent =
                         { VARROCK }),

    /**
     * The Rogues Den area. Intended for the best cooking training and profit.
     */
    ROGUES_DEN(inside = Position(3045, 4972, 1),
               outside = { Position(2906, 3537) },
               area = SimpleBoxArea.of(3036, 4961, 3067, 4988),
               parent = { BURTHORPE }) {

        private val enterTrapdoor =
            lazyVal { world.locator.findObjectsOnTile(Position(2905, 3537)) { it.id == 7257 }.first() }

        override suspend fun enter(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            if (bot.actionHandler.travelTo(BURTHORPE)) {
                // Navigate to the trapdoor that leads us into the dungeon.
                bot.navigator.navigate(selectedOutside, true).await()
                if (selectedOutside?.isViewableFrom(bot) == false) {
                    return false
                }
                // Interact with the ladder.
                if (!bot.actionHandler.interactions.interact(1, enterTrapdoor.value)) {
                    return false
                }
                return true
            }
            return false
        }

        override suspend fun leave(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            bot.output.sendCommand("home")
            return true
        }
    },

    /**
     * The Edgeville Dungeon mine. Intended for low -> high level money-making miners. They will use the brass key.
     * - 2 copper rocks
     * - 2 tin rocks
     * - 3 iron rocks
     * - 3 silver rocks
     * - 6 coal rocks
     * - 1 mithril rock
     * - 2 adamant rocks
     */
    EDGEVILLE_DUNGEON_MINE(inside = Position(3132, 9874),
                           outside = { Position(3115, 3448) },
                           area = SimpleBoxArea.of(3134, 9867, 3143, 9880),
                           parent = { VARROCK }) {
        private val varrockLadder =
            lazyVal { world.locator.findObjectsOnTile(Position(3116, 3452)) { it.id == 1754 }.first() }
        private val dungeonLadder =
            lazyVal { world.locator.findObjectsOnTile(Position(3116, 9852)) { it.id == 1755 }.first() }

        override suspend fun enter(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            if (bot.actionHandler.travelTo(VARROCK)) {

                // Navigate to the ladder that leads us into the dungeon.
                bot.navigator.navigate(selectedOutside, true).await()
                if (selectedOutside?.isViewableFrom(bot) == false) {
                    return false
                }

                // Interact with the ladder.
                if (!bot.actionHandler.interactions.interact(1, varrockLadder.value)) {
                    return false
                }
                return true
            }
            return false
        }

        override suspend fun leave(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            // Interact with the ladder inside the dungeon.
            return bot.actionHandler.interactions.interact(1, dungeonLadder.value)
        }
    },

    /**
     * The Edgeville Dungeon hill giants area. Intended for mid -> high level combat training.
     */
    EDGEVILLE_DUNGEON_HILL_GIANTS(inside = Position(3117, 9849),
                                  outside = EDGEVILLE_DUNGEON_MINE.outside,
                                  area = SimpleBoxArea.of(3090, 9820, 3128, 9855),
                                  parent = { VARROCK }) {
        override suspend fun enter(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            return EDGEVILLE_DUNGEON_MINE.enter(bot, selectedParent, selectedOutside)
        }

        override suspend fun leave(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
            return EDGEVILLE_DUNGEON_MINE.leave(bot, selectedParent, selectedOutside)
        }
    },

    /**
     * The yew trees north of Varrock Palace. Intended for money making woodcutters.
     * - 3 yew trees
     */
    VARROCK_PALACE_YEWS(inside = Position(3213, 3501),
                        area = SimpleBoxArea.of(3201, 3496, 3224, 3508),
                        parent =
                            { VARROCK }),

    /**
     * The yew trees south of Falador. Intended for low-level and money making woodcutters.
     * - 3 yew trees
     * - Oak trees
     * - Normal trees
     */
    SOUTH_FALADOR_YEWS(inside = Position(3008, 3314),
                       area = SimpleBoxArea.of(2991, 3303, 3043, 3327),
                       parent =
                           { FALADOR }),

    /**
     * The Lumbridge river area. Intended for bait drop-fishers and low-level combat.
     * - Goblins
     * - 2 bait fishing spots
     */
    LUMBRIDGE_RIVER(inside = Position(3249, 3228),
                    area = SimpleBoxArea.of(3234, 3227, 3261, 3251),
                    parent =
                        { LUMBRIDGE }),

    /**
     * The south Lumbridge mine. Intended for smart money-making miners.
     * - 7 coal rocks
     * - 5 mithril rocks
     * - 2 adamant rocks
     */
    SOUTH_LUMBRIDGE_MINE(inside = Position(3233, 3150),
                         area = SimpleBoxArea.of(3219, 3139, 3248, 3162),
                         parent = { LUMBRIDGE }),

    /**
     * The musa point fishing area. Intended for mid-level fishers.
     */
    MUSA_POINT_FISHING(inside = Position(2924, 3173),
               area = SimpleBoxArea.of(2922, 3173, 2928, 3181),
               parent = { KARAMJA }),

    /**
     * The Lumbridge swamp. Intended for low-level combat training.
     */
    LUMBRIDGE_SWAMP(inside = Position(3229, 3171),
                    area = SimpleBoxArea.of(3203, 3168, 3231, 3193),
                    parent = { LUMBRIDGE }),

    /**
     * Also known as the Scorpion Mine, this is a major mining area north of Al Kharid.
     * - 3 copper rocks
     * - 1 tin rock
     * - 9 iron rocks
     * - 5 silver rocks
     * - 3 coal rocks
     * - 2 gold rocks
     * - 2 mithril rocks
     * - 2 adamant rocks
     */
    AL_KHARID_MINE(inside = Position(3298, 3273),
                   area = SimpleBoxArea.of(3288, 3271, 3312, 3324),
                   parent =
                       { AL_KHARID }),

    /**
     * The flax field south-east of Seers' Village. Intended for money-makers.
     * - flax
     * - spinning wheel
     */
    SOUTH_SEERS_VILLAGE_FLAX(inside = Position(2735, 3441),
                             area = SimpleBoxArea.of(2734, 3436, 2751, 3453),
                             parent =
                                 { SEERS_VILLAGE }),

    /**
     * The yew trees south of Seers' Village. Intended for money-makers and low -> high level dumb woodcutters.
     * - 3 yew trees
     * - Normal trees
     * - Oak trees
     * - Maple trees
     */
    SOUTH_SEERS_VILLAGE_YEWS(inside = Position(2721, 3460),
                             area = SimpleBoxArea.of(2688, 3456, 2750, 3476),
                             parent =
                                 { CATHERBY }),

    /**
     * The main bank and trees near Seers' Village. Intended for mid-level woodcutters.
     * - 4 maple trees
     * - 6 willow trees
     * - Normal trees
     */
    SEERS_VILLAGE_MAIN(inside = Position(2723, 3504),
                       area = SimpleBoxArea.of(2688, 3478, 2740, 3514),
                       parent =
                           { SEERS_VILLAGE }),

    /**
     * The yew trees west of Catherby. Intended for intelligent money-makers.
     * - 6 yew trees
     * - Willow trees
     * - Normal trees
     */
    WEST_CATHERBY_YEWS(inside = Position(2763, 3429),
                       area = SimpleBoxArea.of(2750, 3419, 2774, 3434),
                       parent =
                           { CATHERBY }),

    /**
     * The Catherby fishing shore. Intended for low -> mid-range level fishing trainers and money-makers.
     * - Net and big net fishing
     * - Harpoon fishing
     * - Lobster pot fishing
     */
    EAST_CATHERBY_FISHING(inside = Position(2838, 3435),
                          area = SimpleBoxArea.of(2829, 3417, 2864, 3437),
                          parent =
                              { CATHERBY }),

    /**
     * The Piscatoris fishing colony. Intended for the highest level fishers.
     */
    PISCATORIS_FISHING_COLONY_MAIN(inside = PISCATORIS_FISHING_COLONY.anchor,
                          area = SimpleBoxArea.of(2306, 3663, 2363, 3705),
                          parent = { PISCATORIS_FISHING_COLONY }),

    /**
     * The Ardougne market square. Intended for dexterous thieving trainers and low -> mid-range level money-makers.
     * - Paladins
     * - Guards
     * - Silk stall
     * - Baker's stall
     * - Fur stall
     * - Gem stall
     */
    ARDOUGNE_SQUARE_THIEVING(inside = Position(2661, 3306),
                             area = SimpleBoxArea.of(2645, 3290, 2677, 3325),
                             parent =
                                 { ARDOUGNE }),

    /**
     * The south-east Ardougne mine. Intended for dexterous mining trainers.
     * - 13 iron rocks
     * - 4 coal rocks
     */
    SOUTH_EAST_ARDOUGNE_MINE(inside = Position(2601, 3240),
                             area = SimpleBoxArea.of(2579, 3215, 2625, 3240),
                             parent =
                                 { ARDOUGNE }),

    /**
     * The Legends' Guild mine, also known as the East Ardougne Mine. Intended for dexterous mining trainers and
     * intelligent money-makers.
     * - 11 iron rocks
     * - 13 coal rocks
     */
    LEGENDS_GUILD_MINE(inside = Position(2702, 3332),
                       area = SimpleBoxArea.of(2688, 3326, 2717, 3340),
                       parent =
                           { ARDOUGNE }),

    /**
     * The farming patch located north of Lumbridge. Intended for farmers and low-level thieving trainers.
     * - Hops patch
     * - Level 7 farmer (pickpocketing)
     */
    NORTH_LUMBRIDGE_FARMING(inside = Position(3229, 3309),
                            area = SimpleBoxArea.of(3223, 3309, 3242, 3321),
                            parent =
                                { LUMBRIDGE }),

    /**
     * The iconic Lumbridge courtyard. Intended for very low-level thieving trainers.
     * - Various men and women
     * - Hans
     */
    LUMBRIDGE_COURT_YARD(inside = LUMBRIDGE.anchor,
                         area = SimpleBoxArea.of(3215, 3205, 3230, 3229),
                         parent =
                             { LUMBRIDGE }),

    /**
     * A four-story tower south of Seers' Village.
     * - 4 magic trees
     * - Normal trees
     */
    SORCERERS_TOWER_MAGICS(inside = Position(2702, 3391),
                           area = SimpleBoxArea.of(2692, 3389, 2717, 3418),
                           parent =
                               { SEERS_VILLAGE }),

    /**
     * The iconic barbarian village, intended for low-level combat training, crafting training, and low -> high level
     * drop-fishing training.
     * - Fly fishing spots
     * - Barbarians
     * - Spinning wheel
     * - Pottery oven
     * - Potter's wheel
     */
    BARBARIAN_VILLAGE(inside = Position(3099, 3420),
                      area = SimpleBoxArea.of(3070, 3402, 3109, 3451),
                      parent =
                          { EDGEVILLE }),

    /**
     * The Al-kharid bank area. Intended for average dexerity/intelligence bots that need to tan, smelt, cook, and do
     * low-level fishing training.
     * - Tanner NPC
     * - Furnace
     * - Kebab shop & range
     * - 2 small net/bait fishing spots
     */
    AL_KHARID_BANK(inside = Position(3270, 3167),
                   area = SimpleBoxArea.of(3263, 3138, 3279, 3194),
                   parent =
                       { AL_KHARID }),

    /**
     * The goblin village area. Intended for beginner combat training.
     * - Plenty of low level goblins
     * - Infinitely burning fire
     */
    GOBLIN_VILLAGE(inside = Position(2955, 3502),
                   area = SimpleBoxArea.of(2944, 3481, 2970, 3518),
                   parent =
                       { FALADOR }),

    /**
     * The main flax spinning area inside Thessalia's clothing store.
     */
    FLAX_SPINNING_MAIN(inside = Position(3207, 3415),
                       area = SimpleBoxArea.of(3200, 3410, 3210, 3420),
                       parent = { VARROCK }),

    /**
     * The main eastern lumbridge cow pen.
     */
    LUMBRIDGE_COW_PEN(inside = Position(3257, 3279),
                      area = SimpleBoxArea.of(3240, 3253, 3265, 3298),
                      parent = { LUMBRIDGE }),

    /**
     * The chaos temple north of falador and to the north-west of goblin village. Primarily intended for low-level combat
     * training and telegrabbing wines money making method.
     * - Zamorak wine
     * - Plenty of lvl 17 monks of Zamorak
     */
    // TODO Spawn wines (maybe 2? one on each side of the table?)
    // TODO Make it so that trying to pick up the wines while monks have you in their view-cone results in damage, fire, etc.
    // TODO Test bots telegrabbing wines.
    NORTH_FALADOR_CHAOS_TEMPLE(inside = Position(2934, 3515),
                               area = SimpleBoxArea.of(2929, 3511, 2942, 3519),
                               parent = { FALADOR });

    /*
     * TODO@0.5.0 Implement Mining Guild access and routing.
     *
     * Add the subzone, mining target selection, route behavior, and any entrance/access checks required for bots to use the
     * Mining Guild safely.
     */

    /*
     * TODO@0.5.0 Implement KBD_LAIR.
     *
     * Planned definition:
     * - inside = Position(2900, 3294)
     * - outside = Position(3004, 3849)
     * - area = SimpleBoxArea.of(2249, 4674, 2291, 4717)
     * - parent = WILDERNESS
     *
     * Remaining work:
     * - Verify the inside/outside coordinates.
     * - Add KBD combat/bossing support.
     * - Implement gates.
     * - Fix simple door handling if required.
     * - Redo/fix docs after the route is confirmed.
     * - TODO@1.0 enter(): Implement wilderness dungeon + lever route into the KBD lair.
     * - TODO@1.0 leave(): Implement lever route out of the KBD lair.
     */

    /*
     * TODO@0.5.0 Implement LVL_20_WILDERNESS_CHAOS_TEMPLE.
     *
     * Planned definition:
     * - area = SimpleBoxArea.of(2929, 3511, 2942, 3519)
     * - parent = EDGEVILLE
     *
     * Used for:
     * - Telegrabbing wines money-making method.
     *
     * Remaining work:
     * - Determine whether this subzone needs inside, outside, or both.
     */

    /*
     * TODO@0.5.0 Implement LVL_40_WILDERNESS_CHAOS_TEMPLE.
     *
     * Planned definition:
     * - area = SimpleBoxArea.of(2929, 3511, 2942, 3519)
     * - parent = EDGEVILLE
     *
     * Used for:
     * - Telegrabbing wines money-making method.
     *
     * Remaining work:
     * - Determine whether this subzone needs inside, outside, or both.
     */

    companion object {

        /**
         * Maps each loaded map region to the subzones that touch it.
         *
         * This is used as a cheap pre-filter before checking exact tile containment. A bot only scans local subzones
         * for its current region instead of checking every subzone every time it moves.
         */
        val LOCAL: ImmutableSetMultimap<Int, SubZone> = run {
            val map = HashMultimap.create<Int, SubZone>()

            for (zone in entries) {
                for (region in zone.area.touchedRegions) {
                    map.put(region, zone)
                }
            }

            ImmutableSetMultimap.copyOf(map)
        }

        /**
         * Describes a rectangular overlap between two subzone areas.
         *
         * @property first The first overlapping subzone.
         * @property second The second overlapping subzone.
         * @property southWest The inclusive south-west corner of the overlap.
         * @property northEast The inclusive north-east corner of the overlap.
         */
        private data class AreaOverlap(
            val first: SubZone,
            val second: SubZone,
            val southWest: Position,
            val northEast: Position
        )

        /**
         * Validates that no two subzone rectangles overlap.
         *
         * This is useful as a startup assertion or test helper. Overlaps are not automatically resolved because the
         * correct fix depends on the intended gameplay area.
         *
         * @throws IllegalStateException If any two subzones overlap.
         */
        fun findAreaOverlaps() {
            for (firstIndex in 0 until entries.size) {
                val first = entries[firstIndex]

                for (secondIndex in firstIndex + 1 until entries.size) {
                    val second = entries[secondIndex]
                    val overlap = findOverlap(first, second) ?: continue

                    throw IllegalStateException("${overlap.first} overlaps ${overlap.second} at " +
                                                        "SW=${overlap.southWest}, NE=${overlap.northEast}")
                }
            }
        }

        /**
         * Finds the rectangular overlap between two subzones.
         *
         * @param first The first subzone to compare.
         * @param second The second subzone to compare.
         * @return The overlap details, or `null` if the areas do not overlap.
         */
        private fun findOverlap(first: SubZone, second: SubZone): AreaOverlap? {
            val firstSouthWest = first.area.southWest
            val firstNorthEast = first.area.northEast
            val secondSouthWest = second.area.southWest
            val secondNorthEast = second.area.northEast

            val overlapSouthWestX = maxOf(firstSouthWest.x, secondSouthWest.x)
            val overlapSouthWestY = maxOf(firstSouthWest.y, secondSouthWest.y)
            val overlapNorthEastX = minOf(firstNorthEast.x, secondNorthEast.x)
            val overlapNorthEastY = minOf(firstNorthEast.y, secondNorthEast.y)

            if (overlapSouthWestX > overlapNorthEastX || overlapSouthWestY > overlapNorthEastY) {
                return null
            }

            return AreaOverlap(
                first = first,
                second = second,
                southWest = Position(overlapSouthWestX, overlapSouthWestY),
                northEast = Position(overlapNorthEastX, overlapNorthEastY)
            )
        }

        /**
         * Updates the cached local subzone list for a bot after its region changes.
         *
         * This should be called from movement/region-change handling before [updateSubZone]. The cached list keeps
         * subzone detection cheap by only checking subzones that touch the bot's current region.
         *
         * @param bot The bot whose local subzone cache is being updated.
         * @param newRegion The bot's new map region id.
         */
        fun updateLocalSubZones(bot: Bot, newRegion: Int) {
            bot.localSubZones = LOCAL[newRegion]
        }

        /**
         * Updates the bot's current subzone from its cached local subzones.
         *
         * The bot is assigned the first local subzone whose [SubZone.area] contains it. If no local subzone contains
         * the bot, [Bot.subZone] is cleared.
         *
         * @param bot The bot whose current subzone is being updated.
         */
        fun updateSubZone(bot: Bot) {
            bot.subZone = null
            if (bot.localSubZones.isNotEmpty()) {
                for (zone in bot.localSubZones) {
                    if (bot in zone.area) {
                        bot.subZone = zone
                        break
                    }
                }
            }
        }
    }

    /**
     * Checks whether a [Bot] is close to this subzone's local or external anchor.
     *
     * @param bot The bot to check.
     * @param distance The maximum allowed tile distance.
     * @return `true` if [bot] is within [distance] of [inside] or [outside].
     */
    fun isWithinDistance(bot: Bot, distance: Int): Boolean {
        if (bot.subZone == this || inside.isWithinDistance(bot, distance)) {
            return true
        }
        val outsidePosition = outside(bot)
        return outsidePosition != null && outsidePosition.isWithinDistance(bot, distance)
    }

    /**
     * Attempts to move the bot from the selected outside anchor into this subzone.
     *
     * Most overworld subzones do not need a custom enter action because the selected parent [Zone] can usually walk
     * directly to [inside]. Override this for caves, dungeons, guilds, ladders, levers, boats, doors, portals, or any
     * other area where the bot must perform an interaction before [area] becomes reachable.
     *
     * [selectedParent] and [selectedOutside] are passed in because both may be dynamic. For example, the rune essence
     * mine may choose Varrock/Aubury or Yanille/Distentor depending on the bot's current stats and location.
     *
     * @param bot The bot attempting to enter.
     * @param selectedParent The parent zone chosen for this entry attempt.
     * @param selectedOutside The outside anchor chosen for this entry attempt, or `null` if none is needed.
     * @return `true` if the bot entered successfully, otherwise `false`.
     */
    open suspend fun enter(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
        return true
    }

    /**
     * Attempts to move the bot out of this subzone and back toward the selected parent zone.
     *
     * Most overworld subzones do not need a custom leave action. Override this for isolated areas, underground areas,
     * dangerous areas, guilds, caves, portals, ladders, levers, doors, boats, or any place where normal walking cannot
     * resume until the bot uses a specific exit.
     *
     * @param bot The bot attempting to leave.
     * @param selectedParent The parent zone the bot should return toward after leaving.
     * @param selectedOutside The outside anchor associated with the route, or `null` if none is needed.
     * @return `true` if the bot left successfully, otherwise `false`.
     */
    open suspend fun leave(bot: Bot, selectedParent: Zone, selectedOutside: Position?): Boolean {
        return true
    }
}