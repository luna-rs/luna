package world.player.skill.smithing

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSetMultimap
import io.luna.game.model.item.Item
import world.player.skill.smithing.smeltOre.SmeltAction

/**
 * An enum representing a metal bar that can be made from a [SmeltAction].
 *
 * @author lare96
 */
enum class BarType(val id: Int, val level: Int, val xp: Double, val widget: Int, val oreRequired: Pair<Item, Item?>) {
    BRONZE(id = 2349,
           level = 1,
           xp = 12.5,
           widget = 2405,
           oreRequired = Pair(
               item("Copper ore"),
               item("Tin ore"),
           )),
    IRON(id = 2351,
         level = 15,
         xp = 25.0,
         widget = 2406,
         oreRequired = Pair(
             item("Iron ore"),
             null
         )),
    STEEL(id = 2353,
          level = 30,
          xp = 37.5,
          widget = 2409,
          oreRequired = Pair(
              item("Iron ore"),
              item("Coal", 2),
          )),
    SILVER(id = 2355,
           level = 20,
           xp = 13.7,
           widget = 2407,
           oreRequired = Pair(
               item("Silver ore"),
               null
           )),
    GOLD(id = 2357,
         level = 40,
         xp = 22.5,
         widget = 2410,
         oreRequired = Pair(
             item("Gold ore"),
             null
         )),
    MITHRIL(id = 2359,
            level = 50,
            xp = 50.0,
            widget = 2411,
            oreRequired = Pair(
                item("Mithril ore"),
                item("Coal", 4),
            )),
    ADAMANT(id = 2361,
            level = 70,
            xp = 62.5,
            widget = 2412,
            oreRequired = Pair(
                item("Adamantite ore"),
                item("Coal", 6),
            )),
    RUNE(id = 2363,
         level = 85,
         xp = 75.0,
         widget = 2413,
         oreRequired = Pair(
             item("Runite ore"),
             item("Coal", 8),
         ));

    /**
     * A list of all ore required to smelt this bar.
     */
    val oreList = oreRequired.toList().filterNotNull()

    /**
     * The lowercase name.
     */
    val lowercaseName = name.toLowerCase()

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableList.copyOf(values())

        /**
         * An immutable map of all [BarType.id] to [BarType].
         */
        val ID_TO_BAR = values().associateBy { it.id }

        /**
         * An immutable multimap of all ore ids to [BarType] types.
         */
        val ORE_TO_BAR = values().run {
            val map = ImmutableSetMultimap.builder<Int, BarType>()
            for (bar in this) {
                val oreRequired = bar.oreRequired
                map.put(oreRequired.first.id, bar)
                if(oreRequired.second != null) {
                    map.put(oreRequired.second!!.id, bar)
                }
            }
            return@run map.build()
        }
    }
}