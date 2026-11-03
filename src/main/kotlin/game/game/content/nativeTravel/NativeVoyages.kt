package game.content.nativeTravel

import api.predef.ext.*
import engine.obj.LadderDestination
import game.content.sailing.Sailing
import game.player.Jingles
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import java.util.OptionalInt

/**
 * Shore journeys through the native sailors and Pirate Pete. The 377 map, widget and sequence assets are
 * preserved; dialogue, safe shore landings and unrecovered journey delays are deliberate reconstructions.
 * Both menu options share admission and travel, while bots use the ordinary conversation.
 *
 * @author lare96
 */
object NativeVoyages {
    const val AGREE = "Yes please."
    const val DECLINE = "Not right now."
    const val JOURNEY_INTERFACE = 11092
    const val SHIP_WIDGET = 11099
    private const val FADE_OVERLAY = 11124
    private const val FADE_WIDGET = 11125

    /**
     * Departure anchors identify the native actors, including the island Pete's upper walkway.
     * Every destination is a nearby walkable tile checked against the selected 377 cache.
     *
     * @author lare96
     */
    enum class Route(val npcId: Int, val departure: Position, val destination: Position,
                     val ticks: Int, val question: String, val shipSequence: Int = -1) {
        TO_MISCELLANIA(1304, Position(2629, 3693), Position(2581, 3846), 7,
            "Would you like to sail to Miscellania? There's no charge.", 1372),
        TO_RELLEKKA(1385, Position(2581, 3847), Position(2629, 3692), 5,
            "Would you like to sail back to Rellekka? There's no charge.", 1373),
        TO_BRAINDEATH(2825, Position(3680, 3537), Position(2163, 5115, 1), 8,
            "Would you like to travel to Braindeath Island? There's no charge."),
        FROM_BRAINDEATH(2826, Position(2162, 5115, 1), Position(3680, 3536), 8,
            "Would you like to travel back to the mainland? There's no charge.")
    }

    fun route(npc: Npc): Route? = Route.entries.firstOrNull {
        npc.id == it.npcId && npc.position.z == it.departure.z && npc.position.isWithinDistance(it.departure, 8)
    }

    /**
     * Offers the reconstructed journey without reserving admission or moving the passenger on cancellation.
     */
    fun talk(player: Player, npc: Npc) {
        val route = route(npc) ?: return
        if (!canBoard(player, npc, route)) return
        var answered = false
        player.newDialogue().npc(npc.id, route.question).options(AGREE, {
            if (!answered) {
                answered = true
                it.overlays.closeWindows()
                travel(it, npc)
            }
        }, DECLINE, { it.overlays.closeWindows() }).open()
    }

    /**
     * Starts exactly the same journey for Talk-to acceptance and the client Travel option.
     */
    fun travel(player: Player, npc: Npc): Boolean {
        val route = route(npc) ?: return false
        if (!canBoard(player, npc, route) || !LadderDestination.canLand(route.destination)) return false
        // TODO: Check Fremennik Trials for Miscellania when its quest progress is implemented.
        // TODO: Check Rum Deal progress and its quest-derived Slayer admission when implemented.
        // The temporary Rum Deal waiver does not change Fever Spider combat requirements.
        val journey = Journey(player, npc, route)
        player.submitAction(journey)
        return !journey.isFinished
    }

    private fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && !player.walking.isLocked && !player.status.isImmobilized() &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun canBoard(player: Player, npc: Npc, route: Route): Boolean = ready(player) && route(npc) == route &&
        npc.state == EntityState.ACTIVE && npc.isAlive &&
        player.position.z == npc.position.z && player.position.isWithinDistance(npc.position, 3) &&
        player.world.collisionManager.reached(player, npc, InteractionPolicy.STANDARD_SIZE)

    /**
     * A soft queue entry owns a hard passenger lock, so modal sailing maps remain open while time advances.
     * Normal disconnects leave this action running until arrival; removal clears it and only performs cleanup.
     * Keeping the passenger at the origin until landing also preserves the origin on forced logout before arrival.
     *
     * @author lare96
     */
    internal class Journey(player: Player, private val npc: Npc, val route: Route) :
        Action<Player>(player, ActionType.SOFT, false, 1), DeathInterruptible {
        private val origin = player.position
        private var ownsLock = false
        private var elapsed = 0
        private var arrived = false
        private var map: StandardInterface? = null
        private var fade: WalkableInterface? = null
        private var tabs: Map<TabIndex, OptionalInt> = emptyMap()

        override fun onSubmit() {
            if (!canBoard(mob, npc, route)) { complete(); return }
            mob.overlays.closeWindows()
            mob.walking.clear()
            mob.lock()
            ownsLock = true
            if (route.shipSequence >= 0) {
                tabs = Sailing.HIDDEN_TABS.associateWith { mob.tabs.get(it) }
                map = StandardInterface(JOURNEY_INTERFACE).also { mob.overlays.open(it) }
                mob.queue(WidgetAnimationMessageWriter(SHIP_WIDGET, -1))
                mob.queue(WidgetAnimationMessageWriter(SHIP_WIDGET, route.shipSequence))
                Sailing.HIDDEN_TABS.forEach { mob.tabs.clear(it) }
                mob.playJingle(Jingles.SAILING_JOURNEY)
            } else {
                // The original Pete sequence is unrecovered: these are native cosmetic punch/unconscious poses.
                npc.animation(Animation(422))
                mob.animation(Animation(838))
                fade(1377)
                mob.sendMessage("Pirate Pete knocks you unconscious.")
            }
        }

        override fun run(): Boolean {
            if (!ownsLock || !stillTravelling()) return true
            ++elapsed
            if (arrived) return elapsed >= route.ticks
            val landingTick = if (route.shipSequence < 0) route.ticks - 2 else route.ticks
            if (elapsed < landingTick) return false
            if (!LadderDestination.canLand(route.destination)) return true
            if (map != null && mob.overlays.getOverlay(StandardInterface::class.java) === map) mob.overlays.closeWindows()
            mob.move(route.destination)
            if (route.shipSequence < 0) {
                mob.animation(Animation.CANCEL)
                fade(1378)
                mob.sendMessage("You wake up beside Pirate Pete.")
                // Allow the native fade-in to finish while retaining the same owned lock.
                arrived = true
                return false
            }
            return true
        }

        private fun stillTravelling(): Boolean = mob.state == EntityState.ACTIVE && mob.isAlive &&
            mob.isLocked && mob.position == (if (arrived) route.destination else origin) &&
            mob.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

        private fun fade(sequence: Int) {
            mob.queue(WidgetAnimationMessageWriter(FADE_WIDGET, -1))
            fade = WalkableInterface(FADE_OVERLAY).also { mob.overlays.open(it) }
            mob.queue(WidgetItemModelMessageWriter(FADE_WIDGET, 1000, 4032))
            mob.queue(WidgetAnimationMessageWriter(FADE_WIDGET, sequence))
        }

        override fun onFinished() {
            if (!ownsLock) return
            ownsLock = false
            if (map != null && mob.overlays.getOverlay(StandardInterface::class.java) === map) mob.overlays.closeWindows()
            if (fade != null && mob.overlays.getOverlay(WalkableInterface::class.java) === fade) mob.overlays.closeWalkable()
            for ((tab, id) in tabs) if (id.isPresent) mob.tabs.set(tab, id.asInt) else mob.tabs.clear(tab)
            if (route.shipSequence < 0) mob.animation(Animation.CANCEL)
            mob.unlock()
        }
    }
}
