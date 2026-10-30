package game.content.blastFurnace

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * A stationary player operates one machine until interrupted or its prerequisites stop being satisfied.
 * Animation identifiers are cache-backed; cadence and operating XP are reconstruction parameters.
 *
 * @author lare96
 */
class BlastFurnaceWorkAction(player: Player, val role: Role) : Action<Player>(player, ActionType.WEAK, true, 1) {
    /**
     * Native manual stations from the revision 377 map and sequence pack.
     *
     * @author lare96
     */
    enum class Role(val position: Position, val animation: Int) {
        PUMP(BlastFurnace.PUMP, 2432), PEDALS(BlastFurnace.PEDALS, 2433)
    }
    private val origin = player.position
    override fun run(): Boolean {
        if (mob.position != origin || !BlastFurnace.present(mob) || !BlastFurnace.hasAccess(mob) ||
            !BlastFurnace.operatorLevel(mob, role) ||
            !mob.position.isWithinDistance(role.position, 4)) return true
        mob.animation(Animation(role.animation))
        return !BlastFurnace.work(mob, role)
    }
    override fun onFinished() {
        mob.animation(Animation(-1))
        BlastFurnace.release(mob, role)
    }
}
