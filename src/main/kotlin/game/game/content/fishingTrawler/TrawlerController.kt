package game.content.fishingTrawler

import com.google.common.collect.ImmutableSet
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener
import io.luna.game.model.mob.controller.PlayerController

/**
 * Applies Lost City's pre_tele_checks restrictions to spells and item teleports at sea and in the wreck.
 * The docked waiting boat remains outside this area, and scripted voyage movement remains available.
 *
 * @author lare96
 */
class TrawlerController(private val player: Player) : PlayerController(player) {
    override fun teleport(action: TeleportAction): Boolean {
        val message = when {
            FishingTrawler.inWreck(player.position) -> "Water and magic don't mix! You can't teleport whilst swimming."
            FishingTrawler.atSea(player.position) -> "You're too far from shore to teleport!"
            else -> return true
        }
        player.sendMessage(message)
        return false
    }
}

/**
 * Keeps the teleport restriction attached across both boat maps and the wreck, independently of crew membership.
 *
 * @author lare96
 */
object TrawlerAreaListener : PlayerAreaListener() {
    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(object : Locatable {
        override fun abs(): Position = Position(x, y)
        override fun getX(): Int = 1889
        override fun getY(): Int = 4825
        override fun contains(position: Position): Boolean =
            FishingTrawler.atSea(position) || FishingTrawler.inWreck(position)
    })

    override fun enter(plr: Player) {
        plr.controllers.register(TrawlerController(plr))
    }

    override fun exit(plr: Player) {
        if (plr.controllers.primary is TrawlerController) plr.controllers.unregister()
    }
}
