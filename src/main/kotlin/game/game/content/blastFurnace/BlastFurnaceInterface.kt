package game.content.blastFurnace

import game.skill.smithing.BarType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface

/**
 * Native stock interface, with withdrawal bound to the current open instance and dispenser position.
 * Child offsets come from pinned Lost City inter_299.if; root id is 14458.
 *
 * @author lare96
 */
class BlastFurnaceInterface(private val origin: Position) : StandardInterface(ID) {
    companion object {
        const val ID = 14458
        val order = listOf(BarType.BRONZE, BarType.IRON, BarType.STEEL, BarType.MITHRIL,
            BarType.ADAMANT, BarType.RUNE, BarType.SILVER, BarType.GOLD)
        val single = mapOf(BarType.IRON to 75, BarType.BRONZE to 78, BarType.STEEL to 92, BarType.MITHRIL to 93,
            BarType.ADAMANT to 94, BarType.RUNE to 95, BarType.SILVER to 96, BarType.GOLD to 97)
        fun allButton(bar: BarType): Int = ID + 1 + 100 + order.indexOf(bar)
    }
    fun take(player: Player, bar: BarType, all: Boolean): Int {
        if (!isOpen || player.overlays.getOverlay(BlastFurnaceInterface::class.java) !== this ||
            player.position != origin) return 0
        return BlastFurnace.take(player, bar, if (all) 255 else 1)
    }
}
