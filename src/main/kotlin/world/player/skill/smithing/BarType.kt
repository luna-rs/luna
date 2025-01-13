package world.player.skill.smithing

import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSetMultimap
import io.luna.game.model.item.Item
import world.player.skill.smithing.smeltOre.SmeltAction

/**
 * An enum representing a metal bar that can be made from a [SmeltAction].
 */
enum class BarType(val id: Int, val level: Int, val xp: Double, val widget: Int, val oreRequired: List<Item>) {
    BRONZE(id = 2349,
           level = 1,
           xp = 12.5,
           widget = 2405,
           oreRequired = listOf(
               Item.byName("Tin ore"),
               Item.byName("Copper ore"),
           )),
    IRON(id = 2351,
         level = 15,
         xp = 25.0,
         widget = 2406,
         oreRequired = listOf(
             Item.byName("Iron ore"),
         )),
    STEEL(id = 2353,
          level = 30,
          xp = 37.5,
          widget = 2409,
          oreRequired = listOf(
              Item.byName("Iron ore"),
              Item.byName("Coal", 2),
          )),
    SILVER(id = 2355,
           level = 20,
           xp = 13.7,
           widget = 2407,
           oreRequired = listOf(
               Item.byName("Silver ore"),
           )),
    GOLD(id = 2357,
         level = 40,
         xp = 22.5,
         widget = 2410,
         oreRequired = listOf(
             Item.byName("Gold ore"),
         )),
    MITHRIL(id = 2359,
            level = 50,
            xp = 50.0,
            widget = 2411,
            oreRequired = listOf(
                Item.byName("Mithril ore"),
                Item.byName("Coal", 4),
            )),
    ADAMANT(id = 2361,
            level = 70,
            xp = 62.5,
            widget = 2412,
            oreRequired = listOf(
                Item.byName("Adamantite ore"),
                Item.byName("Coal", 6),
            )),
    RUNE(id = 2363,
         level = 85,
         xp = 75.0,
         widget = 2413,
         oreRequired = listOf(
             Item.byName("Runite ore"),
             Item.byName("Coal", 8),
         ));

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
                for(item in bar.oreRequired) {
                    map.put(item.id, bar)
                }
            }
            return@run map.build()
        }
    }
}