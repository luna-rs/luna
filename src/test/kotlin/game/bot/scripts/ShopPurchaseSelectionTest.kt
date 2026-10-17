package game.bot.scripts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random
import game.bot.scripts.trading.BuyFromStoreBotScript.ShopPurchaseSelection

/**
 * Tests weighted purchase ordering and variety between equally valued boot colors.
 *
 * @author lare96
 */
class ShopPurchaseSelectionTest {
    @Test fun `expensive and coveted items are favored without excluding cheaper items`() {
        val random = Random(377)
        val first = IntArray(3)
        repeat(20_000) {
            val order = ShopPurchaseSelection.order(listOf(0, 1, 2), random) {
                when (it) {
                    0 -> ShopPurchaseSelection.weight(100.0, 1)
                    1 -> ShopPurchaseSelection.weight(10_000.0, 1)
                    else -> ShopPurchaseSelection.weight(10_000.0, 3)
                }
            }
            assertEquals(setOf(0, 1, 2), order.toSet())
            assertEquals(3, order.size)
            first[order.first()]++
        }
        assertTrue(first[0] > 100)
        assertTrue(first[1] > first[0] * 7)
        assertTrue(first[2] > first[1] * 1.7)
    }

    @Test fun `equally priced boot colors can all be selected first`() {
        val random = Random(2894)
        val boots = listOf(2894, 2934, 2904, 2924, 2914, 3791)
        val first = boots.associateWith { 0 }.toMutableMap()
        repeat(12_000) {
            val id = ShopPurchaseSelection.order(boots, random) { ShopPurchaseSelection.weight(500.0, 1) }.first()
            first[id] = first.getValue(id) + 1
        }
        assertTrue(first.values.all { it in 1700..2300 })
    }

    @Test fun `empty candidates and zero value stock are safe`() {
        assertTrue(ShopPurchaseSelection.order(emptyList<Int>()) { 1.0 }.isEmpty())
        assertEquals(1.0, ShopPurchaseSelection.weight(0.0, 0))
        assertEquals(listOf(1), ShopPurchaseSelection.order(listOf(1)) { ShopPurchaseSelection.weight(0.0, 0) })
    }
}
