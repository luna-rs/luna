package game.content.vampireSlayer

import api.predef.ext.addObject
import api.predef.ext.removeObject
import api.predef.ext.scheduleOnce
import game.content.vampireSlayer.VampireSlayer as Quest
import game.obj.entrances.Entrances
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The cupboard and coffin are ordinary openable scenery. Only the Count's encounter reads quest progress.
 *
 * @author lare96
 */
object VampireProps {
    const val CUPBOARD = 2612
    const val CUPBOARD_OPEN = 2613
    const val COFFIN = 2614
    const val COFFIN_OPEN = 2615
    val cupboardTile = Position(3096, 3269, 1)
    val coffinTile = Position(3077, 9775)

    fun openCupboard(cupboard: GameObject) {
        Entrances.replace(cupboard, CUPBOARD_OPEN, 10)
    }

    fun searchCupboard(player: Player) {
        player.sendMessage(if (player.inventory.add(Quest.GARLIC)) "You take a clove of garlic from the cupboard."
                           else "The cupboard contains garlic, but you don't have room to carry it.")
    }

    fun openCoffin(player: Player, coffin: GameObject) {
        if (!Entrances.replace(coffin, COFFIN_OPEN, 10) || !CountDraynor.canWake(player) ||
            CountDraynor.find(player) != null) return
        val world = player.world
        world.scheduleOnce(4) {
            if (CountDraynor.canWake(player) && CountDraynor.find(player) == null) {
                val count = CountDraynor(player)
                if (world.npcs.add(count)) count.combat.attack(player)
            }
        }
    }

    fun close(player: Player, opened: GameObject, closedId: Int) {
        val world = player.world
        if (world.removeObject(opened)) world.addObject(closedId, opened.position, opened.objectType, opened.direction)
    }
}
