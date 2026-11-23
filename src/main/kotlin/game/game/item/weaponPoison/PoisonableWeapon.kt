package game.item.weaponPoison

/**
 * A piercing weapon that weapon poison can be used on, with its poisoned forms.
 *
 * @property base The unpoisoned weapon.
 * @property poisoned The weapon coated in weapon poison.
 * @property extraStrong The weapon coated in extra strength weapon poison.
 * @property superStrong The weapon coated in super strength weapon poison.
 * @property type The kind of weapon, which sets how many are poisoned at once.
 * @property karambwan The spear coated in karambwan paste, or -1.
 * @author TheLining
 */
enum class PoisonableWeapon(val base: Int,
                            val poisoned: Int,
                            val extraStrong: Int,
                            val superStrong: Int,
                            val type: WeaponType,
                            val karambwan: Int = -1) {
    BRONZE_DART(806, 812, 5628, 5635, WeaponType.DARTS),
    IRON_DART(807, 813, 5629, 5636, WeaponType.DARTS),
    STEEL_DART(808, 814, 5630, 5637, WeaponType.DARTS),
    MITHRIL_DART(809, 815, 5632, 5639, WeaponType.DARTS),
    ADAMANT_DART(810, 816, 5633, 5640, WeaponType.DARTS),
    RUNE_DART(811, 817, 5634, 5641, WeaponType.DARTS),
    BRONZE_JAVELIN(825, 831, 5642, 5648, WeaponType.JAVELINS),
    IRON_JAVELIN(826, 832, 5643, 5649, WeaponType.JAVELINS),
    STEEL_JAVELIN(827, 833, 5644, 5650, WeaponType.JAVELINS),
    MITHRIL_JAVELIN(828, 834, 5645, 5651, WeaponType.JAVELINS),
    ADAMANT_JAVELIN(829, 835, 5646, 5652, WeaponType.JAVELINS),
    RUNE_JAVELIN(830, 836, 5647, 5653, WeaponType.JAVELINS),
    IRON_KNIFE(863, 871, 5655, 5662, WeaponType.KNIVES),
    BRONZE_KNIFE(864, 870, 5654, 5661, WeaponType.KNIVES),
    STEEL_KNIFE(865, 872, 5656, 5663, WeaponType.KNIVES),
    MITHRIL_KNIFE(866, 873, 5657, 5664, WeaponType.KNIVES),
    ADAMANT_KNIFE(867, 875, 5659, 5666, WeaponType.KNIVES),
    RUNE_KNIFE(868, 876, 5660, 5667, WeaponType.KNIVES),
    BLACK_KNIFE(869, 874, 5658, 5665, WeaponType.KNIVES),
    BOLTS(877, 878, 6061, 6062, WeaponType.BOLTS),
    BRONZE_ARROW(882, 883, 5616, 5622, WeaponType.ARROWS),
    IRON_ARROW(884, 885, 5617, 5623, WeaponType.ARROWS),
    STEEL_ARROW(886, 887, 5618, 5624, WeaponType.ARROWS),
    MITHRIL_ARROW(888, 889, 5619, 5625, WeaponType.ARROWS),
    ADAMANT_ARROW(890, 891, 5620, 5626, WeaponType.ARROWS),
    RUNE_ARROW(892, 893, 5621, 5627, WeaponType.ARROWS),
    IRON_DAGGER(1203, 1219, 5668, 5686, WeaponType.DAGGER),
    BRONZE_DAGGER(1205, 1221, 5670, 5688, WeaponType.DAGGER),
    STEEL_DAGGER(1207, 1223, 5672, 5690, WeaponType.DAGGER),
    MITHRIL_DAGGER(1209, 1225, 5674, 5692, WeaponType.DAGGER),
    ADAMANT_DAGGER(1211, 1227, 5676, 5694, WeaponType.DAGGER),
    RUNE_DAGGER(1213, 1229, 5678, 5696, WeaponType.DAGGER),
    DRAGON_DAGGER(1215, 1231, 5680, 5698, WeaponType.DAGGER),
    BLACK_DAGGER(1217, 1233, 5682, 5700, WeaponType.DAGGER),
    BRONZE_SPEAR(1237, 1251, 5704, 5718, WeaponType.SPEAR, karambwan = 3170),
    IRON_SPEAR(1239, 1253, 5706, 5720, WeaponType.SPEAR, karambwan = 3171),
    STEEL_SPEAR(1241, 1255, 5708, 5722, WeaponType.SPEAR, karambwan = 3172),
    MITHRIL_SPEAR(1243, 1257, 5710, 5724, WeaponType.SPEAR, karambwan = 3173),
    ADAMANT_SPEAR(1245, 1259, 5712, 5726, WeaponType.SPEAR, karambwan = 3174),
    RUNE_SPEAR(1247, 1261, 5714, 5728, WeaponType.SPEAR, karambwan = 3175),
    DRAGON_SPEAR(1249, 1263, 5716, 5730, WeaponType.SPEAR, karambwan = 3176),
    BLACK_DART(3093, 3094, 5631, 5638, WeaponType.DARTS),
    BLACK_SPEAR(4580, 4582, 5734, 5736, WeaponType.SPEAR),
    WHITE_DAGGER(6591, 6593, 6595, 6597, WeaponType.DAGGER);

    /**
     * Every poisoned form of this weapon.
     */
    val poisonedForms = listOfNotNull(poisoned, extraStrong, superStrong, karambwan.takeIf { it != -1 })

    companion object {

        /**
         * The unpoisoned weapons by item id.
         */
        val BASE_TO_WEAPON = entries.associateBy { it.base }

        /**
         * The poisoned weapons by the item id of any poisoned form.
         */
        val POISONED_TO_WEAPON = entries.flatMap { weapon -> weapon.poisonedForms.map { it to weapon } }.toMap()
    }
}
