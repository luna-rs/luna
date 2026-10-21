package game.skill.thieving.pickpocketNpc

import api.drops.DropTable
import api.drops.DropTableHandler
import api.drops.GenericDropTables
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableMap
import io.luna.game.model.mob.Npc

/**
 * Represents an [Npc] that can be stolen from.
 *
 * @param pockets The NPC names this type covers, each with the word used in "You pick the ...'s pocket.".
 * @param stunTicks How long a failed attempt stuns the player, counted from the "You've been stunned!" message.
 * @param damage The fixed damage dealt when the player is caught.
 * @param chance The success chance at level 1 and level 99, out of 255.
 * @author lare96
 */
enum class ThievingNpcType(val level: Int,
                           val xp: Double,
                           val stunTicks: Int,
                           val damage: Int,
                           val pockets: Map<String, String>,
                           val drops: DropTable,
                           val chance: Pair<Int, Int>,
                           val shout: String = "What do you think you're doing?") {
    MAN_AND_WOMAN(level = 1,
                  xp = 8.0,
                  stunTicks = 8,
                  damage = 1,
                  pockets = mapOf("Man" to "man", "Woman" to "woman"),
                  drops = DropTableHandler.createSingleton { "Coins" x 3 },
                  chance = 180 to 240),
    FARMER(level = 10,
           xp = 14.5,
           stunTicks = 8,
           damage = 1,
           pockets = mapOf("Farmer" to "farmer"),
           drops = DropTableHandler.createSimple {
               "Coins" x 9 chance (123 of 128)
               "Potato seed" x 1 chance (5 of 128)
           },
           chance = 150 to 240),
    WARRIOR(level = 25,
            xp = 26.0,
            stunTicks = 8,
            damage = 2,
            pockets = mapOf("Warrior woman" to "warrior", "Al-Kharid warrior" to "warrior"),
            drops = DropTableHandler.createSingleton { "Coins" x 18 },
            chance = 100 to 240),
    ROGUE(level = 32,
          xp = 36.5,
          stunTicks = 8,
          damage = 2,
          pockets = mapOf("Rogue" to "rogue"),
          drops = DropTableHandler.createSimple {
              "Coins" x 25..40 chance (108 of 128)
              "Air rune" x 8 chance (8 of 128)
              "Jug of wine" x 1 chance (6 of 128)
              "Lockpick" x 1 chance (5 of 128)
              "Iron dagger(p)" x 1 chance (1 of 128)
          },
          chance = 74 to 240),
    MASTER_FARMER(
        level = 38,
        xp = 43.0,
        stunTicks = 8,
        damage = 3,
        pockets = mapOf("Master Farmer" to "Master Farmer"),
        drops = GenericDropTables.generalSeedDropTable(false),
        chance = 90 to 240,
        shout = "Cor blimey mate, what are ye doing in me pockets?"),
    GUARD(level = 40,
          xp = 46.8,
          stunTicks = 8,
          damage = 2,
          pockets = mapOf("Guard" to "guard"),
          drops = DropTableHandler.createSingleton { "Coins" x 30 },
          chance = 50 to 240),
    KNIGHT_OF_ARDOUGNE(level = 55,
                       xp = 84.3,
                       stunTicks = 10,
                       damage = 3,
                       pockets = mapOf("Knight of Ardougne" to "knight"),
                       drops = DropTableHandler.createSingleton { "Coins" x 50 },
                       chance = 50 to 240),
    WATCHMAN(level = 65,
             xp = 137.5,
             stunTicks = 8,
             damage = 3,
             pockets = mapOf("Watchman" to "watchman"),
             drops = DropTableHandler.createSimple {
                 "Bread" x 1 chance ALWAYS
                 "Coins" x 60 chance ALWAYS
             },
             chance = 15 to 160),
    PALADIN(level = 70,
            xp = 151.8,
            stunTicks = 8,
            damage = 3,
            pockets = mapOf("Paladin" to "paladin"),
            drops = DropTableHandler.createSimple {
                "Chaos rune" x 2 chance ALWAYS
                "Coins" x 80 chance ALWAYS
                // TODO@0.5.0 Clue scroll (hard) 1/1000
            },
            chance = 12 to 150),
    GNOME(level = 75,
          xp = 198.3,
          stunTicks = 8,
          damage = 1,
          pockets = mapOf("Gnome" to "gnome", "Gnome woman" to "gnome"),
          drops = DropTableHandler.createSimple {
              "King worm" x 1 chance (55 of 128)
              "Coins" x 300 chance (30 of 128)
              "Swamp toad" x 1 chance (28 of 128)
              "Gold ore" x 1 chance (8 of 128)
              "Earth rune" x 1 chance (5 of 128)
              "Fire orb" x 1 chance (2 of 128)
              // TODO@0.5.0 medium clue scroll 1/150
          },
          chance = 8 to 120),
    HERO(level = 80,
         xp = 273.3,
         stunTicks = 10,
         damage = 4,
         pockets = mapOf("Hero" to "hero"),
         drops = DropTableHandler.createSimple {
             "Coins" x 200..300 chance (105 of 128)
             "Death rune" x 2 chance (8 of 128)
             "Jug of wine" x 1 chance (6 of 128)
             "Blood rune" x 1 chance (5 of 128)
             "Fire orb" x 1 chance (2 of 128)
             "Diamond" x 1 chance (1 of 128)
             "Gold ore" x 1 chance (1 of 128)
         },
         chance = 5 to 100);

    /**
     * The NPC names this type covers.
     */
    val names: List<String> = pockets.keys.toList()

    /**
     * The word for [npc] in pickpocketing messages, such as "man" in "You pick the man's pocket.".
     */
    fun pocket(npc: Npc): String = pockets[npc.def().name] ?: npc.def().name.lowercase()

    companion object {

        /**
         * Mappings of all names to [ThievingNpcType] instances.
         */
        val NAME_TO_NPC: ImmutableMap<String, ThievingNpcType> = run {
            val builder = ImmutableMap.builder<String, ThievingNpcType>()
            for (next in values()) {
                next.names.forEach { builder.put(it, next) }
            }
            builder.build()
        }
    }

}
