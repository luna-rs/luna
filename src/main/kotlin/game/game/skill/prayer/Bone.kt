package game.skill.prayer

import com.google.common.collect.ImmutableList
import io.luna.game.model.item.Item

/**
 * An enum representing bones that can be buried.
 *
 * @author lare96
 */
enum class Bone(val id: Int, val exp: Double) {
    BONES(id = 526,
          exp = 4.5),
    WOLF_BONES(id = 2859,
               exp = 4.5),
    MONKEY_BONES(id = 3183,
                 exp = 5.0),
    BAT_BONES(id = 530,
              exp = 5.3),
    BIG_BONES(id = 532,
              exp = 15.0),
    BABYDRAGON_BONES(id = 534,
                     exp = 30.0),
    DRAGON_BONES(id = 536,
                 exp = 72.0),
    BURNT_BONES(528, 4.5),
    BURNT_JOGRE_BONES(3127, 16.0),
    SMALL_NINJA_MONKEY_BONES(3179, 16.0),
    MEDIUM_NINJA_MONKEY_BONES(3180, 18.0),
    GORILLA_BONES(3181, 18.0),
    BEARDED_GORILLA_BONES(3182, 20.0),
    SMALL_ZOMBIE_MONKEY_BONES(3185, 5.0),
    LARGE_ZOMBIE_MONKEY_BONES(3186, 5.0),
    SKELETAL_MONKEY_BONES(3187, 3.0),
    JOGRE_BONES(3125, 15.0),
    ZOGRE_BONES(4812, 22.5),
    FAYRG_BONES(4830, 84.0),
    RAURG_BONES(4832, 96.0),
    OURG_BONES(4834, 140.0),
    SHAIKAHAN_BONES(3123, 25.0),
    DAGANNOTH_BONES(6729, 125.0),
    WYVERN_BONES(6812, 50.0);

    companion object {

        /**
         * A mapping of bones identifiers to instances.
         */
        val ID_TO_BONE = entries.associateBy { it.id }

        /**
         * An immutable list of [Bone.values].
         */
        val ALL = ImmutableList.copyOf(entries)
    }

    /**
     * The bone item (with 1 as the amount).
     */
    val boneItem = Item(id)
}
