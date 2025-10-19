package world.player.skill.slayer

import com.google.common.collect.ImmutableSet

/**
 * An enum representing slayer masters and their tasks.
 *
 * @author lare96
 */
enum class SlayerMaster(val id: Int,
                        val combatLevel: Int = 0,
                        val slayerLevel: Int = 0,
                        val tasks: List<SlayerTask>) {
    TURAEL(id = 70,
           tasks = listOf(
               SlayerTask(type = SlayerTaskType.BANSHEE, amount = 15..30, weight = 8),
               SlayerTask(type = SlayerTaskType.BAT, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.BEAR, amount = 10..20, weight = 7),
               SlayerTask(type = SlayerTaskType.BIRD, amount = 15..30, weight = 6),
               SlayerTask(type = SlayerTaskType.CAVE_BUG, amount = 10..30, weight = 8),
               SlayerTask(type = SlayerTaskType.CAVE_CRAWLER, amount = 15..30, weight = 8),
               SlayerTask(type = SlayerTaskType.CAVE_SLIME, amount = 10..20, weight = 8),
               SlayerTask(type = SlayerTaskType.COW, amount = 15..30, weight = 8),
               SlayerTask(type = SlayerTaskType.CRAWLING_HAND, amount = 15..30, weight = 8),
               SlayerTask(type = SlayerTaskType.DOG, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.DWARF, amount = 10..25, weight = 7),
               SlayerTask(type = SlayerTaskType.GHOST, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.GOBLIN, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.ICEFIEND, amount = 15..20, weight = 8),
               SlayerTask(type = SlayerTaskType.KALPHITE, amount = 15..30, weight = 6),
               SlayerTask(type = SlayerTaskType.LIZARD, amount = 15..30, weight = 8),
               SlayerTask(type = SlayerTaskType.MONKEY, amount = 15..30, weight = 6),
               SlayerTask(type = SlayerTaskType.RAT, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.SCORPION, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.SKELETON, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.SPIDER, amount = 15..30, weight = 6),
               SlayerTask(type = SlayerTaskType.WOLF, amount = 15..30, weight = 7),
               SlayerTask(type = SlayerTaskType.ZOMBIE, amount = 15..30, weight = 7)
           )),

    MAZCHNA(id = 1596,
            combatLevel = 20,
            tasks = listOf(
                SlayerTask(type = SlayerTaskType.BANSHEE, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.BAT, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.BEAR, amount = 30..50, weight = 6),
                SlayerTask(type = SlayerTaskType.CAVE_BUG, amount = 10..20, weight = 8),
                SlayerTask(type = SlayerTaskType.CAVE_CRAWLER, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.CAVE_SLIME, amount = 10..20, weight = 8),
                SlayerTask(type = SlayerTaskType.CRAWLING_HAND, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.DOG, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.GHOST, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.GHOUL, amount = 10..20, weight = 7),
                SlayerTask(type = SlayerTaskType.HILL_GIANT, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.HOBGOBLIN, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.ICE_WARRIOR, amount = 40..50, weight = 7),
                SlayerTask(type = SlayerTaskType.KALPHITE, amount = 30..50, weight = 6),
                SlayerTask(type = SlayerTaskType.KILLERWATT, amount = 30..50, weight = 6),
                SlayerTask(type = SlayerTaskType.LIZARD, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.MOGRE, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.PYREFIEND, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.ROCKSLUG, amount = 30..50, weight = 8),
                SlayerTask(type = SlayerTaskType.SCORPION, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.SHADE, amount = 30..70, weight = 8),
                SlayerTask(type = SlayerTaskType.SKELETON, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.WALL_BEAST, amount = 10..20, weight = 7),
                SlayerTask(type = SlayerTaskType.WOLF, amount = 30..50, weight = 7),
                SlayerTask(type = SlayerTaskType.ZOMBIE, amount = 30..50, weight = 7)
            )),

    VANNAKA(id = 1597,
            combatLevel = 40,
            tasks = listOf(
                SlayerTask(type = SlayerTaskType.ABERRANT_SPECTRE, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.BASILISK, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.BLOODVELD, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.COCKATRICE, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.ROCK_CRAB, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.DUST_DEVIL, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.HARPIE_BUG_SWARM, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.INFERNAL_MAGE, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.JELLY, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.OTHERWORLDLY_BEING, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.PYREFIEND, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.SHADE, amount = 40..90, weight = 8),
                SlayerTask(type = SlayerTaskType.SHADOW_WARRIOR, amount = 30..80, weight = 8),
                SlayerTask(type = SlayerTaskType.TUROTH, amount = 30..90, weight = 8),
                SlayerTask(type = SlayerTaskType.BLUE_DRAGON, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.DAGANNOTH, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.ELF, amount = 30..70, weight = 7),
                SlayerTask(type = SlayerTaskType.FEVER_SPIDER, amount = 30..90, weight = 7),
                SlayerTask(type = SlayerTaskType.FIRE_GIANT, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.GHOUL, amount = 10..40, weight = 7),
                SlayerTask(type = SlayerTaskType.HELLHOUND, amount = 30..60, weight = 7),
                SlayerTask(type = SlayerTaskType.HILL_GIANT, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.HOBGOBLIN, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.ICE_GIANT, amount = 30..80, weight = 7),
                SlayerTask(type = SlayerTaskType.ICE_WARRIOR, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.KALPHITE, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.KURASK, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.LESSER_DEMON, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.MOGRE, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.MOSS_GIANT, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.OGRE, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.TROLL, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.WEREWOLF, amount = 30..60, weight = 7),
                SlayerTask(type = SlayerTaskType.KURASK, amount = 40..90, weight = 7),
                SlayerTask(type = SlayerTaskType.CROCODILE, amount = 40..90, weight = 6),
                SlayerTask(type = SlayerTaskType.ABYSSAL_DEMON, amount = 40..90, weight = 5),
                SlayerTask(type = SlayerTaskType.GARGOYLE, amount = 40..90, weight = 5),
                SlayerTask(type = SlayerTaskType.NECHRYAEL, amount = 40..90, weight = 5)
            )),

    CHAELDAR(id = 1598,
             combatLevel = 70,
             tasks = listOf(
                 SlayerTask(type = SlayerTaskType.ABYSSAL_DEMON, amount = 70..130, weight = 12),
                 SlayerTask(type = SlayerTaskType.FIRE_GIANT, amount = 70..130, weight = 12),
                 SlayerTask(type = SlayerTaskType.KURASK, amount = 70..130, weight = 12),
                 SlayerTask(type = SlayerTaskType.NECHRYAEL, amount = 70..130, weight = 12),
                 SlayerTask(type = SlayerTaskType.DAGANNOTH, amount = 70..130, weight = 11),
                 SlayerTask(type = SlayerTaskType.GARGOYLE, amount = 70..130, weight = 11),
                 SlayerTask(type = SlayerTaskType.KALPHITE, amount = 70..130, weight = 11),
                 SlayerTask(type = SlayerTaskType.TROLL, amount = 70..130, weight = 11),
                 SlayerTask(type = SlayerTaskType.BLACK_DEMON, amount = 70..130, weight = 11),
                 SlayerTask(type = SlayerTaskType.JELLY, amount = 70..130, weight = 10),
                 SlayerTask(type = SlayerTaskType.TUROTH, amount = 70..130, weight = 10),
                 SlayerTask(type = SlayerTaskType.DUST_DEVIL, amount = 70..130, weight = 9),
                 SlayerTask(type = SlayerTaskType.GREATER_DEMON, amount = 70..130, weight = 9),
                 SlayerTask(type = SlayerTaskType.HELLHOUND, amount = 70..130, weight = 9),
                 SlayerTask(type = SlayerTaskType.LESSER_DEMON, amount = 70..130, weight = 9),
                 SlayerTask(type = SlayerTaskType.ABERRANT_SPECTRE, amount = 70..130, weight = 8),
                 SlayerTask(type = SlayerTaskType.BLOODVELD, amount = 70..130, weight = 8),
                 SlayerTask(type = SlayerTaskType.BLUE_DRAGON, amount = 70..130, weight = 8),
                 SlayerTask(type = SlayerTaskType.ROCK_CRAB, amount = 70..130, weight = 8),
                 SlayerTask(type = SlayerTaskType.ELF, amount = 70..130, weight = 8),
                 SlayerTask(type = SlayerTaskType.SHADOW_WARRIOR, amount = 70..130, weight = 8),
                 SlayerTask(type = SlayerTaskType.BASILISK, amount = 70..130, weight = 7),
                 SlayerTask(type = SlayerTaskType.FEVER_SPIDER, amount = 70..130, weight = 7),
                 SlayerTask(type = SlayerTaskType.ZYGOMITE, amount = 8..15, weight = 7),
                 SlayerTask(type = SlayerTaskType.SKELETAL_WYVERN, amount = 10..20, weight = 7)
             )),

    DURADEL(id = 1599,
            combatLevel = 100,
            slayerLevel = 50,
            tasks = listOf(
                SlayerTask(type = SlayerTaskType.ABYSSAL_DEMON, amount = 130..200, weight = 12),
                SlayerTask(type = SlayerTaskType.DARK_BEAST, amount = 10..20, weight = 11),
                SlayerTask(type = SlayerTaskType.HELLHOUND, amount = 130..200, weight = 10),
                SlayerTask(type = SlayerTaskType.BLACK_DRAGON, amount = 10..20, weight = 9),
                SlayerTask(type = SlayerTaskType.DAGANNOTH, amount = 130..199, weight = 9),
                SlayerTask(type = SlayerTaskType.GREATER_DEMON, amount = 130..200, weight = 9),
                SlayerTask(type = SlayerTaskType.KALPHITE, amount = 130..200, weight = 9),
                SlayerTask(type = SlayerTaskType.GREATER_DEMON, amount = 130..200, weight = 9),
                SlayerTask(type = SlayerTaskType.NECHRYAEL, amount = 130..200, weight = 9),
                SlayerTask(type = SlayerTaskType.BLACK_DEMON, amount = 130..200, weight = 8),
                SlayerTask(type = SlayerTaskType.BLOODVELD, amount = 130..200, weight = 8),
                SlayerTask(type = SlayerTaskType.GARGOYLE, amount = 130..200, weight = 8),
                SlayerTask(type = SlayerTaskType.RED_DRAGON, amount = 30..65, weight = 8),
                SlayerTask(type = SlayerTaskType.ABERRANT_SPECTRE, amount = 130..200, weight = 7),
                SlayerTask(type = SlayerTaskType.BASILISK, amount = 130..200, weight = 7),
                SlayerTask(type = SlayerTaskType.FIRE_GIANT, amount = 130..200, weight = 7),
                SlayerTask(type = SlayerTaskType.SKELETAL_WYVERN, amount = 20..40, weight = 7),
                SlayerTask(type = SlayerTaskType.STEEL_DRAGON, amount = 10..20, weight = 7),
                SlayerTask(type = SlayerTaskType.TROLL, amount = 130..200, weight = 6),
                SlayerTask(type = SlayerTaskType.DUST_DEVIL, amount = 130..200, weight = 5),
                SlayerTask(type = SlayerTaskType.IRON_DRAGON, amount = 40..60, weight = 5),
                SlayerTask(type = SlayerTaskType.BLUE_DRAGON, amount = 110..170, weight = 4),
                SlayerTask(type = SlayerTaskType.ELF, amount = 110..170, weight = 4),
                SlayerTask(type = SlayerTaskType.KURASK, amount = 130..200, weight = 4),
                SlayerTask(type = SlayerTaskType.ZYGOMITE, amount = 20..30, weight = 2)
            ));

    /**
     * A set of all possible [SlayerTaskType] types from this master.
     */
    val types = tasks.map { it.type }.toSet()

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableSet.copyOf(values())

        /**
         * Computes the next best slayer master based on [other].
         */
        fun computeNextBestMaster(other: SlayerMaster): SlayerMaster {
            return when (other) {
                TURAEL -> MAZCHNA
                MAZCHNA -> CHAELDAR
                CHAELDAR -> VANNAKA
                VANNAKA -> DURADEL
                DURADEL -> DURADEL
            }
        }
    }
}