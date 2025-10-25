package game.skill.magic.enchantJewellery

import com.google.common.collect.ImmutableList
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import game.player.Sounds
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement

/**
 * Represents all different levels of jewellery enchanting spells.
 *
 * @author lare96
 */
enum class EnchantJewelleryType(val spellId: Int,
                                val level: Int,
                                val xp: Double,
                                val sound: Sounds,
                                val requirements: List<SpellRequirement>,
                                val enchantMap: Map<Int, EnchantItem>) {

    LVL_1(spellId = 1155,
          level = 7,
          xp = 17.5,
          requirements = listOf(
              RuneRequirement(Rune.WATER, 1),
              RuneRequirement(Rune.COSMIC, 1)
          ),
          sound = Sounds.ENCHANT_LVL_1,
          enchantMap = mapOf(
              1637 to EnchantItem(2550, Animation(718), Graphic(238)), // Sapphire ring
              1656 to EnchantItem(3858, Animation(719), Graphic(114, 100)), // Sapphire necklace
              1694 to EnchantItem(1727, Animation(719), Graphic(114, 100)) // Sapphire amulet
          )),
    LVL_2(spellId = 1165,
          level = 27,
          xp = 37.0,
          sound = Sounds.ENCHANT_LVL_2,
          requirements = listOf(
              RuneRequirement(Rune.AIR, 3),
              RuneRequirement(Rune.COSMIC, 1)
          ),
          enchantMap = mapOf(
              1639 to EnchantItem(2552, Animation(718), Graphic(238)),  // Emerald ring
              1658 to EnchantItem(3853, Animation(720), Graphic(115, 100)),  // Emerald necklace
              1696 to EnchantItem(1729, Animation(720), Graphic(115, 100)), // Emerald amulet
              6041 to EnchantItem(6040, Animation(720), Graphic(115, 100)), // Pre-nature amulet
          )),
    LVL_3(spellId = 1176,
          level = 49,
          xp = 59.0,
          sound = Sounds.ENCHANT_LVL_3,
          requirements = listOf(
              RuneRequirement(Rune.FIRE, 5),
              RuneRequirement(Rune.COSMIC, 1)
          ),
          enchantMap = mapOf(
              1641 to EnchantItem(2568, Animation(718), Graphic(238)), // Ruby ring
              1698 to EnchantItem(1725, Animation(721), Graphic(116, 100)), // Ruby amulet
          )),
    LVL_4(spellId = 1180,
          level = 57,
          xp = 67.0,
          sound = Sounds.ENCHANT_LVL_4,
          requirements = listOf(
              RuneRequirement(Rune.EARTH, 10),
              RuneRequirement(Rune.COSMIC, 1)
          ),
          enchantMap = mapOf(
              1643 to EnchantItem(2570, Animation(718), Graphic(238)), // Diamond ring
              1700 to EnchantItem(1731, Animation(719), Graphic(153, 100)) // Diamond amulet
          )),
    LVL_5(spellId = 1187,
          level = 68,
          xp = 78.0,
          sound = Sounds.ENCHANT_LVL_5,
          requirements = listOf(
              RuneRequirement(Rune.EARTH, 15),
              RuneRequirement(Rune.WATER, 15),
              RuneRequirement(Rune.COSMIC, 1)
          ),
          enchantMap = mapOf(
              1645 to EnchantItem(2572, Animation(718), Graphic(238)), // Dragonstone ring
              1702 to EnchantItem(1712, Animation(720), Graphic(154, 100)) // Dragonstone amulet
          )),
    LVL_6(spellId = 6003,
          level = 87,
          xp = 97.0,
          sound = Sounds.ENCHANT_LVL_6,
          requirements = listOf(
              RuneRequirement(Rune.FIRE, 20),
              RuneRequirement(Rune.EARTH, 20),
              RuneRequirement(Rune.COSMIC, 1)
          ),
          enchantMap = mapOf(
              6575 to EnchantItem(6583, Animation(718), Graphic(238)), // Onyx ring
              6581 to EnchantItem(6585, Animation(721), Graphic(452, 100)) // Onyx amulet
          ));

    companion object {

        /**
         * An immutable copy of [values].
         */
        val ALL = ImmutableList.copyOf(values())
    }
}