import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 *
 *
 * @author lare96 <http://github.com/lare96>
 */
class TanInterface : StandardInterface(14670) {

    companion object {
        val HIDES = listOf(
                Hide.SOFT_LEATHER,
                Hide.HARD_LEATHER,
                Hide.SWAMP_SNAKESKIN,
                Hide.SNAKESKIN,
                Hide.GREEN_D_LEATHER,
                Hide.BLUE_D_LEATHER,
                Hide.RED_D_LEATHER,
                Hide.BLACK_D_LEATHER
        )
    }

    override fun onOpen(plr: Player) {
        var nameWidget = 14777
        var costWidget = 14785
        var modelWidget = 14769
        for (it in HIDES) {
            plr.sendText(it.displayName, nameWidget++)
            plr.sendText("${it.cost} coins", costWidget++)
            plr.queue(WidgetItemModelMessageWriter(modelWidget++, 250, it.hide))
        }
    }
}