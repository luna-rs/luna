package world.player.skill.prayer

import com.google.common.collect.ImmutableList
import io.luna.game.model.item.Item

/**
 * An enum representing bones that can be buried.
 */
enum class Bone(val id: Int, val exp: Double) {
    BONES(id = 526,
          exp = 4.5),
    WOLF_BONES(id = 2859,
               exp = 4.5),
    MONKEY_BONES(id = 3179,
                 exp = 5.0),
    BAT_BONES(id = 530,
              exp = 5.2),
    BIG_BONES(id = 532,
              exp = 15.0),
    BABYDRAGON_BONES(id = 534,
                     exp = 30.0),
    DRAGON_BONES(id = 536,
                 exp = 72.0);

    companion object {

        /**
         * A mapping of bones identifiers to instances.
         */
        val ID_TO_BONE = values().associateBy { it.id }

        /**
         * An immutable list of [Bone.values].
         */
        val ALL = ImmutableList.copyOf(values())
    }

    /**
     * The bone item (with 1 as the amount).
     */
    val boneItem = Item(id)
}