package game.content.blastFurnace

/**
 * Inclusive revision 377 fields from LostCityRS/Content 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50,
 * pack/varbit.pack and scripts/_unpack/377/all.varbit. Machinery cadence is separate reconstructed gameplay.
 *
 * @author lare96
 */
enum class BlastFurnaceVarbit(val id: Int, val parent: Int, val first: Int, val last: Int) {
    HOT(936, 543, 8, 9), TEMPERATURE(937, 543, 0, 7), PIPE(938, 543, 10, 10),
    FUEL(939, 543, 11, 11), COAL_REQUIRED(940, 543, 12, 29),
    BRONZE(941, 545, 0, 7), IRON(942, 545, 8, 15), STEEL(943, 545, 16, 23), MITHRIL(944, 545, 24, 31),
    ADAMANT(945, 546, 0, 7), RUNE(946, 546, 8, 15), GOLD(947, 546, 16, 23), SILVER(948, 546, 24, 31),
    COAL(949, 547, 0, 7), TIN_ORE(950, 547, 8, 15), IRON_ORE(951, 547, 16, 23), MITHRIL_ORE(952, 547, 24, 31),
    ADAMANT_ORE(953, 548, 0, 7), RUNE_ORE(954, 548, 8, 15), GOLD_ORE(955, 548, 16, 23), SILVER_ORE(956, 548, 24, 31),
    PERFECT_GOLD_ORE(957, 549, 0, 7), PERFECT_GOLD(958, 549, 8, 15), COPPER_ORE(959, 549, 16, 23);

    val maximum = (1 shl (last - first + 1)) - 1
    private val mask = maximum shl first
    fun read(value: Int): Int = value ushr first and maximum
    fun write(parentValue: Int, value: Int): Int {
        require(value in 0..maximum)
        return parentValue and mask.inv() or (value shl first)
    }
}
