package game.content.fightPit

import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.event.impl.WalkingEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.WidgetVisibilityMessageWriter
import java.util.OptionalInt

/**
 * Looks after a player in the Fight Pit's waiting room or in the pit.
 *
 * @author TheLining
 */
class FightPitController(private val plr: Player) : PlayerController(plr) {

    companion object {

        /**
         * Every sidebar the orb's sidebar replaces.
         */
        val SIDEBARS = TabIndex.values().filter { it != TabIndex.UNUSED }
    }

    /**
     * The foes count last shown, or -1.
     */
    private var foes = -1

    /**
     * Whether the foes line is showing.
     */
    private var foesShown = false

    /**
     * Where the player looked into the orb from, while they're watching the pit through it.
     */
    private var viewingFrom: Position? = null

    /**
     * The tabs put away while viewing.
     */
    private var tabs: Map<TabIndex, OptionalInt> = emptyMap()

    /**
     * Whether the player's model was hidden before they looked into the orb.
     */
    private var wasHidden = false

    /**
     * Whether the player is watching the pit through the orb.
     */
    val viewing: Boolean
        get() = viewingFrom != null

    // Watching through the orb: no collision, combat or damage.
    override fun isSpectator() = viewing

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
        if (viewing || FightPit.PIT.contains(plr.position)) {
            plr.sendMessage("You can't teleport out of the Fight Pit.")
            return false
        }
        return true
    }

    override fun combat(victim: Mob): Boolean = when {
        viewing -> false
        victim is Player -> FightPitRound.canFight(plr, victim)
        victim is Npc -> FightPit.PIT.contains(plr.position)
        else -> false
    }

    // A viewer can't walk or use anything in the pit they're watching.
    override fun event(event: ControllableEvent): Boolean =
        !viewing || (event !is WalkingEvent && event !is InteractableEvent)

    /**
     * Looks into the orb: the orb's sidebar takes the place of the tabs, and the player, hidden, watches the pit from
     * its centre.
     */
    fun lookInto() {
        if (viewing || !plr.isAlive || plr.isLocked || !FightPit.WAITING_ROOM.contains(plr.position)) return
        // The collision map stops following a spectator, so the player leaves it first.
        plr.world.collisionManager.updateEntity(plr, true)
        viewingFrom = plr.position
        plr.overlays.closeWindows()
        plr.walking.clear()
        tabs = SIDEBARS.associateWith { plr.tabs.get(it) }
        SIDEBARS.forEach { plr.tabs.clear(it) }
        plr.tabs.set(TabIndex.INVENTORY, FightPitOrb.SIDEBAR)
        plr.tabs.show(TabIndex.INVENTORY)
        wasHidden = plr.isModelHidden
        plr.isModelHidden = true
        plr.move(FightPitOrb.CENTRE)
    }

    /**
     * Watches the pit from the view behind [button].
     */
    fun view(button: Int) {
        if (viewing) FightPitOrb.VIEWS[button]?.let { plr.move(it) }
    }

    /**
     * Stops viewing: the player is back where they looked into the orb, with their tabs.
     */
    fun stopViewing() {
        val from = viewingFrom ?: return
        plr.move(from)
        viewingFrom = null
        plr.world.collisionManager.updateEntity(plr, false)
        for ((tab, id) in tabs) {
            if (id.isPresent) plr.tabs.set(tab, id.asInt) else plr.tabs.clear(tab)
        }
        plr.tabs.show(TabIndex.INVENTORY)
        plr.isModelHidden = wasHidden
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
            viewing -> if (round) standing.size else null
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
