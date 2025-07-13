package world.player.settings.emote

import io.luna.game.model.mob.varp.Varbit

/**
 * An enum representing player animations triggered from the Player controls menu.
 */
enum class Emote(val id: Int, val button: Int) {
    YES(
        id = 855,
        button = 168
    ),
    NO(
        id = 856,
        button = 169
    ),
    THINK(
        id = 857,
        button = 162
    ),
    BOW(
        id = 858,
        button = 164
    ),
    ANGRY(
        id = 859,
        button = 165
    ),
    CRY(
        id = 860,
        button = 161
    ),
    LAUGH(
        id = 861,
        button = 170
    ),
    CHEER(
        id = 862,
        button = 171
    ),
    WAVE(
        id = 863,
        button = 163
    ),
    BECKON(
        id = 864,
        button = 167
    ),
    CLAP(
        id = 865,
        button = 172
    ),
    DANCE(
        id = 866,
        button = 166
    ),
    PANIC(
        id = 2105,
        button = 13362
    ),
    JIG(
        id = 2106,
        button = 13363
    ),
    SPIN(
        id = 2107,
        button = 13364
    ),
    HEAD_BANG(
        id = 2108,
        button = 13365
    ),
    JOY_JUMP(
        id = 2109,
        button = 13366
    ),
    RASPBERRY(
        id = 2110,
        button = 13367
    ),
    YAWN(
        id = 2111,
        button = 13368
    ),
    SALUTE(
        id = 2112,
        button = 13369
    ),
    SHRUG(
        id = 2113,
        button = 13370
    ),
    BLOW_KISS(
        id = 1702,
        button = 11100
    ),
    GLASS_BOX(
        id = 1131,
        button = 667
    ),
    CLIMB_ROPE(
        id = 1130,
        button = 6503
    ),
    LEAN(
        id = 1129,
        button = 6506
    ),
    GLASS_WALL(
        id = 1128,
        button = 666
    ),
    GOBLIN_BOW(
        id = 2127,
        button = 13383
    ),
    GOBLIN_DANCE(
        id = 2128,
        button = 13384
    ),
    SCARED(
        id = 2836,
        button = 15166
    ),
    ZOMBIE_WALK(
        id = 3544,
        button = 18464
    ),
    ZOMBIE_DANCE(
        id = 3543,
        button = 18465
    ),
    RABBIT_HOP(
        id = 3866,
        button = 18686
    );


    companion object {

        /**
         * Mapping of buttons to [Emote] instances.
         */
        val BUTTON_TO_EMOTE = values().associateBy { it.button }

        /**
         * Set of varbits that unlock emotes in the settings tab.
         */
        val UNLOCK_EMOTE_VARBITS = setOf(1367, 1368, 1369, 1370, 1371, 1920, 1921, 2055).map { Varbit(it, 1) }

        /**
         * The varbit that unlocks the goblin emotes.
         */
        val UNLOCK_GOBLIN_EMOTE_VARBIT = Varbit(532, 1)
    }
}
