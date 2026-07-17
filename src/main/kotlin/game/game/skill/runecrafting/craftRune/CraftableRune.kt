package game.skill.runecrafting.craftRune

import game.skill.runecrafting.enterAltar.Altar

/**
 * Represents a rune that can be crafted through Runecrafting.
 *
 * The rune stores its item id, the altar object used for crafting inside the altar area, the altar it belongs to, its
 * multiple-rune threshold, required level, and experience reward.
 *
 * @property id The crafted rune item id.
 * @property insideAltarObject The altar object id clicked inside the altar area to craft this rune.
 * @property altar The Runecrafting altar associated with this rune.
 * @property multiplier The level at which this rune starts receiving multiple rune output.
 * @property level The Runecrafting level required to craft this rune.
 * @property exp The experience gained per essence crafted.
 * @author lare96
 */
enum class CraftableRune(
    val id: Int,
    val insideAltarObject: Int,
    val altar: Altar,
    val multiplier: Int,
    val level: Int,
    val exp: Double
) {

    AIR(
        id = 556,
        insideAltarObject = 2478,
        altar = Altar.AIR,
        multiplier = 11,
        level = 1,
        exp = 5.0
    ),

    MIND(
        id = 558,
        insideAltarObject = 2479,
        altar = Altar.MIND,
        multiplier = 14,
        level = 2,
        exp = 5.5
    ),

    WATER(
        id = 555,
        insideAltarObject = 2480,
        altar = Altar.WATER,
        multiplier = 19,
        level = 5,
        exp = 6.0
    ),

    EARTH(
        id = 557,
        insideAltarObject = 2481,
        altar = Altar.EARTH,
        multiplier = 26,
        level = 9,
        exp = 6.5
    ),

    FIRE(
        id = 554,
        insideAltarObject = 2482,
        altar = Altar.FIRE,
        multiplier = 35,
        level = 14,
        exp = 7.0
    ),

    BODY(
        id = 559,
        insideAltarObject = 2483,
        altar = Altar.BODY,
        multiplier = 46,
        level = 20,
        exp = 7.5
    ),

    COSMIC(
        id = 564,
        insideAltarObject = 2484,
        altar = Altar.COSMIC,
        multiplier = 59,
        level = 27,
        exp = 8.0
    ),

    CHAOS(
        id = 562,
        insideAltarObject = 2487,
        altar = Altar.CHAOS,
        multiplier = 74,
        level = 35,
        exp = 8.5
    ),

    NATURE(
        id = 561,
        insideAltarObject = 2486,
        altar = Altar.NATURE,
        multiplier = 91,
        level = 44,
        exp = 9.0
    ),

    LAW(
        id = 563,
        insideAltarObject = 2485,
        altar = Altar.LAW,
        multiplier = 99,
        level = 54,
        exp = 9.5
    ),

    DEATH(
        id = 560,
        insideAltarObject = 2488,
        altar = Altar.DEATH,
        multiplier = 99,
        level = 65,
        exp = 10.0
    );
    /*
        BLOOD(
            id = 565,
            insideAltarObject = 2490,
            altar = Altar.BLOOD,
            multiplier = 99,
            level = 80,
            exp = 10.5
        ),

        SOUL(
            id = 566,
            insideAltarObject = 2489,
            altar = Altar.SOUL,
            multiplier = 99,
            level = 95,
            exp = 11.0
        );*/

    companion object {

        /**
         * Maps craft-altar object ids to their corresponding [CraftableRune].
         */
        val INSIDE_ALTAR_TO_RUNE = entries.associateBy { it.insideAltarObject }
        val ALTAR_TO_RUNE = entries.associateBy { it.altar }
    }
}