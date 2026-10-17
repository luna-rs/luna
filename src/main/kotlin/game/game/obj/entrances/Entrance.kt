package game.obj.entrances

import game.obj.entrances.EntranceLanding.Companion.across
import game.obj.entrances.EntranceLanding.Companion.acrossNorth
import game.obj.entrances.EntranceLanding.Companion.near
import game.obj.entrances.EntranceLanding.Companion.row
import game.obj.entrances.EntranceLanding.Companion.shift
import game.obj.entrances.EntranceLanding.Companion.tile
import io.luna.game.model.Direction
import io.luna.game.model.Position

/**
 * Cave and dungeon entrances and exits that take the player somewhere when clicked. Entrances that need an item, a
 * fee, a skill level or a choice are handled in their own scripts instead.
 *
 * Quest requirements aren't checked because Luna doesn't have that content yet; the entrances that need one say so in
 * a TODO.
 *
 * @param id The object.
 * @param landing Where it takes the player.
 * @param message The message sent when it's used, if any.
 * @param delay How many ticks after [message] the player moves.
 * @param arrival The message sent once the player arrives, if any.
 * @param animation The animation played when it's used, if any.
 * @param arriveDelay Whether to wait a tick first if the player has only just walked up to it.
 * @param face The direction to face after arriving, if any.
 * @param position Where the object is, for objects that lead somewhere different from each spot. `null` if it leads
 * to [landing] from everywhere.
 *
 * @author TheLining
 */
enum class Entrance(val id: Int,
                    val landing: EntranceLanding,
                    val message: String? = null,
                    val delay: Int = 0,
                    val arrival: String? = null,
                    val animation: Int? = null,
                    val arriveDelay: Boolean = false,
                    val face: Direction? = null,
                    val position: Position? = null) {

    // Karamja Volcano.
    KARAMJA_VOLCANO_DOWN(id = 492, landing = shift(0, 6400), message = "You climb down through the pot hole.",
                         arriveDelay = true),
    KARAMJA_VOLCANO_UP(id = 1764, landing = tile(2856, 3167), message = "You climb up the hanging rope...",
                       delay = 3, arrival = "You appear on the volcano rim."),

    // TzHaar City, from the Karamja Volcano dungeon.
    TZHAAR_CITY_IN(id = 9358, landing = tile(2480, 5174), message = "You walk through the passage...", delay = 1),
    TZHAAR_CITY_OUT(id = 9359, landing = tile(2862, 9572), message = "You walk through the passage...", delay = 1),

    // Crandor, the hole down into Elvarg's lair and the rope back up (Dragon Slayer).
    CRANDOR_DOWN(id = 2609, landing = tile(2833, 9658)),
    CRANDOR_UP(id = 2610, landing = tile(2833, 3257)),

    // Fremennik Slayer Dungeon.
    FREMENNIK_SLAYER_DUNGEON_IN(id = 4499, landing = tile(2808, 10002)),
    FREMENNIK_SLAYER_DUNGEON_OUT(id = 4500, landing = tile(2796, 3615)),

    // Kalphite Lair and the Kalphite Queen's chamber, once a rope is tied to the entrance.
    KALPHITE_LAIR_IN(id = 3828, landing = tile(3483, 9510, 2)),
    KALPHITE_LAIR_OUT(id = 3829, landing = tile(3226, 3108)),
    KALPHITE_QUEEN_IN(id = 3831, landing = tile(3508, 9493)),
    KALPHITE_QUEEN_OUT(id = 3832, landing = tile(3508, 9497, 2)),

    // Lumbridge Swamp Caves: the rope back up, and the tunnels to the Tears of Guthix cave.
    // TODO Once darkness is added, players without a light source get bitten by insects in here.
    LUMBRIDGE_SWAMP_CAVES_UP(id = 5946, landing = shift(0, -6400), delay = 1, animation = 828),
    TEARS_OF_GUTHIX_TUNNEL_IN(id = 6659, landing = tile(3219, 9532, 2), delay = 1),
    TEARS_OF_GUTHIX_TUNNEL_OUT(id = 6658, landing = tile(3226, 9542), delay = 1),

    // Tears of Guthix, the rocks down to Juna and the one-way rocks back from the far side.
    TEARS_OF_GUTHIX_ROCKS(id = 6673, landing = across(3239, 3241, 2), delay = 3),
    TEARS_OF_GUTHIX_FAR_ROCKS(id = 6672, landing = tile(3240, 9498, 2), delay = 3),

    // The Lost Tribe's tunnel into the Lumbridge Swamp Caves.
    // TODO Once The Lost Tribe is added, the tunnel is blocked with rocks until the quest has been started.
    LOST_TRIBE_TUNNEL(id = 6912, landing = acrossNorth(9600, 9604), delay = 3),

    // Smoke Dungeon, under the well near Pollnivneach.
    // TODO Once Desert Treasure is added, only players looking for the four diamonds may climb down. Others say "I
    //  don't really fancy climbing down a well in the middle of the desert for no good reason...".
    SMOKE_DUNGEON_DOWN(id = 6279, landing = tile(3206, 9379), delay = 1, animation = 827),
    SMOKE_DUNGEON_UP(id = 6439, landing = tile(3310, 2961), delay = 1, animation = 828),

    // The Asgarnian Ice Dungeon's skeletal wyvern cave.
    WYVERN_CAVE_IN(id = 10596, landing = row(9555)),
    WYVERN_CAVE_OUT(id = 10595, landing = row(9562)),

    // Waterbirth Island Dungeon, and the cave back down from Askeladden's hill.
    WATERBIRTH_DUNGEON_IN(id = 8929, landing = tile(2442, 10146)),
    WATERBIRTH_HILL_CAVE(id = 8930, landing = tile(2545, 10143)),

    // Burthorpe, out of the Rogues' Den to the pub.
    ROGUES_DEN_OUT(id = 7258, landing = tile(2906, 3537), message = "You walk through the passage...", delay = 1),

    // Entrana dungeon's magic door, out to the Wilderness.
    ENTRANA_DUNGEON_OUT(id = 2407, landing = tile(3250, 3772), message = "You feel the world around you dissolve...",
                        face = Direction.NORTH),

    // East Ardougne, the cave into the sewers (Hazeel Cult).
    ARDOUGNE_SEWERS_IN(id = 2852, landing = tile(2570, 9682), message = "You enter the cave."),

    // The tunnels between Rellekka, Trollweiss Mountain and the cave outside Keldagrim.
    RELLEKKA_TUNNEL_IN(id = 5008, landing = tile(2773, 10162)),
    RELLEKKA_TUNNEL_OUT(id = 5014, landing = tile(2730, 3713)),
    TROLLWEISS_TUNNEL_IN(id = 5012, landing = tile(2799, 10134)),
    TROLLWEISS_TUNNEL_OUT(id = 5013, landing = tile(2797, 3719)),
    KELDAGRIM_CAVE_IN(id = 5973, landing = tile(2838, 10124)),
    KELDAGRIM_CAVE_OUT(id = 5998, landing = tile(2778, 10161)),

    // Trollweiss Mountain and the troll caves under it (Troll Romance).
    TROLL_ROMANCE_CAVE_IN(id = 5007, landing = tile(2803, 10187)),
    TROLL_ROMANCE_CAVE_OUT(id = 5011, landing = tile(2822, 3744)),
    TROLL_ROMANCE_TUNNEL_IN(id = 5009, landing = tile(2772, 10231)),
    TROLL_ROMANCE_CREVASSE(id = 5025, landing = tile(2778, 3869)),

    // Feldip Hills, Rantz's cave (Big Chompy Bird Hunting).
    RANTZ_CAVE_IN(id = 3379, landing = tile(2647, 9379),
                  message = "You walk through the cave entrance into a dimly lit cave."),
    RANTZ_CAVE_OUT(id = 3380, landing = tile(2630, 2997),
                   message = "You walk back out of the darkness of the cave into daylight."),
    RANTZ_CAVE_OUT_2(id = 3381, landing = tile(2630, 2997),
                     message = "You walk back out of the darkness of the cave into daylight."),

    // The goblin cave north of Hemenster (Dwarf Cannon).
    // TODO Once Dwarf Cannon is added, entering while looking for the goblin cave moves the quest on.
    DWARF_CANNON_CAVE_IN(id = 2, landing = tile(2620, 9797), delay = 2, arrival = "You cautiously enter the cave."),
    DWARF_CANNON_CAVE_OUT(id = 13, landing = tile(2623, 3391), delay = 1, animation = 828),

    // The Underground Pass, west of West Ardougne.
    // TODO Once Biohazard and Underground Pass are added, only players who have finished Biohazard ("You must
    //  complete the Biohazard quest before you can enter.") and talked to King Lathas ("You must talk to King Lathas
    //  before you can enter.") may go in, and the first time Koftik talks to them instead.
    UNDERGROUND_PASS_IN(id = 3213, landing = tile(2494, 9716), message = "You cautiously enter the cave...", delay = 4),
    UNDERGROUND_PASS_OUT(id = 3214, landing = tile(2436, 3315), message = "You leave the underground pass.",
                         delay = 4),

    // The troll pass caves under Trollheim.
    TROLLHEIM_CAVE_SOUTH_IN(id = 3757, landing = tile(2907, 10019), position = Position(2903, 3644)),
    TROLLHEIM_CAVE_NORTH_IN(id = 3757, landing = tile(2907, 10035), position = Position(2907, 3652)),
    TROLLHEIM_CAVE_SOUTH_OUT(id = 3758, landing = tile(2904, 3643), position = Position(2906, 10017)),
    TROLLHEIM_CAVE_NORTH_OUT(id = 3758, landing = tile(2908, 3654), position = Position(2906, 10036)),

    // Mad Eadgar's cave on top of Trollheim.
    // TODO Once Troll Stronghold is added, players who haven't freed Eadgar go to the copy of the cave without him,
    //  on the ground floor.
    EADGARS_CAVE_IN(id = 3759, landing = tile(2893, 10074, 2)),
    EADGARS_CAVE_OUT(id = 3760, landing = tile(2893, 3671)),

    // The Troll Stronghold.
    STRONGHOLD_IN(id = 3771, landing = tile(2837, 10090, 2)),
    STRONGHOLD_OUT(id = 3772, landing = tile(2840, 3690)),
    STRONGHOLD_OUT_2(id = 3773, landing = tile(2840, 3690)),
    STRONGHOLD_OUT_3(id = 3774, landing = tile(2840, 3690)),

    // The ice troll cave on the Ice Path (Desert Treasure).
    // TODO Once Desert Treasure is added, it stays blocked by ice ("The entrance to the cave is covered in too much
    //  ice to get through.") until the five ice trolls are dead.
    ICE_PATH_CAVE_IN(id = 6440, landing = tile(2874, 3720)),
    ICE_PATH_CAVE_OUT(id = 6447, landing = tile(2867, 3719)),

    // Saba's cave, on the way up Death Plateau.
    SABAS_CAVE_IN(id = 3735, landing = tile(2269, 4752), arriveDelay = true),
    SABAS_CAVE_OUT(id = 3736, landing = tile(2858, 3577), arriveDelay = true),

    // The Kendal's cave on Rellekka's mountain (Mountain Daughter).
    // TODO Once Mountain Daughter is added, players who haven't learnt about the Kendal can't stand the smell ("What a
    //  terrible stench! No one can live there, so you decide not to go either."), and once the corpse is taken back
    //  they don't want to ("You really don't want to go back in there again, now that you don't need to.").
    KENDAL_CAVE_IN(id = 5857, landing = tile(2807, 10105), message = "You take a very deep breath..."),
    KENDAL_CAVE_OUT(id = 5858, landing = tile(2808, 3703), message = "You welcome the fresh air."),

    // Mort Myre, the druids' grotto (Nature Spirit).
    // TODO Once Nature Spirit is added, Filliman's spirit appears instead until the ritual has been done, and players
    //  who haven't finished the quest go to the ground-floor copy.
    GROTTO_IN(id = 3516, landing = tile(3442, 9734, 1), message = "You prepare to enter the Druid's grotto.",
              arrival = "You see a beautifully tended small grotto area."),
    GROTTO_OUT(id = 3525, landing = tile(3440, 3337), message = "You prepare to exit the Druid's grotto.", delay = 1,
               arrival = "You crawl back out of the grotto."),
    GROTTO_OUT_2(id = 3526, landing = tile(3440, 3337), message = "You prepare to exit the Druid's grotto.", delay = 1,
                 arrival = "You crawl back out of the grotto."),

    // The Hollows under Canifis, between the Myreque's hideout and the cave tunnels (In Search of the Myreque).
    HOLLOWS_TUNNEL_1(id = 5046, landing = tile(3477, 9799), arriveDelay = true, position = Position(3492, 9808)),
    HOLLOWS_TUNNEL_2(id = 5046, landing = tile(3492, 9809), arriveDelay = true, position = Position(3478, 9799)),
    HOLLOWS_TUNNEL_3(id = 5046, landing = tile(3475, 9806), arriveDelay = true, position = Position(3488, 9814)),
    HOLLOWS_TUNNEL_4(id = 5046, landing = tile(3488, 9815), arriveDelay = true, position = Position(3476, 9806)),
    HOLLOWS_TUNNEL_5(id = 5046, landing = tile(3466, 9820), arriveDelay = true, position = Position(3480, 9824)),
    HOLLOWS_TUNNEL_6(id = 5046, landing = tile(3481, 9824), arriveDelay = true, position = Position(3467, 9820)),
    MYREQUE_HIDEOUT_OUT(id = 5046, landing = tile(3491, 9824), arriveDelay = true, position = Position(3505, 9831)),
    MYREQUE_HIDEOUT_COPY_OUT(id = 5046, landing = tile(3491, 9824), arriveDelay = true,
                             position = Position(3505, 9831, 3)),

    // Ivandis's tomb in the Hollows (In Aid of the Myreque).
    // TODO Once In Aid of the Myreque is added, boards cover the entrance until they're smashed with a hammer.
    IVANDIS_TOMB_IN(id = 12770, landing = tile(3461, 9821, 2)),
    IVANDIS_TOMB_OUT(id = 12771, landing = tile(3483, 9832)),

    // The Genie's cave under the Nardah crevice, and the River Elid cave (Spirits of the Elid).
    ELID_CREVICE_UP(id = 10434, landing = tile(3375, 2904), delay = 1, animation = 828),
    ELID_CAVE_OUT(id = 10417, landing = tile(3371, 3129)),

    // Mort'ton, out of the Shade catacombs.
    SHADE_CATACOMBS_OUT(id = 4106, landing = tile(3485, 3322), delay = 1,
                        arrival = "You make your way back out of the Shade tombs.", arriveDelay = true,
                        position = Position(3493, 9726)),

    // Fenkenstrain's experiment cave, the gate in the passage to the mausoleum (Creature of Fenkenstrain).
    // TODO Once Creature of Fenkenstrain is added, the gate needs the cavern key from the chest by the ladder ("The
    //  door is locked.") until the quest is done.
    FENKENSTRAIN_CAVE_GATE(id = 5170, landing = across(3510, 3511)),

    // Jaldraocht Pyramid's doors (Desert Treasure).
    // TODO Once Desert Treasure is added, the doors stay sealed until a diamond is in each of the four obelisks.
    JALDRAOCHT_PYRAMID_DOOR(id = 6545, landing = acrossNorth(2898, 2900)),
    JALDRAOCHT_PYRAMID_DOOR_2(id = 6547, landing = acrossNorth(2898, 2900)),

    // Jaldraocht Pyramid, the tunnel down to the altar.
    // TODO Once Desert Treasure is added, the passage only leads anywhere after the quest ("This passage doesn't seem
    //  to lead anywhere...").
    JALDRAOCHT_PYRAMID_TUNNEL(id = 6481, landing = tile(3233, 9313)),

    // Out of the rift under Mort Myre (A Soul's Bane).
    TOLNAS_RIFT_OUT(id = 13999, landing = tile(3312, 3450), delay = 1, animation = 828),

    // The Haunted Mine's cart tunnels.
    // TODO Once Haunted Mine is added, crawling in moves the quest on, and a glowing fungus carried out crumbles to
    //  dust ("As you crawl out into the sunlight, the strange fungus you are carrying crumbles to dust.").
    HAUNTED_MINE_TUNNEL_1_IN(id = 4913, landing = tile(3436, 9637), delay = 1, animation = 844, arriveDelay = true),
    HAUNTED_MINE_TUNNEL_1_OUT(id = 4920, landing = tile(3441, 3232), delay = 1, animation = 844, arriveDelay = true),
    HAUNTED_MINE_TUNNEL_2_IN(id = 4914, landing = tile(3405, 9631), delay = 1, animation = 844, arriveDelay = true),
    HAUNTED_MINE_TUNNEL_2_OUT(id = 4921, landing = tile(3429, 3233), delay = 1, animation = 844, arriveDelay = true),
    HAUNTED_MINE_TUNNEL_3_IN(id = 4915, landing = tile(3409, 9623), delay = 1, animation = 844, arriveDelay = true),
    HAUNTED_MINE_TUNNEL_3_OUT(id = 4922, landing = tile(3428, 3225), delay = 1, animation = 844, arriveDelay = true),

    // The Ruins of Uzer's portal room (The Golem).
    // TODO Once The Golem is added, the door can't be opened ("You can't find any way to open the door.") until the
    //  golem has been told about the demon.
    UZER_PORTAL_IN(id = 6310, landing = tile(2720, 4884, 2), delay = 1, arrival = "You step into the portal.",
                   arriveDelay = true),
    UZER_PORTAL_OUT(id = 6282, landing = tile(2713, 4913), delay = 1, arrival = "You step into the portal.",
                    arriveDelay = true),

    // The well between Iban's temple and the Tirannwn temple, and the cave into it (Regicide).
    // TODO Once Regicide is added, the wells only work once the quest has been started.
    REGICIDE_WELL_DOWN(id = 4004, landing = tile(2343, 9622), delay = 1, arrival = "You climb into the well.",
                       animation = 828),
    REGICIDE_WELL_UP(id = 4005, landing = tile(2010, 4712, 1), delay = 1, arrival = "You climb into the well.",
                     animation = 828),
    REGICIDE_CAVE_IN(id = 4006, landing = tile(2314, 9624)),
    REGICIDE_CAVE_OUT(id = 4007, landing = tile(2312, 3216)),

    // Baxtorian Falls, out of the Waterfall Dungeon.
    WATERFALL_DUNGEON_OUT(id = 2000, landing = tile(2511, 3463), message = "You exit the dungeon."),

    // The Dig Site, the ropes back up the shafts.
    DIG_SITE_ROPE_WEST(id = 2352, landing = tile(3370, 3427), animation = 832, face = Direction.SOUTH),
    DIG_SITE_ROPE_EAST(id = 2353, landing = tile(3354, 3417), animation = 832, face = Direction.WEST),

    // West Ardougne, the mud pile out of the sewer (Plague City).
    PLAGUE_CITY_MUD_PILE(id = 2533, landing = tile(2566, 3331), delay = 1, arrival = "You climb up the mud pile.",
                         animation = 828),

    // Enakhra's temple: the secret entrances down from the desert, the sand piles back up, and the stone ladders
    // between its floors.
    // TODO Once Enakhra's Lament is added, each entrance stays covered by a boulder until it's been climbed out of.
    ENAKHRAS_TEMPLE_NORTH_IN(id = 11045, landing = tile(3086, 9333, 1), delay = 1, animation = 827),
    ENAKHRAS_TEMPLE_EAST_IN(id = 11046, landing = tile(3124, 9328, 1), delay = 1, animation = 827),
    ENAKHRAS_TEMPLE_SOUTH_IN(id = 11047, landing = tile(3120, 9288, 1), delay = 1, animation = 827),
    ENAKHRAS_TEMPLE_WEST_IN(id = 11048, landing = tile(3080, 9306, 1), delay = 1, animation = 827),
    ENAKHRAS_TEMPLE_NORTH_OUT(id = 10950, landing = near(3148, 2936), delay = 1, animation = 828,
                              position = Position(3087, 9333, 1)),
    ENAKHRAS_TEMPLE_EAST_OUT(id = 10950, landing = near(3194, 2924), delay = 1, animation = 828,
                             position = Position(3124, 9329, 1)),
    ENAKHRAS_TEMPLE_SOUTH_OUT(id = 10950, landing = near(3189, 2887), delay = 1, animation = 828,
                              position = Position(3120, 9289, 1)),
    ENAKHRAS_TEMPLE_WEST_OUT(id = 10950, landing = near(3146, 2907), delay = 1, animation = 828,
                             position = Position(3081, 9306, 1)),
    ENAKHRAS_TEMPLE_NORTH_LADDER_UP(id = 11043, landing = row(9301, 2), delay = 1, animation = 828,
                                    position = Position(3104, 9300, 1)),
    ENAKHRAS_TEMPLE_NORTH_LADDER_UP_2(id = 11043, landing = row(9301, 2), delay = 1, animation = 828,
                                      position = Position(3105, 9300, 1)),
    ENAKHRAS_TEMPLE_NORTH_LADDER_DOWN(id = 11044, landing = row(9299, 1), delay = 1, animation = 827,
                                      position = Position(3104, 9300, 2)),
    ENAKHRAS_TEMPLE_NORTH_LADDER_DOWN_2(id = 11044, landing = row(9299, 1), delay = 1, animation = 827,
                                        position = Position(3105, 9300, 2)),
    ENAKHRAS_TEMPLE_SOUTH_LADDER_UP(id = 11043, landing = row(9284, 2), delay = 1, animation = 828,
                                    position = Position(3104, 9285, 1)),
    ENAKHRAS_TEMPLE_SOUTH_LADDER_UP_2(id = 11043, landing = row(9284, 2), delay = 1, animation = 828,
                                      position = Position(3105, 9285, 1)),
    ENAKHRAS_TEMPLE_SOUTH_LADDER_DOWN(id = 11044, landing = row(9286, 1), delay = 1, animation = 827,
                                      position = Position(3104, 9285, 2)),
    ENAKHRAS_TEMPLE_SOUTH_LADDER_DOWN_2(id = 11044, landing = row(9286, 1), delay = 1, animation = 827,
                                        position = Position(3105, 9285, 2)),

    // Karamja, the rope up out of the Viyeldi caves to the shaman caves (Legends' Quest).
    VIYELDI_CAVES_UP(id = 2958, landing = tile(2760, 9328),
                     message = "You climb back up the rope to the Shaman Caves..", delay = 2, animation = 828),

    // Sophanem, the hole out through the east wall and the rock back in (Icthlarin's Little Helper).
    // TODO Once Icthlarin's Little Helper is added, the rock only opens once the Wanderer has hypnotised the player.
    SOPHANEM_HOLE(id = 6620, landing = tile(3321, 2858)),
    SOPHANEM_ROCK(id = 6621, landing = tile(3319, 2796), delay = 5),

    // The Shadow Dungeon's ladder, which only shows while the ring of visibility is worn (Desert Treasure).
    SHADOW_DUNGEON_DOWN(id = 6560, landing = tile(2630, 5071), delay = 1, animation = 827),

    // The lighthouse, down to its basement and the dagannoth cave below (Horror from the Deep).
    // TODO Once Horror from the Deep is added, the door needs Jossik's key the first time ("You unlock the Lighthouse
    //  front door."), players who haven't finished the quest go into its copy of the lighthouse, and the iron ladder
    //  down only works once the light is fixed.
    LIGHTHOUSE_DOOR(id = 4577, landing = acrossNorth(3635, 3636), position = Position(2509, 3636)),
    LIGHTHOUSE_QUEST_COPY_DOOR(id = 4577, landing = tile(2509, 3635), position = Position(2445, 4596)),
    LIGHTHOUSE_BASEMENT_DOWN(id = 4383, landing = tile(2518, 9994, 1), delay = 1, animation = 827,
                             position = Position(2509, 3644)),
    LIGHTHOUSE_BASEMENT_UP(id = 4412, landing = tile(2510, 3644), delay = 1, animation = 828,
                           position = Position(2519, 9994, 1)),
    LIGHTHOUSE_CAVE_DOWN(id = 4485, landing = tile(2515, 10008), delay = 1, animation = 827,
                         position = Position(2515, 10006, 1)),
    LIGHTHOUSE_CAVE_UP(id = 4413, landing = tile(2515, 10005, 1), delay = 1, animation = 828,
                       position = Position(2515, 10007)),

    // Paterdomus, the trapdoor under the temple down to the library (In Aid of the Myreque).
    // TODO Once In Aid of the Myreque is added, the trapdoor only shows once it's been unlocked with the library key.
    PATERDOMUS_LIBRARY_DOWN(id = 12762, landing = near(3413, 9868, 2), delay = 1, animation = 827),

    // The Temple of Light's tunnel to the dwarves' camp under the Underground Pass, and the stairs back down
    // (Mourning's End Part II).
    TEMPLE_OF_LIGHT_TUNNEL(id = 9977, landing = tile(2311, 9793), delay = 4),
    TEMPLE_OF_LIGHT_STAIRS(id = 9974, landing = tile(1857, 4639), delay = 4),

    // The Mogre camp's underwater cavern (Recipe for Disaster).
    // TODO Once Recipe for Disaster is added, Nung has to have asked for help first ("You have no idea what is in
    //  there, best leave it for the time being."), and swimmers need 5 rocks to sink in ("You won't be able to fight
    //  in there while swimming." "You need to collect 5 rocks to get into the cave.").
    MOGRE_CAVERN_IN(id = 12460, landing = row(9520, 1), message = "You enter the cave..."),
    MOGRE_CAVERN_IN_2(id = 12461, landing = row(9520, 1), message = "You enter the cave..."),
    MOGRE_CAVERN_OUT(id = 12462, landing = row(9515, 1), message = "You exit the cave...",
                     arrival = "You kick off the sea bed and begin swimming again."),
    MOGRE_CAVERN_OUT_2(id = 12463, landing = row(9515, 1), message = "You exit the cave...",
                       arrival = "You kick off the sea bed and begin swimming again."),

    // The caves of Recipe for Disaster's King Awowogei part: back up from the tch'ki nut cave and the snake pit, and in
    // and out of the stuffed-snake cave under the Temple of Marimbo.
    TCHIKI_NUT_CAVE_UP(id = 12616, landing = near(2758, 2730), delay = 1, animation = 828),
    SNAKE_PIT_UP(id = 12601, landing = near(2921, 2723), delay = 1, animation = 828),
    STUFFED_SNAKE_CAVE_IN(id = 12617, landing = tile(3068, 5485)),
    STUFFED_SNAKE_CAVE_OUT(id = 12657, landing = tile(2804, 9199)),

    // Ape Atoll: the bamboo ladder into the dungeon under it, and the ropes back up to the trapdoors (Monkey Madness).
    // TODO Once Monkey Madness is added, Ape Atoll can only be reached during or after the quest.
    APE_ATOLL_DUNGEON_DOWN(id = 4780, landing = shift(0, 6400), delay = 3, arrival = "You climb down the ladder.",
                           animation = 827),
    APE_ATOLL_DUNGEON_UP(id = 4781, landing = shift(0, -6400), delay = 3, arrival = "You climb up the ladder.",
                         animation = 828),
    APE_ATOLL_EAST_WAREHOUSE_UP(id = 4728, landing = near(2764, 2769), delay = 3, arrival = "You climb up the rope.",
                                animation = 828),
    APE_ATOLL_WEST_WAREHOUSE_UP(id = 4889, landing = near(2749, 2768), delay = 3, arrival = "You climb up the rope.",
                                animation = 828),
    APE_ATOLL_TEMPLE_UP(id = 4881, landing = near(2807, 2785), message = "You climb up the rope.", delay = 3,
                        animation = 828),

    // The wall cracks between the jungle demon's arena, its corners and Bonzara's room (Monkey Madness).
    MONKEY_MADNESS_CRACK_BONZARA(id = 5793, landing = tile(2715, 9184), message = "You enter the crack in the wall.",
                                 delay = 4),
    MONKEY_MADNESS_CRACK_NW(id = 5794, landing = tile(2696, 9212, 1), message = "You enter the crack in the wall.",
                            delay = 4),
    MONKEY_MADNESS_CRACK_NE(id = 5795, landing = tile(2741, 9205, 1), message = "You enter the crack in the wall.",
                            delay = 4),
    MONKEY_MADNESS_CRACK_SW(id = 5796, landing = tile(2690, 9163, 1), message = "You enter the crack in the wall.",
                            delay = 4),
    MONKEY_MADNESS_CRACK_SE(id = 5797, landing = tile(2736, 9159, 1), message = "You enter the crack in the wall.",
                            delay = 4),
    MONKEY_MADNESS_CRACK_CENTRE(id = 5798, landing = tile(2415, 9908), message = "You enter the crack in the wall.",
                                delay = 4),

    // The Watchtower's skavid caves: the ways out, and the tunnel down into Yanille.
    SKAVID_CAVE_1_OUT(id = 2817, landing = tile(2562, 3024)),
    SKAVID_CAVE_2_OUT(id = 2818, landing = tile(2524, 3070)),
    SKAVID_CAVE_3_OUT(id = 2819, landing = tile(2540, 3054)),
    SKAVID_CAVE_4_OUT(id = 2820, landing = tile(2553, 3054)),
    SKAVID_CAVE_5_OUT(id = 2821, landing = tile(2552, 3034)),
    SKAVID_CAVE_6_OUT(id = 2822, landing = tile(2529, 3013)),
    SKAVID_TUNNEL_TO_YANILLE(id = 2823, landing = tile(2585, 3108), message = "You enter the tunnel.",
                             arrival = "So that's how the skavids are getting into Yanille!"),
    OGRE_ENCLAVE_OUT(id = 2813, landing = tile(2540, 3054));

    companion object {

        /**
         * The entrances by object id.
         */
        val BY_ID = values().groupBy { it.id }

        /**
         * Returns the entrance for the object [id] on [position], or `null` if there isn't one.
         */
        fun forObject(id: Int, position: Position) =
            BY_ID[id]?.let { list ->
                list.firstOrNull { it.position == position } ?: list.firstOrNull { it.position == null }
            }
    }
}
