package world.player.skill.crafting.gemCutting

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation

/**
 * An enum representing uncut precious gems.
 */
enum class Gem(val uncut: Int,
               val cut: Int,
               val animation: Animation,
               val level: Int,
               val exp: Double) {

    OPAL(uncut = 1625,
         cut = 1609,
         animation = Animation(890),
         level = 1,
         exp = 15.0),
    JADE(uncut = 1627,
         cut = 1611,
         animation = Animation(891),
         level = 13,
         exp = 20.0),
    RED_TOPAZ(uncut = 1629,
              cut = 1613,
              animation = Animation(892),
              level = 16,
              exp = 25.0),
    SAPPHIRE(uncut = 1623,
             cut = 1607,
             animation = Animation(888),
             level = 20,
             exp = 50.0),
    EMERALD(uncut = 1621,
            cut = 1605,
            animation = Animation(889),
            level = 27,
            exp = 67.5),
    RUBY(uncut = 1619,
         cut = 1603,
         animation = Animation(887),
         level = 34,
         exp = 85.0),
    DIAMOND(uncut = 1617,
            cut = 1601,
            animation = Animation(886),
            level = 43,
            exp = 107.5),
    DRAGONSTONE(uncut = 1631,
                cut = 1615,
                animation = Animation(885),
                level = 55,
                exp = 137.5),
    ONYX(uncut = 6571,
         cut = 6573,
         animation = Animation(2717),
         level = 67,
         exp = 167.5);

    companion object {

        /**
         * The chisel id.
         */
        const val CHISEL = 1755

        /**
         * Mappings of [Gem.uncut] to [Gem].
         */
        val UNCUT_TO_GEM = values().associateBy { it.uncut }
    }

    /**
     * The uncut item.
     */
    val uncutItem = Item(uncut)

    /**
     * The cut item.
     */
    val cutItem = Item(cut)
}