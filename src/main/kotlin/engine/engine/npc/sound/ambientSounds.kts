package engine.npc.sound

import engine.npc.sound.NpcAmbientSound.register
import game.player.Sound

// Lost City adult ducks: 50-99 ticks between quacks, audible within five tiles.
register(
    npcs = listOf(44, 45),
    sounds = setOf(Sound.QUACK),
    radius = 5,
    minTicks = 50,
    maxTicks = 99
)

register(
    npc = 46,
    sounds = setOf(Sound.CHICK_CHEEP),
    radius = 3,
    minTicks = 60,
    maxTicks = 110
)

// Coastal birds, including cormorants and albatrosses, share the available gull calls.
register(
    npcs = listOf(
        146, 147, 148, 149, 150, 450, 451, 1179, 1180, 1181,
        1322, 1323, 1324, 1325, 1400, 2693, 2694, 2707, 2708, 2726, 2727, 3197
    ),
    sounds = setOf(Sound.GULL1, Sound.GULL_1, Sound.GULL_2, Sound.GULL_3),
    radius = 6,
    minTicks = 70,
    maxTicks = 140
)

register(
    npcs = listOf(1751, 1752, 1753, 1754, 1755, 1756),
    sounds = setOf(Sound.CROW_1),
    radius = 5,
    minTicks = 80,
    maxTicks = 150
)

register(
    npcs = listOf(1475, 1476, 1521, 1522),
    sounds = setOf(
        Sound.BIRD_TWITTER_1,
        Sound.BIRD_TWITTER_2,
        Sound.BIRD_TWITTER_3,
        Sound.BIRD_TWITTER_4,
        Sound.BIRD_TWITTER_5,
        Sound.EXOTIC_BIRD_1,
        Sound.EXOTIC_BIRD_2
    ),
    radius = 5,
    minTicks = 60,
    maxTicks = 120
)

register(
    npc = 1215,
    sounds = setOf(Sound.PARROT_1, Sound.PARROT_2),
    radius = 5,
    minTicks = 70,
    maxTicks = 130
)

register(
    npcs = listOf(48, 139),
    sounds = setOf(Sound.EXOTIC_BIRD_1, Sound.EXOTIC_BIRD_2),
    radius = 5,
    minTicks = 90,
    maxTicks = 160
)

register(
    npcs = listOf(1015, 3476),
    sounds = setOf(Sound.CHOMPY_BIRD_SQUAK),
    radius = 5,
    minTicks = 90,
    maxTicks = 160
)

// Ordinary chickens only; dead birds and quest boss forms are deliberately excluded.
register(
    npcs = listOf(41, 288, 951, 1017, 1401, 1402, 2313, 2314, 2315),
    sounds = setOf(Sound.CHICKEN_HIT),
    radius = 4,
    minTicks = 70,
    maxTicks = 140
)

register(
    npcs = listOf(42, 43, 1762, 1763, 1764, 1765, 3310, 3311, 3579),
    sounds = setOf(Sound.SHEEP_ATMOSPHERIC1, Sound.SHEEP_ATMOSPHERIC2),
    radius = 5,
    minTicks = 90,
    maxTicks = 180
)

register(
    npcs = listOf(81, 397, 955, 1766, 1767, 1768, 2310, 3309),
    sounds = setOf(Sound.COW_WORK),
    radius = 5,
    minTicks = 100,
    maxTicks = 190
)

register(
    npcs = listOf(1013, 1014),
    sounds = setOf(Sound.TOAD_CROAK),
    radius = 4,
    minTicks = 80,
    maxTicks = 150
)

register(
    npcs = listOf(1828, 1829, 1830, 1997, 2471, 2473, 2689, 3300),
    sounds = setOf(Sound.CROAK_1, Sound.CROAK_2, Sound.CROAK_3),
    radius = 4,
    minTicks = 80,
    maxTicks = 160
)

register(
    npcs = listOf(78, 412, 1005, 2482),
    sounds = setOf(Sound.CAVE_BATS),
    radius = 4,
    minTicks = 100,
    maxTicks = 180
)

// Flying insect sources. Underwater plankton (3353) and TzHaar "swarms" are not flies.
register(
    npcs = listOf(411, 3153),
    sounds = setOf(Sound.INSECT_SWARM),
    radius = 3,
    minTicks = 100,
    maxTicks = 180
)

register(
    npc = 2493,
    sounds = setOf(Sound.MOSQUITOS_1, Sound.MOSQUITOS_2),
    radius = 3,
    minTicks = 90,
    maxTicks = 170
)

register(
    npc = 2494,
    sounds = setOf(Sound.TBCU_MOSQUITO_3),
    radius = 3,
    minTicks = 90,
    maxTicks = 170
)

register(
    npc = 2495,
    sounds = setOf(Sound.TBCU_MOSQUITO_5),
    radius = 3,
    minTicks = 90,
    maxTicks = 170
)

// No owl NPC exists in Luna's 377 NPC dump. Owl/cricket/cicada calls need area emitters;
// butterflies are kept silent instead of using them as unrelated sound sources.