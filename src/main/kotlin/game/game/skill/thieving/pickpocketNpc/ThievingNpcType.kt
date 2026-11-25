package game.skill.thieving.pickpocketNpc

import api.drops.DropTable
import api.drops.DropTableHandler
import api.drops.GenericDropTables
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableMap
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Npc

/**
 * The loot shared by male and female H.A.M. members.
 */
private val HAM_MEMBER_DROPS = DropTableHandler.createSimple {
    "Coins" x 1..21 chance (15 of 100)
    "Buttons" x 1 chance (4 of 100)
    "Damaged armour" x 1 chance (4 of 100)
    "Rusty sword" x 1 chance (4 of 100)
    "Bronze arrow" x 1..13 chance (3 of 100)
    "Bronze axe" x 1 chance (3 of 100)
    "Bronze dagger" x 1 chance (3 of 100)
    "Bronze pickaxe" x 1 chance (3 of 100)
    "Iron axe" x 1 chance (3 of 100)
    "Iron dagger" x 1 chance (3 of 100)
    "Iron pickaxe" x 1 chance (3 of 100)
    "Leather body" x 1 chance (3 of 100)
    "Feather" x 1..7 chance (3 of 100)
    "Logs" x 1 chance (3 of 100)
    "Thread" x 1..10 chance (3 of 100)
    "Cowhide" x 1 chance (3 of 100)
    "Steel arrow" x 1..13 chance (2 of 100)
    "Steel axe" x 1 chance (2 of 100)
    "Steel dagger" x 1 chance (2 of 100)
    "Steel pickaxe" x 1 chance (2 of 100)
    "Knife" x 1 chance (2 of 100)
    "Needle" x 1 chance (2 of 100)
    "Raw anchovies" x 1 chance (2 of 100)
    "Raw chicken" x 1 chance (2 of 100)
    "Tinderbox" x 1 chance (2 of 100)
    "Uncut opal" x 1 chance (2 of 100)
    "Uncut jade" x 1 chance (2 of 100)
    "Coal" x 1 chance (2 of 100)
    "Iron ore" x 1 chance (2 of 100)
    199 x 1 chance (6 of 550) // Unidentified guam.
    201 x 1 chance (3 of 550) // Unidentified marrentill.
    203 x 1 chance (2 of 550) // Unidentified tarromin.
    4298 x 1 chance (1 of 100) // H.A.M. shirt.
    4300 x 1 chance (1 of 100) // H.A.M. robe.
    4302 x 1 chance (1 of 100) // H.A.M. hood.
    4304 x 1 chance (1 of 100) // H.A.M. cloak.
    4306 x 1 chance (1 of 100) // H.A.M. logo.
    4308 x 1 chance (1 of 100) // H.A.M. gloves.
    4310 x 1 chance (1 of 100) // H.A.M. boots.
    // TODO@0.5.0 Easy clue scroll 2/100.
}

/**
 * Represents an [Npc] that can be stolen from.
 *
 * @param pockets The NPC names this type covers, each with the word used in "You pick the ...'s pocket.".
 * @param stunTicks How long a failed attempt stuns the player, counted from the "You've been stunned!" message.
 * @param damage The fixed damage dealt when the player is caught.
 * @param chance The success chance at level 1 and level 99, out of 255.
 * @param maxDamage The most damage dealt when the player is caught, for NPCs whose hit varies from [damage].
 * @param ids NPC ids this type covers, each with its pocket word, for NPCs that share their name with another type.
 * @param shouts What the NPC says when it catches the player, one picked at random.
 * @param blackjackOnly Whether the NPC always catches the player, because it can only be robbed once knocked out.
 * @author lare96
 */
enum class ThievingNpcType(val level: Int,
                           val xp: Double,
                           val stunTicks: Int,
                           val damage: Int,
                           val pockets: Map<String, String>,
                           val drops: DropTable,
                           val chance: Pair<Int, Int>,
                           val shout: String = "What do you think you're doing?",
                           val maxDamage: Int = damage,
                           val ids: Map<Int, String> = emptyMap(),
                           val shouts: List<String> = listOf(shout),
                           val blackjackOnly: Boolean = false) {
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
         chance = 5 to 100),

    // TODO The Fremennik Trials: until it's complete, citizens can't be pickpocketed ("They are too suspicious of you
    //  for you to get close enough to steal from them." and the citizen says "You stay away from me outerlander!").
    //  After it, a citizen who catches the player uses their Fremennik name: "You shame us, <name>!", "How can you
    //  steal from me, <name>?", "You have betrayed your heritage, <name>!" or "Why would you try to steal from me,
    //  <name>?".
    FREMENNIK_CITIZEN(level = 45,
                      xp = 65.0,
                      stunTicks = 8,
                      damage = 2,
                      pockets = listOf("Agnar", "Freidir", "Borrokar", "Lanzig", "Pontak", "Freygerd", "Lensa",
                                       "Jennella", "Sassilik", "Inga").associateWith { "Fremennik" },
                      drops = DropTableHandler.createSingleton { "Coins" x 40 },
                      chance = 40 to 240),
    DESERT_BANDIT(level = 53,
                  xp = 79.4,
                  stunTicks = 8,
                  damage = 3,
                  pockets = emptyMap(),
                  ids = mapOf(1926 to "bandit", 1931 to "bandit"),
                  drops = DropTableHandler.createSimple {
                      "Coins" x 30 chance (5 of 7)
                      "Antipoison(1)" x 1 chance (1 of 7)
                      "Lockpick" x 1 chance (1 of 7)
                  },
                  chance = 50 to 240),

    // No success rate is recorded for the Pollnivneach bandits. They use the rates of the level 55 knight and the
    // level 45 Fremennik citizen.
    POLLNIVNEACH_BANDIT(level = 55,
                        xp = 84.3,
                        stunTicks = 8,
                        damage = 5,
                        pockets = emptyMap(),
                        ids = mapOf(1880 to "bandit", 1881 to "bandit"),
                        drops = DropTableHandler.createSingleton { "Coins" x 50 },
                        chance = 50 to 240),
    BEARDED_POLLNIVNEACH_BANDIT(level = 45,
                                xp = 65.0,
                                stunTicks = 8,
                                damage = 5,
                                pockets = emptyMap(),
                                ids = mapOf(1883 to "bandit", 1884 to "bandit"),
                                drops = DropTableHandler.createSingleton { "Coins" x 40 },
                                chance = 40 to 240),

    // TODO The Feud: a villager knocked out with a blackjack ("Lure", then "Knock-Out") can be pickpocketed.
    POLLNIVNEACH_VILLAGER(level = 30,
                          xp = 8.0,
                          stunTicks = 8,
                          damage = 2,
                          pockets = emptyMap(),
                          ids = listOf(1888, 1889, 1890, 1892, 1893, 1894, 1896, 1897, 1898)
                              .associateWith { "villager" },
                          drops = DropTableHandler.createSingleton { "Coins" x 5 },
                          chance = 0 to 0,
                          shouts = listOf("I know your tricks, thief.", "You won't get my purse."),
                          blackjackOnly = true),
    ELF(level = 85,
        xp = 353.3,
        stunTicks = 10,
        damage = 5,
        pockets = listOf("Goreu", "Ysgawyn", "Arvel", "Mawrth", "Kelyn").associateWith { "elf" },
        drops = DropTableHandler.createSimple {
            "Coins" x 280..350 chance (105 of 128)
            "Death rune" x 2 chance (8 of 128)
            "Jug of wine" x 1 chance (6 of 128)
            "Nature rune" x 3 chance (5 of 128)
            "Fire orb" x 1 chance (2 of 128)
            "Diamond" x 1 chance (1 of 128)
            "Gold ore" x 1 chance (1 of 128)
        },
        chance = 6 to 100),

    // TODO Getting caught too often gets the player thrown in the H.A.M. jail or "beaten unconscious and bundled out
    //  of the HAM camp". No source says how often either happens.
    FEMALE_HAM_MEMBER(level = 15,
                      xp = 18.5,
                      stunTicks = 7,
                      damage = 1,
                      maxDamage = 2,
                      pockets = emptyMap(),
                      ids = mapOf(1715 to "H.A.M. member"),
                      drops = HAM_MEMBER_DROPS,
                      chance = 135 to 240),
    MALE_HAM_MEMBER(level = 20,
                    xp = 22.2,
                    stunTicks = 7,
                    damage = 2,
                    maxDamage = 3,
                    pockets = emptyMap(),
                    ids = mapOf(1714 to "H.A.M. member"),
                    drops = HAM_MEMBER_DROPS,
                    chance = 135 to 240);

    /**
     * The NPC names this type covers.
     */
    val names: List<String> = pockets.keys.toList()

    /**
     * The word for [npc] in pickpocketing messages, such as "man" in "You pick the man's pocket.".
     */
    fun pocket(npc: Npc): String = pocket(npc.def())

    /**
     * The word for an NPC with definition [def] in pickpocketing messages.
     */
    fun pocket(def: NpcDefinition): String = ids[def.id()] ?: pockets[def.name] ?: def.name.lowercase()

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

        /**
         * Mappings of NPC ids to [ThievingNpcType] instances, for NPCs that share their name with another type.
         */
        val ID_TO_NPC: ImmutableMap<Int, ThievingNpcType> = run {
            val builder = ImmutableMap.builder<Int, ThievingNpcType>()
            for (next in values()) {
                next.ids.keys.forEach { builder.put(it, next) }
            }
            builder.build()
        }

        /**
         * Returns the [ThievingNpcType] for [def], or `null` if it can't be pickpocketed.
         */
        fun forNpc(def: NpcDefinition): ThievingNpcType? = ID_TO_NPC[def.id()] ?: NAME_TO_NPC[def.name]
    }

}
