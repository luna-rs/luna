package game.skill.magic.chargeOrb

import com.google.common.collect.ImmutableList
import game.player.Sound
import game.skill.magic.ItemRequirement
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement
import game.skill.magic.chargeOrb.ChargeOrbAction.Companion.UNPOWERED_ORB

/**
 * Existing charge-orb spells and their matching obelisks, outputs, and elemental rune costs.
 *
 * Each normal cast consumes one unpowered orb, thirty runes of its own element, and three cosmic runes.
 * [game.skill.magic.Magic.checkRequirements] resolves equipped staffs and combination runes before
 * [ChargeOrbAction] completes the conversion. Spell IDs, obelisks, levels, experience, and effects use
 * the existing player interaction registrations.
 *
 * @property spellId Client spell widget used on the matching obelisk.
 * @property level Current Magic level needed to cast the spell.
 * @property xp Magic experience awarded for a successful conversion.
 * @property objectId Obelisk accepted by the spell interaction.
 * @property chargedOrb Product replacing the unpowered orb.
 * @property graphic Graphic displayed when the delayed cast completes.
 * @property sound Sound played when the cast starts.
 * @property requirements Item and rune costs resolved by the shared Magic validation.
 *
 * @author lare96
 */
enum class ChargeOrbType(val spellId: Int,
                         val level: Int,
                         val xp: Double,
                         val objectId: Int,
                         val chargedOrb: Int,
                         val graphic: Int,
                         val sound: Sound,
                         val requirements: List<SpellRequirement>) {
    WATER(spellId = 1179,
          level = 56,
          xp = 66.0,
          objectId = 2151,
          chargedOrb = 571,
          graphic = 149,
          sound = Sound.CHARGE_WATER_ORB,
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
          sound = Sound.CHARGE_EARTH_ORB,
          requirements = listOf(
              ItemRequirement(UNPOWERED_ORB),
              RuneRequirement(Rune.EARTH, 30),
              RuneRequirement(Rune.COSMIC, 3)
          )
    ),
    FIRE(spellId = 1184,
         level = 63,
         xp = 73.0,
         objectId = 2153,
         chargedOrb = 569,
         graphic = 152,
         sound = Sound.CHARGE_FIRE_ORB,
         requirements = listOf(
             ItemRequirement(UNPOWERED_ORB),
             RuneRequirement(Rune.FIRE, 30),
             RuneRequirement(Rune.COSMIC, 3)
         )
    ),
    AIR(spellId = 1186,
        level = 66,
        xp = 76.0,
        objectId = 2152,
        chargedOrb = 573,
        graphic = 150,
        sound = Sound.CHARGE_AIR_ORB,
        requirements = listOf(
            ItemRequirement(UNPOWERED_ORB),
            RuneRequirement(Rune.AIR, 30),
            RuneRequirement(Rune.COSMIC, 3)
        )
    );

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableList.copyOf(values())
    }
}
