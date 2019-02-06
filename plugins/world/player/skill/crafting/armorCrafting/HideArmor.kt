package world.player.skill.crafting.armorCrafting

import api.predef.*
import com.google.common.collect.ArrayListMultimap
import io.luna.game.model.item.Item
import world.player.skill.crafting.hideTanning.Hide

/**
 * An enum representing armor that can be crafted from [Hide]s.
 */
enum class HideArmor(val id: Int, val level: Int, val exp: Double, val hides: Pair<Hide, Int>? = null) {
    LEATHER_GLOVES(id = 1059,
                   level = 1,
                   exp = 13.8,
                   hides = Pair(Hide.SOFT_LEATHER, 1)),
    LEATHER_BOOTS(id = 1061,
                  level = 7,
                  exp = 16.25,
                  hides = Pair(Hide.SOFT_LEATHER, 1)),
    LEATHER_COWL(id = 1167,
                 level = 9,
                 exp = 25.0,
                 hides = Pair(Hide.SOFT_LEATHER, 1)),
    LEATHER_VAMBRACES(id = 1063,
                      level = 11,
                      exp = 22.0,
                      hides = Pair(Hide.SOFT_LEATHER, 1)),
    LEATHER_BODY(id = 1129,
                 level = 14,
                 exp = 25.0,
                 hides = Pair(Hide.SOFT_LEATHER, 1)),
    LEATHER_CHAPS(id = 1095,
                  level = 18,
                  exp = 27.0,
                  hides = Pair(Hide.SOFT_LEATHER, 1)),
    HARD_LEATHER_BODY(id = 1131,
                      level = 28,
                      exp = 35.0,
                      hides = Pair(Hide.HARD_LEATHER, 1)),
    COIF(id = 1169,
         level = 38,
         exp = 37.0,
         hides = Pair(Hide.SOFT_LEATHER, 1)),
    STUDDED_BODY(id = 1133,
                 level = 41,
                 exp = 40.0),
    STUDDED_CHAPS(id = 1097,
                  level = 44,
                  exp = 42.0),
    SNAKESKIN_BOOTS(id = 6328,
                    level = 45,
                    exp = 30.0,
                    hides = Pair(Hide.SNAKESKIN, 6)),
    SNAKESKIN_BRACE(id = 6330,
                    level = 47,
                    exp = 35.0,
                    hides = Pair(Hide.SNAKESKIN, 8)),
    SNAKESKIN_BANDANA(id = 6326,
                      level = 48,
                      exp = 45.0,
                      hides = Pair(Hide.SNAKESKIN, 5)),
    SNAKESKIN_CHAPS(id = 6324,
                    level = 51,
                    exp = 50.0,
                    hides = Pair(Hide.SNAKESKIN, 12)),
    SNAKESKIN_BODY(id = 6322,
                   level = 53,
                   exp = 55.0,
                   hides = Pair(Hide.SNAKESKIN, 15)),
    GREEN_DHIDE_VAMBRACES(id = 1065,
                          level = 57,
                          exp = 62.0,
                          hides = Pair(Hide.GREEN_D_LEATHER, 1)),
    GREEN_DHIDE_CHAPS(id = 1099,
                      level = 60,
                      exp = 124.0,
                      hides = Pair(Hide.GREEN_D_LEATHER, 2)),
    GREEN_DHIDE_BODY(id = 1135,
                     level = 63,
                     exp = 186.0,
                     hides = Pair(Hide.GREEN_D_LEATHER, 3)),
    BLUE_DHIDE_VAMBRACES(id = 2487,
                         level = 66,
                         exp = 70.0,
                         hides = Pair(Hide.BLUE_D_LEATHER, 1)),
    BLUE_DHIDE_CHAPS(id = 2493,
                     level = 68,
                     exp = 140.0,
                     hides = Pair(Hide.BLUE_D_LEATHER, 2)),
    BLUE_DHIDE_BODY(id = 2499,
                    level = 71,
                    exp = 210.0,
                    hides = Pair(Hide.BLUE_D_LEATHER, 3)),
    RED_DHIDE_VAMBRACES(id = 2489,
                        level = 73,
                        exp = 78.0,
                        hides = Pair(Hide.RED_D_LEATHER, 1)),
    RED_DHIDE_CHAPS(id = 2495,
                    level = 75,
                    exp = 156.0,
                    hides = Pair(Hide.RED_D_LEATHER, 2)),
    RED_DHIDE_BODY(id = 2501,
                   level = 77,
                   exp = 234.0,
                   hides = Pair(Hide.RED_D_LEATHER, 3)),
    BLACK_DHIDE_VAMBRACES(id = 2491,
                          level = 79,
                          exp = 86.0,
                          hides = Pair(Hide.BLACK_D_LEATHER, 1)),
    BLACK_DHIDE_CHAPS(id = 2497,
                      level = 82,
                      exp = 172.0,
                      hides = Pair(Hide.BLACK_D_LEATHER, 2)),
    BLACK_DHIDE_BODY(id = 2503,
                     level = 84,
                     exp = 258.0,
                     hides = Pair(Hide.BLACK_D_LEATHER, 3));

    companion object {

        /**
         * Mappings of [HideArmor.id] to [HideArmor].
         */
        val ID_TO_ARMOR = values().associateBy { it.id }

        /**
         * Mappings of [Hide] to [IntArray].
         */
        val HIDE_TO_ARMOR = run {
            val map = ArrayListMultimap.create<Hide, Int>()
            for (armor in values()) {
                map.put(armor.hides?.first ?: Hide.SOFT_LEATHER, armor.id)
            }
            map.asMap().entries.map { it.key to it.value.toIntArray() }.toMap()
        }
    }

    /**
     * The armor item.
     */
    val armorItem = Item(id)

    /**
     * The hides item.
     */
    val hidesItem =
        when (hides) {
            null -> null
            else -> Item(hides.first.tan, hides.second)
        }

    /**
     * The formatted name.
     */
    val formattedName = itemDef(id).name.toLowerCase()
}