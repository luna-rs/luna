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
 *
 * @author TheLining
 */
object BillTeach {
    const val IN_PUB = 3155
    const val ON_ISLAND = 3159
    const val ACCEPT = "Yes Cap'n, I'd like a lift."
    const val DECLINE = "No thanks, Cap'n."
    const val ACCEPT_REPLY = "Yes Cap'n, I'd like a lift. If you aren't busy."
    const val SAIL_TICKS = 6

    private const val FADE_OVERLAY = 11124
    private const val FADE_WIDGET = 11125
    private const val FADE_OUT = 1377
    private const val FADE_IN = 1378

    /**
     * Bill's actor, landing and gangplanks at each port.
     *
     * @author lare96
     */
    enum class Port(val npcId: Int, val deck: Position, val boardPlank: Int, val leavePlank: Int) {
        PHASMATYS(3157, Position(3712, 3496, 1), 11209, 11210),
        MOS_LE_HARMLESS(3160, Position(3684, 2950, 1), 11211, 11212);

        val other: Port
            get() = if (this == PHASMATYS) MOS_LE_HARMLESS else PHASMATYS
    }

    fun canSail(player: Player) = QuestGates.passes(player, Quest.CABIN_FEVER)

    private fun lad(player: Player) = if (player.appearance.isMale) "lad" else "lass"

    fun question(player: Player) = "I suppose ye'll be wantin' a lift while yer here, right ${lad(player)}?"

    fun reply(player: Player, port: Port): String {
        val answer = if (port == Port.PHASMATYS) "No, I should be gettin' back anyway." else "Nothin' I won't put on hold."
        return "$answer Come on ${lad(player)}, let's be off."
    }

    private fun before(player: Player) {
        player.newDialogue().npc(IN_PUB, "Mumblelandlubbermumblemumble...").open()
    }

    fun ashore(player: Player, npc: Npc) {
        if (!canSail(player)) {
            before(player)
            return
        }
        player.newDialogue().npc(npc.id, "If yer after a lift go to the ship and I'll take ye where ye want to go.").open()
    }

    fun aboard(player: Player, port: Port) {
        if (!canSail(player)) {
            before(player)
            return
        }
        player.newDialogue().npc(port.npcId, question(player))
            .options(ACCEPT, {
                it.newDialogue().player(ACCEPT_REPLY).npc(port.npcId, reply(it, port))
                    .then { p -> sail(p, port.other.deck) }.open()
            }, DECLINE, {
                it.newDialogue().player(DECLINE).npc(port.npcId, "Well, come see me if ye ever do.").open()
            }).open()
    }

    private fun fade(player: Player, sequence: Int) {
        player.queue(WidgetAnimationMessageWriter(FADE_WIDGET, -1))
        player.overlays.open(WalkableInterface(FADE_OVERLAY))
        player.queue(WidgetItemModelMessageWriter(FADE_WIDGET, 1000, 4032))
        player.queue(WidgetAnimationMessageWriter(FADE_WIDGET, sequence))
    }

    private fun sail(player: Player, destination: Position) {
        if (player.isLocked || !canSail(player) || !LadderDestination.canLand(destination)) return
        player.overlays.closeWindows()
        player.lock()
        fade(player, FADE_OUT)
        world.scheduleOnce(SAIL_TICKS - 2) {
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

    val ladderDirections = mapOf(4772 to true, 4778 to false, 11289 to true, 11290 to false, 11308 to true, 11309 to false)

    /**
     * The island's bamboo ladders and the ladders on both of Bill's ships.
     */
    fun ladderDirection(ladder: GameObject): Boolean? {
        val direction = ladderDirections[ladder.id] ?: return null
        val position = ladder.position
        return direction.takeIf { position.x in 3640..3720 && (position.y in 2940..3010 || position.y in 3490..3505) }
    }

    fun climb(player: Player, ladder: GameObject) {
        val up = ladderDirection(ladder) ?: return
        val destination = LadderDestination.destination(ladder, player.position, up)
        if (destination == null) {
            player.sendMessage("Nothing interesting happens.")
            return
        }
        player.submitAction(ClimbAction(player, destination, Direction.between(player.position, ladder.position),
            if (up) "You climb up the ladder." else "You climb down the ladder."))
    }
}
