package game.content.mosLeHarmless

import api.predef.*
import api.predef.ext.*
import engine.obj.LadderDestination
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.action.impl.ClimbAction
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Bill Teach's ship between Port Phasmatys and Mos Le'Harmless, which Cabin Fever opens (Jagex Knowledge Base,
 * 17 May 2006). He waits on board at both ports; in the Green Ghost and on the island he points players to the ship.
 */
object BillTeach {
    const val IN_PUB = 3155
    const val ON_ISLAND = 3159
    const val AT_PHASMATYS = 3157
    const val AT_MOS_LE_HARMLESS = 3160

    private const val FADE_OVERLAY = 11124
    private const val FADE_WIDGET = 11125
    private const val FADE_OUT = 1377
    private const val FADE_IN = 1378

    /**
     * Where the lift lands: on the other ship's deck, by its gangplank.
     */
    private val TO_MOS_LE_HARMLESS = Position(3684, 2950, 1)
    private val TO_PHASMATYS = Position(3712, 3496, 1)

    private fun lad(player: Player) = if (player.appearance.isMale) "lad" else "lass"

    private fun before(player: Player) {
        player.newDialogue().npc(IN_PUB, "Mumblelandlubbermumblemumble...").open()
    }

    fun ashore(player: Player, npc: Npc) {
        if (!QuestGates.passes(player, Quest.CABIN_FEVER)) {
            before(player)
            return
        }
        player.newDialogue().npc(npc.id, "If yer after a lift go to the ship and I'll take ye where ye want to go.").open()
    }

    fun aboard(player: Player, npc: Npc) {
        if (!QuestGates.passes(player, Quest.CABIN_FEVER)) {
            before(player)
            return
        }
        val toIsland = npc.id == AT_PHASMATYS
        player.newDialogue().npc(npc.id, "I suppose ye'll be wantin' a lift while yer here, right ${lad(player)}?")
            .options("Yes Cap'n, I'd like a lift.", {
                val reply = if (toIsland) "No, I should be gettin' back anyway." else "Nothin' I won't put on hold."
                it.newDialogue().player("Yes Cap'n, I'd like a lift. If you aren't busy.")
                    .npc(npc.id, "$reply Come on ${lad(it)}, let's be off.")
                    .then { p -> sail(p, if (toIsland) TO_MOS_LE_HARMLESS else TO_PHASMATYS) }.open()
            }, "No thanks, Cap'n.", {
                it.newDialogue().player("No thanks, Cap'n.").npc(npc.id, "Well, come see me if ye ever do.").open()
            }).open()
    }

    private fun fade(player: Player, sequence: Int) {
        player.queue(WidgetAnimationMessageWriter(FADE_WIDGET, -1))
        player.overlays.open(WalkableInterface(FADE_OVERLAY))
        player.queue(WidgetItemModelMessageWriter(FADE_WIDGET, 1000, 4032))
        player.queue(WidgetAnimationMessageWriter(FADE_WIDGET, sequence))
    }

    private fun sail(player: Player, destination: Position) {
        if (player.isLocked || !LadderDestination.canLand(destination)) return
        player.overlays.closeWindows()
        player.lock()
        fade(player, FADE_OUT)
        world.scheduleOnce(4) {
            if (player.state != EntityState.ACTIVE) return@scheduleOnce
            player.move(destination)
            fade(player, FADE_IN)
            world.scheduleOnce(2) {
                if (player.state != EntityState.ACTIVE) return@scheduleOnce
                player.overlays.closeWalkable()
                player.unlock()
            }
        }
    }

    /**
     * The island's bamboo ladders and the ladders on both of Bill's ships. The same ids elsewhere are left alone.
     */
    fun ladders(position: Position) = position.x in 3640..3720 && (position.y in 2940..3010 || position.y in 3490..3505)

    fun climb(player: Player, ladder: GameObject, up: Boolean) {
        if (!ladders(ladder.position)) return
        val destination = LadderDestination.destination(ladder, player.position, up)
        if (destination == null) {
            player.sendMessage("Nothing interesting happens.")
            return
        }
        player.submitAction(ClimbAction(player, destination, Direction.between(player.position, ladder.position),
            if (up) "You climb up the ladder." else "You climb down the ladder."))
    }
}
