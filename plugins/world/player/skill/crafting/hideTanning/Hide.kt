package world.player.skill.crafting.hideTanning

/**
 * An enum representing all tannable hides.
 */
enum class Hide(val hide: Int, val tan: Int, val cost: Int, val displayName: String) {
    SOFT_LEATHER(hide = 1739,
                 tan = 1741,
                 cost = 2,
                 displayName = "Soft leather"),
    HARD_LEATHER(hide = 1739,
                 tan = 1743,
                 cost = 3,
                 displayName = "Hard leather"),
    SWAMP_SNAKESKIN(hide = 7801,
                    tan = 6289,
                    cost = 20,
                    displayName = "Snakeskin"),
    SNAKESKIN(hide = 6287,
              tan = 6289,
              cost = 15,
              displayName = "Snakeskin"),
    GREEN_D_LEATHER(hide = 1753,
                    tan = 1745,
                    cost = 20,
                    displayName = "Green d'hide"),
    BLUE_D_LEATHER(hide = 1751,
                   tan = 2505,
                   cost = 20,
                   displayName = "Blue d'hide"),
    RED_D_LEATHER(hide = 1749,
                  tan = 2507,
                  cost = 20,
                  displayName = "Red d'hide"),
    BLACK_D_LEATHER(hide = 1747,
                    tan = 2509,
                    cost = 20,
                    displayName = "Black d'hide");

    companion object {

        /**
         * Mappings of [Hide.tan] to [Hide].
         */
        val TAN_TO_HIDE = values().associateBy { it.tan }
    }
}