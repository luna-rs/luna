package game.skill.magic.teleOther

import io.luna.game.model.Position
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement
import game.skill.magic.teleportSpells.TeleportSpell

/**
 * Represents all the different teleother spell types.
 *
 * @author lare96
 */
enum class TeleOtherType(val location: String,
                         val level: Int,
                         val xp: Double,
                         val destination: Position,
                         val requirements: List<SpellRequirement>) {
    LUMBRIDGE(location = "Lumbridge",
              level = 74,
              xp = 84.0,
              destination = TeleportSpell.LUMBRIDGE.destination,
              requirements = listOf(
                  RuneRequirement(Rune.EARTH, 1),
                  RuneRequirement(Rune.LAW, 1),
                  RuneRequirement(Rune.SOUL, 1)
              )),
    FALADOR(location = "Falador",
            level = 82,
            xp = 92.0,
            destination = TeleportSpell.FALADOR.destination,
            requirements = listOf(
                RuneRequirement(Rune.WATER, 1),
                RuneRequirement(Rune.LAW, 1),
                RuneRequirement(Rune.SOUL, 1)
            )),
    CAMELOT(location = "Camelot",
            level = 90,
            xp = 100.0,
            destination = TeleportSpell.CAMELOT.destination,
            requirements = listOf(
                RuneRequirement(Rune.LAW, 1),
                RuneRequirement(Rune.SOUL, 1)
            ))
}