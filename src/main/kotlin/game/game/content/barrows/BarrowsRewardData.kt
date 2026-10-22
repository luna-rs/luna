package game.content.barrows

/**
 * Reviewable reward data from trufitus/Content at 0e7d7a8291d837f48646b2a962f3c5e208e744ad.
 * This is a reconstruction, not proof of 2005 behavior. Its PR explicitly leaves minimum reward
 * quantities unresolved. These data do not roll quantities, award items or activate the chest.
 *
 * @author lare96
 */
object BarrowsRewardData {
    val uniqueItems: Map<BarrowsBrother, List<Int>> = mapOf(
        BarrowsBrother.AHRIM to listOf(4708, 4710, 4712, 4714),
        BarrowsBrother.DHAROK to listOf(4716, 4718, 4720, 4722),
        BarrowsBrother.GUTHAN to listOf(4724, 4726, 4728, 4730),
        BarrowsBrother.KARIL to listOf(4732, 4734, 4736, 4738),
        BarrowsBrother.TORAG to listOf(4745, 4747, 4749, 4751),
        BarrowsBrother.VERAC to listOf(4753, 4755, 4757, 4759)
    )

    /**
     * An inclusive default-table roll band. Multiple item IDs are equally likely alternatives.
     * The quantity ceiling is the contribution's max_qty input, not a verified quantity distribution.
     *
     * @author lare96
     */
    data class CommonBand(val rolls: IntRange, val itemIds: List<Int>, val quantityCeiling: Int)

    val commonBands = listOf(
        CommonBand(1..380, listOf(995), 774),
        CommonBand(381..505, listOf(558), 336),
        CommonBand(506..630, listOf(562), 139),
        CommonBand(631..755, listOf(560), 83),
        CommonBand(756..880, listOf(565), 43),
        CommonBand(881..1005, listOf(4740), 40),
        CommonBand(1006..1011, listOf(985, 987), 1),
        CommonBand(1012..1012, listOf(1149), 1)
    )

    /**
     * Inputs to the candidate loot algorithm. A unique roll is 1 / uniqueDenominator on each roll;
     * chosen uniques are removed from uniquePool, and an empty pool falls back to the default table.
     *
     * @author lare96
     */
    data class Parameters(val rolls: Int, val potential: Int, val uniqueDenominator: Int, val uniquePool: List<Int>)

    fun parameters(killsParent: Int): Parameters {
        val killed = BarrowsBrother.entries.filter { it.killed.read(killsParent) == 1 }
        val score = BarrowsVarbit.MONSTER_SCORE.read(killsParent).coerceAtMost(1000)
        return Parameters(1 + killed.size, score + 2 * killed.size, 450 - 58 * killed.size,
                          killed.flatMap { uniqueItems.getValue(it) })
    }

    /**
     * The source rolls inclusively from zero through potential. Zero gives no default reward.
     */
    fun commonBand(roll: Int): CommonBand? {
        require(roll in 0..1012)
        return commonBands.singleOrNull { roll in it.rolls }
    }
}
