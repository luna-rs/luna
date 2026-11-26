package game.content.championsChallenge

/**
 * The rule a champion sets for its fight.
 */
enum class ChampionRule(val text: String) {
    NO_SPECIAL("For this fight you're not allowed to use any Special Attacks."),
    MAGIC_ONLY("For this fight you're only allowed to use Magic attacks, no Melee or Ranged."),
    RANGED_ONLY("For this fight you're only allowed to use Ranged attacks, no Melee or Magic."),
    NO_MAGIC("For this fight you're not allowed to use any Magic attacks."),
    MELEE_ONLY("For this fight you're only allowed to use Melee attacks, no Ranged or Magic."),
    NO_MELEE("For this fight you're not allowed to use any Melee attacks."),
    WEAPON_ONLY("For this fight you're only allowed to take Weapons, no other items are allowed."),
    NO_PRAYER("For this fight you're not allowed to use any Prayers."),
    NO_RANGED("For this fight you're not allowed to use any Ranged attacks."),
    UNARMED("For this fight you're not allowed any Weapons or Armour."),
    EMPTY_INVENTORY("He has stated there will be no items allowed except those you're wearing.")
}

/**
 * A champion of the Champions' Challenge: its scroll, its NPC, the varbit that hangs its banner and the Slayer and
 * Hitpoints experience it awards. Leon d'Cour has no scroll or banner; he challenges players who beat the other ten.
 */
enum class Champion(val scroll: Int, val npc: Int, val banner: Int, val xp: Int, val rule: ChampionRule,
                    val challenge: String, val signature: String) {
    EARTH_WARRIOR(6798, 3057, 1452, 432, ChampionRule.NO_PRAYER,
        "I challenge you to a duel, come to the arena beneath the Champions' Guild and fight me if you dare.",
        "Champion of Earth Warriors"),
    GHOUL(6799, 3059, 1453, 400, ChampionRule.WEAPON_ONLY,
        "Come and duel me at the Champions' Guild, I'll make sure nothing goes to waste.", "Champion of Ghouls"),
    GIANT(6800, 3058, 1454, 280, ChampionRule.MELEE_ONLY,
        "Get yourself to the Champions' Guild, if you dare to face me puny human.", "Champion of Giants"),
    GOBLIN(6801, 3060, 1455, 128, ChampionRule.MAGIC_ONLY,
        "Fight me if you think you can human, I'll wait for you in the Champions's Guild.", "Champion of Goblins"),
    HOBGOBLIN(6802, 3061, 1456, 232, ChampionRule.NO_MELEE,
        "You won't defeat me, though you're welcome to try at the Champions' Guild.", "Champion of Hobgoblins"),
    IMP(6803, 3062, 1457, 160, ChampionRule.NO_SPECIAL,
        "How about picking on someone your own size? I'll see you at the Champions' Guild.", "Champion of Imps"),
    JOGRE(6804, 3063, 1458, 480, ChampionRule.NO_RANGED,
        "You think you can defeat me? Come to the Champions' Guild and prove it!", "Champion of Jogres"),
    LESSER_DEMON(6805, 3064, 1459, 592, ChampionRule.UNARMED,
        "Come to the Champions' Guild so I can banish you mortal!", "Champion of Lesser Demons"),
    SKELETON(6806, 3065, 1460, 232, ChampionRule.RANGED_ONLY,
        "I'll be waiting at the Champions' Guild to collect your bones.", "Champion of Skeletons"),
    ZOMBIE(6807, 3066, 1461, 240, ChampionRule.NO_MAGIC,
        "You come to Champions' Guild, you fight me, I squish you, I get brains!", "Champion of Zombies"),
    LEON(-1, 3067, -1, 492, ChampionRule.EMPTY_INVENTORY, "", "");

    val bit = 1 shl ordinal

    companion object {
        val LOWER = entries.filter { it != LEON }
        val ALL_LOWER = LOWER.fold(0) { mask, it -> mask or it.bit }
        val SCROLLS = LOWER.associateBy { it.scroll }
        val NPCS = entries.associateBy { it.npc }
    }
}
