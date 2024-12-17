package world.player.skill.thieving.pickpocketNpc

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.item.dropTable.GenericDropTables
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableMap

 /**
 * Represents an NPC that can be stolen from.
 */
enum class ThievableNpc(val level: Int,
                        val xp: Double,
                        val master: Int,
                        val stun: Long,
                        val damage: IntRange,
                        val names: List<String>,
                        val drops: DropTable) {
    MAN_AND_WOMAN(level = 1,
                  xp = 8.0,
                  master = 85,
                  stun = 5,
                  damage = 1..1,
                  names = listOf("Man", "Woman"),
                  drops = DropTableHandler.createSingleton { "Coins" x 3 }),
    FARMER(level = 10,
           xp = 14.5,
           master = 90,
           stun = 5,
           damage = 1..1,
           names = emptyList(),
           drops = DropTableHandler.createSimple {
               "Coins" x 9 chance (15 of 16)
               "Potato seed" x 1 chance (1 of 25)
           }),
    WARRIOR(level = 25,
            xp = 26.0,
            master = 93,
            stun = 5,
            damage = 1..2,
            names = emptyList(),
            drops = DropTableHandler.createSingleton { "Coins" x 18 }),
    ROGUE(level = 32,
          xp = 36.5,
          master = 94,
          stun = 5,
          damage = 2..3,
          names = emptyList(),
          drops = DropTableHandler.createSimple {
              "Iron dagger(p)" x 1 chance (1 of 128)
              "Coins" x 25..40 chance (21 of 25)
              "Air rune" x 8 chance (1 of 16)
              "Jug of wine" x 1 chance (1 of 21)
              "Lockpick" x 1 chance (1 of 26)
          }),
    MASTER_FARMER(
        level = 38,
        xp = 43.0,
        master = 94,
        stun = 5,
        damage = 2..4,
        names = emptyList(),
        drops = GenericDropTables.generalSeedDropTable(false)
    ),
    GUARD(level = 40,
          xp = 46.8,
          master = 95,
          stun = 5,
          damage = 2..2,
          names = emptyList(),
          drops = DropTableHandler.createSingleton { "Coins" x 30 }),
    KNIGHT_OF_ARDOUGNE(level = 55,
                       xp = 84.3,
                       master = 95,
                       stun = 6,
                       damage = 3..5,
                       names = emptyList(),
                       drops = DropTableHandler.createSingleton { "Coins" x 50 }),
    WATCHMAN(level = 65,
             xp = 137.5,
             master = 110,
             stun = 5,
             damage = 4..5,
             names = emptyList(),
             drops = DropTableHandler.createSimple {
                 "Bread" x 1 chance ALWAYS
                 "Coins" x 60 chance ALWAYS
             }),
    PALADIN(level = 70,
            xp = 131.8,
            master = 115,
            stun = 5,
            damage = 3..5,
            names = emptyList(),
            drops = DropTableHandler.createSimple {
                "Chaos rune" x 2 chance ALWAYS
                "Coins" x 80 chance ALWAYS
                // TODO clue scroll hard 1/1000
            }),
    GNOME(level = 75,
          xp = 133.5,
          master = 120,
          stun = 5,
          damage = 1..1,
          names = emptyList(),
          drops = DropTableHandler.createSimple {
              "Coins" x 300 chance COMMON
              "Gold ore" x 1 chance COMMON
              "Arrow shaft" x 2..4 chance COMMON
              "Earth rune" x 1 chance UNCOMMON
              "Fire orb" x 1 chance UNCOMMON
              // TODO medium clue scroll 1/150
          }),
    HERO(level = 80,
         xp = 163.3,
         master = 125,
         stun = 6,
         damage = 3..5,
         names = emptyList(),
         drops = DropTableHandler.createSimple {
             "Coins" x 300..400 chance (41 of 50)
             "Death rune" x 2 chance (1 of 16)
             "Jug of wine" x 1 chance (1 of 21)
             "Blood rune" x 1 chance (1 of 26)
             "Fire orb" x 1 chance (1 of 64)
             "Diamond" x 1 chance (1 of 128)
             "Gold ore" x 1 chance (1 of 128)
         });

    companion object {

        /**
         * Mappings of all names to [ThievableNpc] instances.
         */
        val NAME_TO_NPC: ImmutableMap<String, ThievableNpc> = run {
            val builder = ImmutableMap.builder<String, ThievableNpc>()
            for (next in values()) {
                next.names.forEach { builder.put(it, next) }
            }
            builder.build()
        }
    }

}