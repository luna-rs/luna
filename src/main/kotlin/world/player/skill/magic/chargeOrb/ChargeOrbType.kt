package world.player.skill.magic.chargeOrb

import com.google.common.collect.ImmutableList
import world.player.skill.magic.ItemRequirement
import world.player.skill.magic.Magic
import world.player.skill.magic.Rune
import world.player.skill.magic.RuneRequirement
import world.player.skill.magic.SpellRequirement
import world.player.skill.magic.chargeOrb.ChargeOrbAction.Companion.UNPOWERED_ORB

/**
 * An enum representing all the different charge orb spells.
 */
enum class ChargeOrbType(val spellId: Int,
                         val level: Int,
                         val xp: Double,
                         val objectId: Int,
                         val chargedOrb: Int,
                         val graphic: Int,
                         val sound: Int,
                         val requirements: List<SpellRequirement>) {
    WATER(spellId = 1179,
          level = 56,
          xp = 66.0,
          objectId = 2151,
          chargedOrb = 571,
          graphic = 149,
          sound = 215,
          requirements = listOf(
              ItemRequirement(UNPOWERED_ORB),
              RuneRequirement(Rune.WATER, 30),
              RuneRequirement(Rune.COSMIC, 3)
          )
    ),
    EARTH(spellId = 1182,
          level = 60,
          xp = 70.0,
          objectId = 2150,
          chargedOrb = 575,
          graphic = 151,
          sound = 213,
          requirements = listOf(
              ItemRequirement(UNPOWERED_ORB),
              RuneRequirement(Rune.WATER, 30),
              RuneRequirement(Rune.COSMIC, 3)
          )
    ),
    FIRE(spellId = 1184,
         level = 63,
         xp = 73.0,
         objectId = 2153,
         chargedOrb = 569,
         graphic = 152,
         sound = 216,
         requirements = listOf(
             ItemRequirement(UNPOWERED_ORB),
             RuneRequirement(Rune.WATER, 30),
             RuneRequirement(Rune.COSMIC, 3)
         )
    ),
    AIR(spellId = 1186,
        level = 66,
        xp = 76.0,
        objectId = 2152,
        chargedOrb = 573,
        graphic = 150,
        sound = 214,
        requirements = listOf(
            ItemRequirement(UNPOWERED_ORB),
            RuneRequirement(Rune.WATER, 30),
            RuneRequirement(Rune.COSMIC, 3)
        )
    );

    companion object {
        val VALUES = ImmutableList.copyOf(values())
    }
}