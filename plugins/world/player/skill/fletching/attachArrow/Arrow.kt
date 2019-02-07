package world.player.skill.fletching.attachArrow

/**
 * An enum representing an arrow made from two materials.
 */
enum class Arrow(val level: Int,
                 val exp: Double,
                 val tip: Int,
                 val with: Int = 53,
                 val id: Int) {

    HEADLESS_ARROW(level = 1,
                   exp = 1.0,
                   tip = 52,
                   with = 314,
                   id = Arrow.HEADLESS),
    BRONZE_ARROW(level = 1,
                 exp = 1.3,
                 tip = 39,
                 id = 882),
    IRON_ARROW(level = 15,
               exp = 2.5,
               tip = 40,
               id = 884),
    STEEL_ARROW(level = 30,
                exp = 5.0,
                tip = 41,
                id = 886),
    MITHRIL_ARROW(level = 45,
                  exp = 7.5,
                  tip = 42,
                  id = 888),
    ADAMANT_ARROW(level = 60,
                  exp = 10.0,
                  tip = 43,
                  id = 890),
    RUNE_ARROW(level = 75,
               exp = 12.5,
               tip = 44,
               id = 892);

    companion object {

        /**
         * The headless arrow identifier.
         */
        const val HEADLESS = 53

        /**
         * The amount to make in one set.
         */
        const val SET_AMOUNT = 15

        /**
         * Mappings of [Arrow.tip] to [Arrow] instances.
         */
        val TIP_TO_ARROW = values().associateBy { it.tip }
    }
}