package game.content.ectofuntus

import game.skill.prayer.Bone

/**
 * Unnoted bone and bonemeal pairs present in the revision 377 cache. Worship uses four times burial XP.
 * Wyvern bones retain their pre-2014 burial value; later ashes and bone types are deliberately absent.
 *
 * @author lare96
 */
enum class EctofuntusBone(val bone: Bone, val bonemealId: Int) {
    REGULAR(Bone.BONES, 4255),
    BAT(Bone.BAT_BONES, 4256),
    BIG(Bone.BIG_BONES, 4257),
    BURNT(Bone.BURNT_BONES, 4258),
    BURNT_JOGRE(Bone.BURNT_JOGRE_BONES, 4259),
    BABY_DRAGON(Bone.BABYDRAGON_BONES, 4260),
    DRAGON(Bone.DRAGON_BONES, 4261),
    WOLF(Bone.WOLF_BONES, 4262),
    SMALL_NINJA(Bone.SMALL_NINJA_MONKEY_BONES, 4263),
    MEDIUM_NINJA(Bone.MEDIUM_NINJA_MONKEY_BONES, 4264),
    GORILLA(Bone.GORILLA_BONES, 4265),
    BEARDED_GORILLA(Bone.BEARDED_GORILLA_BONES, 4266),
    MONKEY(Bone.MONKEY_BONES, 4267),
    SMALL_ZOMBIE(Bone.SMALL_ZOMBIE_MONKEY_BONES, 4268),
    LARGE_ZOMBIE(Bone.LARGE_ZOMBIE_MONKEY_BONES, 4269),
    SKELETAL_MONKEY(Bone.SKELETAL_MONKEY_BONES, 4270),
    JOGRE(Bone.JOGRE_BONES, 4271),
    ZOGRE(Bone.ZOGRE_BONES, 4852),
    FAYRG(Bone.FAYRG_BONES, 4853),
    RAURG(Bone.RAURG_BONES, 4854),
    OURG(Bone.OURG_BONES, 4855),
    SHAIKAHAN(Bone.SHAIKAHAN_BONES, 5615),
    DAGANNOTH(Bone.DAGANNOTH_BONES, 6728),
    WYVERN(Bone.WYVERN_BONES, 6810);

    val boneId: Int get() = bone.id
    val baseXp: Double get() = bone.exp

    companion object {
        private val bones = entries.associateBy { it.boneId }
        private val meals = entries.associateBy { it.bonemealId }

        fun forBone(id: Int): EctofuntusBone? = bones[id]
        fun forMeal(id: Int): EctofuntusBone? = meals[id]
    }
}
