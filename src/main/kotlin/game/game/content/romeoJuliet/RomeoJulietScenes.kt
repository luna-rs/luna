package game.content.romeoJuliet

import api.predef.ext.addNpc
import api.predef.ext.scheduleOnce
import game.content.romeoJuliet.RomeoJuliet.JULIET
import game.content.romeoJuliet.RomeoJuliet.JULIET_CRYPT
import game.content.romeoJuliet.RomeoJuliet.POTION
import game.content.romeoJuliet.RomeoJuliet.ROMEO
import game.content.romeoJuliet.RomeoJuliet.SPOKEN_APOTHECARY
import game.player.Jingles
import game.skill.magic.teleportSpells.TeleportAction
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.map.DynamicMapController
import io.luna.game.model.map.builder.DynamicMapBuilder
import io.luna.game.model.map.builder.DynamicMapPalette
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.Expression
import io.luna.net.msg.out.CameraTargetMessageWriter
import io.luna.net.msg.out.ResetCameraMessageWriter
import java.util.ArrayDeque
import java.util.function.Function

/**
 * Reconstructs the March 2006 scenes on private copies of the balcony and crypt from the revision-377 map.
 * A scene advances the story only after its dialogue is completed.
 *
 * @author lare96
 */
internal object RomeoJulietScenes {
    private const val DRAUL = 3324
    private const val PHILLIPA = 3325
    private const val DRINK = 3284
    private const val COLLAPSE = 3280

    private val balcony = Position(3158, 3425, 1)
    private val crypt = Position(2325, 4645)
    private val tomb = Position(2322, 4642)

    fun potion(player: Player) {
        if (RomeoJuliet.stage(player) == SPOKEN_APOTHECARY && player.inventory.contains(POTION)) {
            enter(player, balcony, player.position, true)
        }
    }

    fun finale(player: Player) {
        if (RomeoJuliet.stage(player) == JULIET_CRYPT) enter(player, crypt, crypt, false)
    }

    private fun enter(player: Player, anchor: Position, entry: Position, potion: Boolean) {
        if (player.dynamicMap != null || player.combat.inCombat() ||
            player.controllers.primary.javaClass != PlayerController::class.java) return
        val palette = DynamicMapPalette()
        for (plane in 0..anchor.z) palette.setChunkRadius(6, 6, plane, anchor.chunk, plane, 1, 1)
        val returnTile = player.position
        val map = DynamicMapBuilder(player.context).setPalette(anchor.chunk, palette)
            .setController(Function<Player, DynamicMapController> { Scene(it, returnTile, potion) }).build()
        map.create()
        try {
            if (!map.add(player, map.getInstancePosition(entry))) map.delete()
        } catch (failure: RuntimeException) {
            map.delete()
            throw failure
        }
    }

    /**
     * Keeps movement and interactions out of the private scene while allowing dialogue and logout.
     * The same return tile is used for ordinary exit and persistent recovery.
     *
     * @author lare96
     */
    private class Scene(player: Player, private val returnTile: Position, private val potion: Boolean) :
        DynamicMapController(player) {

        private var ticks = 0
        private val map get() = player.dynamicMap

        override fun enter(player: Player) {
            player.walking.clear()
        }

        override fun process() {
            if (!player.isAlive) {
                map.remove(player)
                return
            }
            when (++ticks) {
                2 -> if (potion) showPotion() else showCrypt()
                // Closing a scene or waiting two minutes returns the player to the hand-off.
                in 3..199 -> if (!player.overlays.has(DialogueInterface::class.java)) map.remove(player)
                200 -> map.remove(player)
            }
        }

        override fun event(event: ControllableEvent): Boolean = event is ButtonClickEvent && event.id == 2458

        override fun teleport(action: TeleportAction): Boolean = false

        override fun combat(victim: Mob): Boolean = false

        override fun exit(player: Player): Position {
            val instance = map
            player.overlays.closeWindows(false)
            player.queue(ResetCameraMessageWriter())
            player.world.scheduleOnce(1) { instance.delete() }
            return returnTile
        }

        override fun recovery(player: Player): Position = returnTile

        private fun actor(id: Int, at: Position): Npc =
            player.world.addNpc(Npc(player.context, id, map.getInstancePosition(at)))

        private fun camera(at: Position, focus: Position, height: Int) {
            player.queue(CameraTargetMessageWriter(map.getInstancePosition(at), height, 100, 100, false))
            player.queue(CameraTargetMessageWriter(map.getInstancePosition(focus), 60, 100, 100, true))
        }

        private fun showPotion() {
            val juliet = actor(JULIET, balcony)
            val phillipa = actor(PHILLIPA, Position(3160, 3425, 1))
            juliet.face(phillipa.position)
            phillipa.face(juliet.position)
            camera(Position(3161, 3422, 1), balcony, 250)

            player.newDialogue().npc(JULIET, "When I drink this, everyone must believe I'm dead.")
                .npc(PHILLIPA, "Leave that to me, cousin. I'll fetch your father.")
                .then {
                    it.music.playJingle(Jingles.CADAVA_POTION)
                    juliet.animation(Animation(DRINK))
                    it.newDialogue().npc(JULIET, "Here goes. Tell Romeo I'll be waiting in the crypt.")
                        .then { p -> showCollapse(p, juliet, phillipa) }.open()
                }.open()
        }

        private fun showCollapse(player: Player, juliet: Npc, phillipa: Npc) {
            juliet.animation(Animation(COLLAPSE))
            val draul = actor(DRAUL, Position(3163, 3425, 1))
            draul.face(juliet.position)
            draul.walking.addForcedStep(Direction.WEST)
            phillipa.face(draul.position)
            player.newDialogue()
                .npc(PHILLIPA, Expression.SAD, "Uncle! Come quickly. Something's happened to Juliet!")
                .npc(DRAUL, Expression.SAD, "Juliet! My daughter, what has happened to you?")
                .npc(PHILLIPA, "We should take her down to the family crypt.")
                .then {
                    map.remove(it)
                    if (RomeoJuliet.stage(it) == SPOKEN_APOTHECARY && it.inventory.remove(Item(POTION))) {
                        RomeoJuliet.advance(it, JULIET_CRYPT)
                    }
                }.open()
        }

        private fun showCrypt() {
            val romeo = actor(ROMEO, Position(2326, 4644))
            romeo.walking.addForcedStep(Direction.WEST)
            romeo.face(map.getInstancePosition(tomb))
            camera(Position(2327, 4646), tomb, 240)
            player.music.playJingle(Jingles.ROMEO_FALLS_IN_LOVE)
            player.newDialogue().player("There she is, Romeo. The potion will wear off.")
                .npc(ROMEO, Expression.SAD, "Oh, Juliet. You look quite dead to me.")
                .then { showPhillipa(romeo) }.open()
        }

        private fun showPhillipa(romeo: Npc) {
            val phillipa = actor(PHILLIPA, Position(2328, 4645))
            phillipa.walking.addForcedStep(Direction.WEST)
            phillipa.face(romeo.position)
            romeo.face(phillipa.position)
            camera(Position(2325, 4646), Position(2326, 4644), 460)
            player.newDialogue()
                .npc(PHILLIPA, "Romeo? I came to see how my cousin was doing.")
                .npc(ROMEO, Expression.HAPPY, "Phillipa! Why haven't we spent more time together?")
                .npc(PHILLIPA, "What about Juliet?")
                .npc(ROMEO, "Juliet? Who's Juliet? Come on, let's get out of this gloomy place.")
                .then {
                    leaveTogether(romeo, phillipa)
                    it.newDialogue().text("Romeo and Phillipa leave the crypt together.")
                        .then { p ->
                            map.remove(p)
                            RomeoJuliet.finish(p)
                        }.open()
                }.open()
        }

        private fun leaveTogether(romeo: Npc, phillipa: Npc) {
            for (actor in listOf(romeo, phillipa)) {
                val path = ArrayDeque<Position>()
                for (step in 1..4) path.add(actor.position.translate(step, 0))
                actor.walking.replaceForcedPath(path)
            }
        }
    }
}
