package engine.controllers

import api.attr.Attr
import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.Locatable
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerAreaListener
import io.luna.game.model.mob.overlay.WalkableInterface

/**
 * A [PlayerAreaListener] that manages wilderness-specific state while a player is inside the wilderness.
 *
 * @author lare96
 */
object WildernessLocatableController : PlayerAreaListener() {

    /**
     * The player's current wilderness level.
     *
     * This value should be {@code 0} whenever the player is outside the wilderness.
     */
    var Player.wildernessLevel by Attr.int()

    override fun enter(plr: Player) {
        plr.overlays.open(WalkableInterface(197))
        plr.contextMenu.show(OPTION_ATTACK)
        plr.controllers.register(WildernessController(plr))
    }

    override fun exit(plr: Player) {
        plr.contextMenu.hide(OPTION_ATTACK)
        plr.overlays.closeWalkable()
        plr.wildernessLevel = 0
        plr.combat.magic.teleBlock = 0
        plr.clearText(199)
        plr.controllers.unregister()
    }

    override fun computeLocatables(): ImmutableSet<Locatable> = ImmutableSet.of(
        Area.of(2944, 3519, 3392, 3966)
    )
}