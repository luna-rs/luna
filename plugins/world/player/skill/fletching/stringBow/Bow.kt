package world.player.skill.fletching.stringBow

import io.luna.game.model.item.Item

/**
 * An enum representing an item cut from a [Log].
 */
enum class Bow(val level: Int,
               val exp: Double,
               val unstrung: Int,
               val strung: Int) {
    ARROW_SHAFT(level = 1,
                exp = 5.0,
                unstrung = 52,
                strung = -1),
    SHORTBOW(level = 5,
             exp = 5.0,
             unstrung = 50,
             strung = 841),
    LONGBOW(level = 10,
            exp = 10.0,
            unstrung = 48,
            strung = 839),
    OAK_SHORTBOW(level = 20,
                 exp = 16.5,
                 unstrung = 54,
                 strung = 843),
    OAK_LONGBOW(level = 25,
                exp = 25.0,
                unstrung = 56,
                strung = 845),
    WILLOW_SHORTBOW(level = 35,
                    exp = 33.3,
                    unstrung = 60,
                    strung = 847),
    WILLOW_LONGBOW(level = 40,
                   exp = 41.5,
                   unstrung = 58,
                   strung = 849),
    MAPLE_SHORTBOW(level = 50,
                   exp = 50.0,
                   unstrung = 64,
                   strung = 853),
    MAPLE_LONGBOW(level = 55,
                  exp = 58.3,
                  unstrung = 62,
                  strung = 851),
    YEW_SHORTBOW(level = 65,
                 exp = 67.5,
                 unstrung = 68,
                 strung = 857),
    YEW_LONGBOW(level = 70,
                exp = 75.0,
                unstrung = 66,
                strung = 855),
    MAGIC_SHORTBOW(level = 80,
                   exp = 83.3,
                   unstrung = 70,
                   strung = 861),
    MAGIC_LONGBOW(level = 85,
                  exp = 91.5,
                  unstrung = 68,
                  strung = 859);

    companion object {

        /**
         * Mappings of [Bow.unstrung] to [Bow].
         */
        val UNSTRUNG_TO_BOW = values().associateBy { it.unstrung }

        /**
         * The bow string identifier.
         */
        const val BOW_STRING = 1777
    }

    /**
     * The strung item.
     */
    val strungItem = if (strung == -1) null else Item(strung)

    /**
     * The unstrung item.
     */
    val unstrungItem = Item(unstrung)
}