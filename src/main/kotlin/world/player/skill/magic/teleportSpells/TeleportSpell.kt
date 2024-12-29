package world.player.skill.magic.teleportSpells

import com.google.common.collect.ImmutableList
import io.luna.game.model.Position
import world.player.skill.magic.ItemRequirement
import world.player.skill.magic.Rune
import world.player.skill.magic.RuneRequirement
import world.player.skill.magic.SpellRequirement

/**
 * An enum representing every single teleport spell that can be cast.
 */
enum class TeleportSpell(val level: Int,
                         val xp: Double,
                         val button: Int,
                         val displayName: String,
                         val destination: Position,
                         val style: TeleportStyle,
                         val requirements: List<SpellRequirement>) {
    VARROCK(level = 25,
            xp = 35.0,
            button = 1164,
            displayName = "Varrock",
            destination = Position(3212, 3423),
            style = TeleportStyle.REGULAR,
            requirements = listOf(
                RuneRequirement(Rune.AIR, 3),
                RuneRequirement(Rune.FIRE, 1),
                RuneRequirement(Rune.LAW, 1)
            )),
    LUMBRIDGE(level = 31,
              xp = 41.0,
              button = 1167,
              displayName = "Lumbridge",
              destination = Position(3222, 3219),
              style = TeleportStyle.REGULAR,
              requirements = listOf(
                  RuneRequirement(Rune.AIR, 3),
                  RuneRequirement(Rune.EARTH, 1),
                  RuneRequirement(Rune.LAW, 1)
              )),
    FALADOR(level = 37,
            xp = 48.0,
            button = 1170,
            displayName = "Falador",
            destination = Position(2964, 3377),
            style = TeleportStyle.REGULAR,
            requirements = listOf(
                RuneRequirement(Rune.AIR, 3),
                RuneRequirement(Rune.WATER, 1),
                RuneRequirement(Rune.LAW, 1)
            )),
    CAMELOT(level = 45,
            xp = 55.5,
            button = 1174,
            displayName = "Camelot",
            destination = Position(2757, 3479),
            style = TeleportStyle.REGULAR,
            requirements = listOf(
                RuneRequirement(Rune.AIR, 5),
                RuneRequirement(Rune.LAW, 1)
            )),
    ARDOUGNE(level = 51,
             xp = 61.0,
             button = 1540,
             displayName = "Ardougne",
             destination = Position(2661, 3307),
             style = TeleportStyle.REGULAR,
             requirements = listOf(
                 RuneRequirement(Rune.WATER, 2),
                 RuneRequirement(Rune.LAW, 2)
             )),
    PADDEWWA(level = 54,
             xp = 64.0,
             button = 13035,
             displayName = "Paddewwa",
             destination = Position(3097, 9881),
             style = TeleportStyle.ANCIENT,
             requirements = listOf(
                 RuneRequirement(Rune.AIR, 1),
                 RuneRequirement(Rune.FIRE, 1),
                 RuneRequirement(Rune.LAW, 2)
             )),
    WATCHTOWER(level = 58,
               xp = 68.0,
               button = 1541,
               displayName = "the Watchtower",
               destination = Position(2546, 3115),
               style = TeleportStyle.REGULAR,
               requirements = listOf(
                   RuneRequirement(Rune.EARTH, 2),
                   RuneRequirement(Rune.LAW, 2)
               )),
    SENNTISTEN(level = 60,
               xp = 70.0,
               button = 13045,
               displayName = "Senntisten",
               destination = Position(3320, 3337),
               style = TeleportStyle.ANCIENT,
               requirements = listOf(
                   RuneRequirement(Rune.SOUL, 1),
                   RuneRequirement(Rune.LAW, 2)
               )),
    TROLLHEIM(level = 61,
              xp = 68.0,
              button = 7455,
              displayName = "Trollheim",
              destination = Position(2889, 3677),
              style = TeleportStyle.REGULAR,
              requirements = listOf(
                  RuneRequirement(Rune.FIRE, 2),
                  RuneRequirement(Rune.LAW, 2)
              )),
    APE_ATOLL(level = 64,
              xp = 74.0,
              button = 18470,
              displayName = "Ape Atoll",
              destination = Position(2797, 2798),
              style = TeleportStyle.REGULAR,
              requirements = listOf(
                  RuneRequirement(Rune.WATER, 2),
                  RuneRequirement(Rune.FIRE, 2),
                  RuneRequirement(Rune.LAW, 2),
                  ItemRequirement(1963),
              )),
    KHARYRLL(level = 66,
             xp = 76.0,
             button = 13053,
             displayName = "Kharyll",
             destination = Position(3494, 3473),
             style = TeleportStyle.ANCIENT,
             requirements = listOf(
                 RuneRequirement(Rune.BLOOD, 1),
                 RuneRequirement(Rune.LAW, 2)
             )),
    LASSAR(level = 72,
           xp = 82.0,
           button = 13061,
           displayName = "Lassar",
           destination = Position(3002, 3473),
           style = TeleportStyle.ANCIENT,
           requirements = listOf(
               RuneRequirement(Rune.WATER, 4),
               RuneRequirement(Rune.LAW, 2)
           )),
    DAREEYAK(level = 78,
             xp = 88.0,
             button = 13069,
             displayName = "Dareeyak",
             destination = Position(2968, 3696),
             style = TeleportStyle.ANCIENT,
             requirements = listOf(
                 RuneRequirement(Rune.AIR, 2),
                 RuneRequirement(Rune.FIRE, 3),
                 RuneRequirement(Rune.LAW, 2)
             )),
    CARRALLANGER(level = 84,
                 xp = 94.0,
                 button = 13079,
                 displayName = "Carrallanger",
                 destination = Position(3158, 3666),
                 style = TeleportStyle.ANCIENT,
                 requirements = listOf(
                     RuneRequirement(Rune.SOUL, 2),
                     RuneRequirement(Rune.LAW, 2)
                 )),
    ANNAKARL(level = 90,
             xp = 100.0,
             button = 13087,
             displayName = "Annakarl",
             destination = Position(3288, 3888),
             style = TeleportStyle.ANCIENT,
             requirements = listOf(
                 RuneRequirement(Rune.BLOOD, 2),
                 RuneRequirement(Rune.LAW, 2)
             )),
    GHORROCK(level = 96,
             xp = 106.0,
             button = 13095,
             displayName = "Ghorrock",
             destination = Position(2974, 3875),
             style = TeleportStyle.ANCIENT,
             requirements = listOf(
                 RuneRequirement(Rune.WATER, 8),
                 RuneRequirement(Rune.LAW, 2)
             ));

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableList.copyOf(values())
    }
}