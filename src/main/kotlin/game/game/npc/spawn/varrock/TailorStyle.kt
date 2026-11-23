package game.npc.spawn.varrock

import io.luna.game.model.mob.block.PlayerAppearance

/**
 * Thessalia's four make-over catalogues: a man's top, a woman's top, a man's legwear and a woman's legwear. A top
 * catalogue sets the body and the arms, a legwear catalogue sets the legs, and either one sets that part's colour.
 *
 * @param interfaceId The catalogue window.
 * @param gender The gender that the catalogue is for.
 * @param part The appearance slot that the style buttons set.
 * @param styles The identity kit behind each style button.
 * @param arms The identity kit behind each arms button, empty for legwear.
 * @param colourPart The appearance colour slot that the colour buttons set.
 * @param colours The colour buttons, in colour order.
 * @param confirmButton The confirm button.
 * @author TheLining
 */
enum class TailorStyle(val interfaceId: Int,
                       val gender: Int,
                       val part: Int,
                       val styles: Map<Int, Int>,
                       val arms: Map<Int, Int>,
                       val colourPart: Int,
                       val colours: List<Int>,
                       val confirmButton: Int) {

    MAN_TOP(interfaceId = 2851,
            gender = PlayerAppearance.GENDER_MALE,
            part = PlayerAppearance.CHEST,
            styles = mapOf(3010 to 18, 3011 to 19, 3012 to 21, 3013 to 20, 3014 to 23, 3015 to 22, 3016 to 25),
            arms = mapOf(3017 to 26, 3018 to 27, 3019 to 28, 3020 to 29, 3021 to 30, 3022 to 31),
            colourPart = PlayerAppearance.TORSO_COLOR,
            colours = (2943..2958).toList(),
            confirmButton = 2959),

    WOMAN_TOP(interfaceId = 3038,
              gender = PlayerAppearance.GENDER_FEMALE,
              part = PlayerAppearance.CHEST,
              styles = mapOf(3186 to 56, 3187 to 57, 3188 to 58, 3189 to 59, 3190 to 60),
              arms = mapOf(3191 to 61, 3192 to 62, 3193 to 63, 3194 to 64, 3195 to 65),
              colourPart = PlayerAppearance.TORSO_COLOR,
              colours = (3130..3145).toList(),
              confirmButton = 3146),

    MAN_LEGS(interfaceId = 0,
             gender = PlayerAppearance.GENDER_MALE,
             part = PlayerAppearance.LEGS,
             styles = mapOf(130 to 36, 131 to 37, 132 to 38, 133 to 39),
             arms = emptyMap(),
             colourPart = PlayerAppearance.LEG_COLOR,
             colours = listOf(107) + (92..106),
             confirmButton = 108),

    WOMAN_LEGS(interfaceId = 4731,
               gender = PlayerAppearance.GENDER_FEMALE,
               part = PlayerAppearance.LEGS,
               styles = mapOf(4865 to 70, 4866 to 71, 4867 to 72, 4868 to 74, 4869 to 73, 4871 to 76, 4872 to 77),
               arms = emptyMap(),
               colourPart = PlayerAppearance.LEG_COLOR,
               colours = listOf(4838) + (4823..4837),
               confirmButton = 4839);

    /**
     * If this is a top catalogue, with arms to pick as well.
     */
    val top get() = arms.isNotEmpty()
}
