package world.player.skill.crafting.battlestaffCrafting

import io.luna.game.model.item.Item

/**
 * An enum representing battlestaves that can be made.
 */
enum class Battlestaff(val staff: Int,
                       val level: Int,
                       val orb: Int,
                       val exp: Double) {

    WATER(staff = 1395,
          level = 54,
          orb = 571,
          exp = 100.0),
    EARTH(staff = 1399,
          level = 58,
          orb = 575,
          exp = 112.5),
    FIRE(staff = 1393,
         level = 62,
         orb = 569,
         exp = 125.0),
    AIR(staff = 1397,
        level = 66,
        orb = 573,
        exp = 137.5);

    companion object {

        /**
         * The battlestaff identifier.
         */
        const val BATTLESTAFF = 1391

        /**
         * The battlestaff item.
         */
        val BATTLESTAFF_ITEM = Item(BATTLESTAFF)

        /**
         * Mappings of [Battlestaff.orb] to [Battlestaff].
         */
        val ORB_TO_BATTLESTAFF = values().associateBy { it.orb }
    }

    /**
     * The staff item.
     */
    val staffItem = Item(staff)

    /**
     * The orb item.
     */
    val orbItem = Item(orb)
}