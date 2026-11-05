package game.content.fightPit

import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * Looks after a player in the Fight Pit's waiting room or in the pit.
 *
 * @author TheLining
 */
class FightPitController(private val plr: Player) : PlayerController(plr) {

    /**
     * The foes count last shown, or -1.
     */
    private var foes = -1

    /**
     * Whether the foes line is showing.
     */
    private var foesShown = false

    override fun register() {
        FightPitRound.joined(plr)
        FightPit.showOverlay(plr)
    }

    override fun unregister() {
        FightPitRound.departed(plr)
        FightPit.hideOverlay(plr)
    }

    override fun process() {
        // A Wilderness skull running out would otherwise take the champion's red skull with it.
        if (plr === FightPitRound.champion && plr.skullIcon != SkullIcon.RED) plr.skullIcon = SkullIcon.RED
    }

    override fun move() {
        if (!FightPit.inside(plr.position)) plr.controllers.unregister()
    }

    // A player who logs out anywhere in the Fight Pit comes back outside the north door.
    override fun getPersistencePosition(): Position = FightPit.OUTSIDE

    override fun beforeLogout() {
        // Logging out counts as walking out; a champion is paid on their next login.
        FightPitRound.walkedOut(plr, false)
    }

    override fun teleport(action: TeleportAction): Boolean {
        if (FightPit.PIT.contains(plr.position)) {
            plr.sendMessage("You can't teleport out of the Fight Pit.")
            return false
        }
        return true
    }

    override fun combat(victim: Mob): Boolean = when (victim) {
        is Player -> FightPitRound.canFight(plr, victim)
        is Npc -> FightPit.PIT.contains(plr.position)
        else -> false
    }

    // A fallen fighter is out of the round before their Retribution goes off.
    override fun retribution(victim: Mob): Boolean = victim is Player && FightPitRound.fighting(victim) &&
        FightPitRound.phase == FightPitRound.Phase.FIGHT && FightPit.PIT.contains(plr.position)

    /**
     * Brings the overlay up to date. In the pit a fighter or the champion sees the foes left; in the waiting room,
     * during a round, the players left in the pit.
     */
    fun refresh() {
        plr.sendText("Current Champion: ${FightPit.championName}", FightPit.CHAMPION_TEXT)
        val standing = FightPitRound.standing()
        val round = FightPitRound.phase == FightPitRound.Phase.SIGNAL ||
            FightPitRound.phase == FightPitRound.Phase.FIGHT
        val count = when {
            FightPit.PIT.contains(plr.position) ->
                if (plr in standing || plr === FightPitRound.champion) standing.count { it !== plr } else null
            round -> standing.size
            else -> null
        }
        if ((count != null) != foesShown) {
            foesShown = count != null
            plr.queue(WidgetVisibilityMessageWriter(FightPit.FOES_LINE, !foesShown))
        }
        if (count != null && count != foes) {
            foes = count
            plr.sendVarp(Varp(FightPit.FOES_VARP, count))
        }
    }
}
