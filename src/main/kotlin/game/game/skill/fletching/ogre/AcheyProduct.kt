package game.skill.fletching.ogre

import com.google.common.collect.ImmutableList

/**
 * An enum representing an item cut from achey tree logs with a knife, in the order of the make menu.
 *
 * @author lare96
 */
enum class AcheyProduct(val id: Int, val level: Int, val exp: Double, val label: String) {
    OGRE_ARROW_SHAFTS(id = OgreFletching.OGRE_ARROW_SHAFT,
                      level = 5,
                      exp = 1.6,
                      label = "Ogre Arrow Shafts"),
    COMP_OGRE_BOW(id = OgreFletching.UNSTRUNG_COMP_BOW,
                  level = 30,
                  exp = 45.0,
                  label = "Composite Ogre Bow");

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES: ImmutableList<AcheyProduct> = ImmutableList.copyOf(entries)

        /**
         * The item identifiers in menu order.
         */
        val IDS = VALUES.map { it.id }.toIntArray()
    }
}
