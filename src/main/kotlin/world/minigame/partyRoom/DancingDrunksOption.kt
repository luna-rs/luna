package world.minigame.partyRoom

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import java.util.*

/**
 * Handles the lever option for the "Dancing Drunks" event.
 */
object DancingDrunksOption : PartyRoomOption(100_000, "Dancing Drunks") {

    /**
     * The NPC.
     */
    private const val DANCING_DRUNK_NPC = 3222

    /**
     * The base starting position.
     */
    private val BASE_STARTING_POS = Position(2740, 3468)

    /**
     * All the different dance animations that can occur.
     */
    private val DANCE_ANIMATIONS = listOf(866, 897).map { Animation(it) }

    /**
     * The bow animation.
     */
    private val BOW_ANIMATION = Animation(858)

    /**
     * The item that will be dropped by the drunks.
     */
    private const val DROP_ITEM = 1917

    override fun execute(plr: Player) {
        val npcs = mutableListOf<Npc>()
        repeat(6) {
            val pos = BASE_STARTING_POS.translate(-it, 0)
            val npc = Npc(ctx, DANCING_DRUNK_NPC, pos)
            npcs.add(world.addNpc(npc))
            npc.face(Direction.NORTH)
        }

        world.schedule(2, true) { task ->
            when (task.executionCounter) {
                0 -> npcs.forEach { it.animation(BOW_ANIMATION) }
                2 -> animate(npcs) { it.forceChat("We like to drink!") }
                4 -> animate(npcs) { it.forceChat("NO!") }
                6 -> animate(npcs) { it.forceChat("We LOVE to drink!") }
                8 -> animate(npcs) { it.forceChat("Do you like to drink as well?") }
                10 -> animate(npcs) { it.forceChat("Only time will tell!") }
                12 -> animate(npcs) { it.forceChat("It's time to party!") }
                14 -> npcs.forEach { it.animation(BOW_ANIMATION) }
                16 -> {
                    npcs.forEach {
                        world.addItem(GroundItem(ctx, DROP_ITEM, 1, it.position.translate(0, 1), ChunkUpdatableView.globalView()))
                        world.removeNpc(it)
                    }
                    PartyRoom.resetLeverOption()
                    task.cancel()
                }
            }
        }
    }

    /**
     * Applies an action to the dancing drunks.
     */
    private fun animate(npcs: List<Npc>, action: (Npc) -> Unit) {
        val nextAnimation = DANCE_ANIMATIONS.random()
        for (npc in npcs) {
            npc.animation(nextAnimation)
            action(npc)
        }
    }
}