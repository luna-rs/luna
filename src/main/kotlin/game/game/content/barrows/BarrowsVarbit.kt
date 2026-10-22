package game.content.barrows

/**
 * Revision 377 Barrows fields packed into client varps 452 and 453. Bit endpoints are inclusive.
 *
 * The layout is preserved in LostCityRS/Content at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50,
 * pack/varbit.pack and scripts/_unpack/377/all.varbit. These helpers operate on a supplied parent value;
 * they do not persist player progress or send packets. Compose changes before sending the parent varp.
 *
 * @author lare96
 */
enum class BarrowsVarbit(val id: Int, val parentId: Int, val firstBit: Int, val lastBit: Int) {
    AHRIM_KILLED(457, 453, 0, 0),
    DHAROK_KILLED(458, 453, 1, 1),
    GUTHAN_KILLED(459, 453, 2, 2),
    KARIL_KILLED(460, 453, 3, 3),
    TORAG_KILLED(461, 453, 4, 4),
    VERAC_KILLED(462, 453, 5, 5),
    MONSTER_SCORE(463, 453, 6, 15),
    KILL_COUNT(464, 453, 17, 31),
    CHAMBER_A(465, 452, 6, 6),
    CHAMBER_C(466, 452, 7, 7),
    CHAMBER_G(467, 452, 8, 8),
    CHAMBER_I(468, 452, 9, 9),
    DOOR_A(469, 452, 10, 10),
    DOOR_B(470, 452, 11, 11),
    DOOR_C(471, 452, 12, 12),
    DOOR_D(472, 452, 13, 13),
    DOOR_E(473, 452, 14, 14),
    DOOR_F(474, 452, 15, 15),
    DOOR_G(475, 452, 16, 16),
    DOOR_H(476, 452, 17, 17),
    DOOR_I(477, 452, 18, 18),
    DOOR_J(478, 452, 19, 19),
    DOOR_K(479, 452, 20, 20),
    DOOR_L(480, 452, 21, 21),
    DOOR_M(481, 452, 22, 22),
    DOOR_N(482, 452, 23, 23),
    DOOR_O(483, 452, 24, 24),
    DOOR_P(484, 452, 25, 25),
    PUZZLE(485, 452, 29, 31),
    CHEST_OPEN(1394, 453, 16, 16);

    val maxValue: Int = (1 shl (lastBit - firstBit + 1)) - 1
    private val mask: Int = maxValue shl firstBit

    /**
     * Reads this field, including fields extending into the signed integer's highest bit.
     */
    fun read(parentValue: Int): Int = (parentValue ushr firstBit) and maxValue

    /**
     * Replaces this field without changing any other bits. Rejects values that would be truncated.
     */
    fun write(parentValue: Int, value: Int): Int {
        require(value in 0..maxValue) { "$name must be between 0 and $maxValue: $value" }
        return (parentValue and mask.inv()) or (value shl firstBit)
    }
}
