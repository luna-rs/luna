package game.skill.runecrafting.essenceMine

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.zone.SubZone
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.teleOther.NpcTeleOtherAction
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Teleports players to the Rune Essence mine, and back to whoever sent them.
 *
 * @author TheLining
 */
object EssenceMine {

    /**
     * The mine's exit portal.
     */
    const val PORTAL = 2492

    /**
     * How far from its wizard's exit tile a portal may land a player.
     */
    private const val EXIT_RADIUS = 2

    /**
     * The wizard who last sent this player to the mine, so its portals return the player there.
     */
    private var Player.essenceMineExit: EssenceMineWizard? by Attr.nullableObj(EssenceMineWizard::class)
        .persist("essence_mine_exit")

    /**
     * Stores this npc's currently active tele-other action.
     *
     * Players asking to go to the same place are queued into the running action instead of starting overlapping
     * teleport sequences on the same npc.
     */
    private var Npc.teleOtherAction by Attr.nullableObj(NpcTeleOtherAction::class)

    /**
     * Has [wizard] teleport [plr] to the Rune Essence mine.
     *
     * @param wizard The npc performing the teleport.
     * @param plr The player requesting transport to the Rune Essence mine.
     */
    fun teleport(wizard: Npc, plr: Player) {
        val type = EssenceMineWizard.forNpc(wizard.id) ?: return
        // TODO Rune Mysteries: only players who have completed it may be teleported. The rest get "You need to have
        //  completed the Rune Mysteries Quest to use this feature."
        plr.essenceMineExit = type
        teleOther(wizard, plr, SubZone.ESSENCE_MINE.inside) {
            NpcTeleOtherAction(wizard, plr, SubZone.ESSENCE_MINE.inside, animate = type.animates)
        }
    }

    /**
     * Has [npc] teleport [plr] to [destination].
     *
     * If [npc] is already teleporting players to [destination], [plr] is added to that action's request queue.
     * Otherwise, [newAction] is submitted.
     *
     * @param npc The npc performing the teleport.
     * @param plr The player requesting the teleport.
     * @param destination Where [plr] is going.
     * @param newAction Creates the action if one has to be started.
     */
    fun teleOther(npc: Npc, plr: Player, destination: Position, newAction: () -> NpcTeleOtherAction) {
        val action = npc.teleOtherAction
        if (action == null || action.isFinished || action.destination != destination) {
            val nextAction = newAction()
            npc.teleOtherAction = nextAction
            npc.submitAction(nextAction)
        } else {
            action.addRequest(plr)
        }
    }

    /**
     * Reads [plr]'s saved exit at login. Only attributes read during a session are saved again, so a session that
     * never uses a portal would otherwise lose it.
     *
     * @param plr The player logging in.
     */
    fun login(plr: Player) {
        plr.essenceMineExit
    }

    /**
     * Returns [plr] through a mine portal to the wizard who sent them.
     *
     * @param plr The player leaving the mine.
     */
    fun leave(plr: Player) {
        val wizard = plr.essenceMineExit ?: EssenceMineWizard.AUBURY
        plr.move(findLandingTile(wizard.exit, EXIT_RADIUS))
    }
}
