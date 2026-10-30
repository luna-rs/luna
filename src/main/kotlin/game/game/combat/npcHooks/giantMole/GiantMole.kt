package game.combat.npcHooks.giantMole

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.skill.firemaking.lightSource.LightSource
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.LocalProjectile
import io.luna.game.model.LocalSound
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.movement.wandering.DumbWanderingAction
import io.luna.game.model.mob.movement.wandering.WanderingAction
import io.luna.game.model.mob.movement.wandering.WanderingFrequency
import io.luna.game.model.area.Area
import java.util.concurrent.ThreadLocalRandom

/**
 * The Giant Mole under Falador Park. Once it's down to half its hitpoints, every attack on it has a chance to make it
 * burrow away and surface somewhere else in its lair, shaking off everyone who was fighting it.
 *
 * @author TheLining
 */
object GiantMole {

    /**
     * The Giant Mole's NPC id.
     */
    const val ID = 3340

    /**
     * Every attack on the mole at or below half its hitpoints makes it burrow with a chance of 1 in this.
     */
    const val BURROW_CHANCE = 4

    /**
     * The ticks between the mole starting to dig and coming back up somewhere else. It vanishes into the dirt partway
     * through its dig animation.
     */
    const val UNDERGROUND_TICKS = 2

    /**
     * A tick into a burrow, the dirt lands on the screens of the players beside the mole with a chance of 1 in this.
     */
    const val MUD_CHANCE = 4

    /**
     * The mole comes back up at least this many tiles from where it went down, so never where it was wandering.
     */
    const val MIN_SURFACING_DISTANCE = 6

    /**
     * How far around itself the mole wanders after surfacing.
     */
    const val WANDER_RADIUS = 5

    /**
     * The animations for digging down and coming back up.
     */
    val BURROW_DOWN = Animation(3314, AnimationPriority.HIGH)
    val BURROW_UP = Animation(3315, AnimationPriority.HIGH)

    /**
     * The dust kicked up around the hole the mole digs and comes out of, the holes themselves, and the balls of mud it
     * throws up.
     */
    const val MUD_CLOUD = 571
    const val MUD_HOLE = 572
    const val MUD_HOLE_UP = 573
    const val MUD_BALL = 570

    /**
     * How long the mud stays on a player's screen, in ticks.
     */
    const val MUD_SPLAT_TICKS = 2

    /**
     * The places the mole can come back up, each its south-west tile.
     */
    val SURFACING_SPOTS = listOf(
        Position(1739, 5220), Position(1775, 5232), Position(1737, 5208), Position(1778, 5207),
        Position(1753, 5197), Position(1778, 5187), Position(1741, 5177), Position(1758, 5179),
        Position(1746, 5170), Position(1775, 5173), Position(1778, 5164), Position(1760, 5163),
        Position(1753, 5150)
    )

    /**
     * Whether the mole is underground, between digging down and coming back up.
     */
    var Npc.burrowing by Attr.boolean()

    /**
     * Whether [damage], about to land on [mole], makes it burrow. Splashed spells never do, and neither does the hit
     * that kills it.
     */
    fun shouldBurrow(mole: Npc, damage: CombatDamage): Boolean {
        if (mole.burrowing || damage.amount.isEmpty) {
            return false
        }
        val healthAfter = mole.health - damage.rawAmount
        return healthAfter > 0 && healthAfter * 2 <= mole.totalHealth && rand().nextInt(BURROW_CHANCE) == 0
    }

    /**
     * Makes [mole] dig down, throwing up dirt that can land on the players beside it, and come back up at another
     * spot a couple of ticks later. Everyone fighting it loses track of it.
     */
    fun burrow(mole: Npc) {
        mole.burrowing = true
        mole.lock()
        mole.walking.clear()
        mole.interact(null)
        mole.combat.target = null
        loseAttackers(mole)

        // It dives head first, so the hole opens under its nose.
        val facing = Direction.NESW[rand().nextInt(Direction.NESW.size)]
        mole.face(centre(mole.position).translate(4, facing))
        val hole = holeTile(mole.position, facing)
        mole.animation(BURROW_DOWN)
        LocalSound.of(mole, Sound.MOLE_BURROW_DOWN).display()
        throwMudBalls(mole, hole)

        val spawnCount = mole.spawnCount
        val stillHere = { mole.spawnCount == spawnCount && mole.state == EntityState.ACTIVE && mole.isAlive }
        world.scheduleOnce(1) {
            if (stillHere()) {
                dust(mole, hole)
                graphic(mole, MUD_HOLE, hole)
                if (rand().nextInt(MUD_CHANCE) == 0) {
                    splatMud(mole)
                }
            }
        }
        world.scheduleOnce(UNDERGROUND_TICKS) {
            if (stillHere()) {
                surface(mole, nextSpot(mole.position, ThreadLocalRandom.current()))
            }
        }
    }

    /**
     * Brings [mole] back up at [spot], where it wanders from now on.
     */
    private fun surface(mole: Npc, spot: Position) {
        mole.move(spot)
        mole.animation(BURROW_UP)
        dust(mole, centre(spot))
        graphic(mole, MUD_HOLE_UP, centre(spot))
        LocalSound.of(mole, Sound.MOLE_BURROW_UP).display()
        mole.burrowing = false
        mole.unlock()
        loseAttackers(mole)
        mole.actions.getAll(WanderingAction::class.java).forEach { it.interrupt() }
        mole.actions.submit(DumbWanderingAction(mole, Area.of(spot, WANDER_RADIUS), WanderingFrequency.NORMAL))
    }

    /**
     * Picks where the mole comes back up: a surfacing spot at least [MIN_SURFACING_DISTANCE] tiles from where it went
     * down.
     */
    fun nextSpot(from: Position, random: java.util.Random): Position {
        val far = SURFACING_SPOTS.filter { it.computeLongestDistance(from) >= MIN_SURFACING_DISTANCE }
        val choices = far.ifEmpty { SURFACING_SPOTS }
        return choices[random.nextInt(choices.size)]
    }

    /**
     * Stops every player fighting [mole], so they have to find it again.
     */
    private fun loseAttackers(mole: Npc) {
        for (player in mole.localHumans) {
            if (player.combat.target == mole) {
                player.combat.target = null
                player.interact(null)
                player.walking.clear()
            }
            if (player.combat.autoRetaliateTarget == mole) {
                player.combat.autoRetaliateTarget = null
            }
        }
    }

    /**
     * Throws two or three balls of mud out of [hole] onto nearby tiles.
     */
    private fun throwMudBalls(mole: Npc, hole: Position) {
        repeat(2 + rand().nextInt(2)) {
            LocalProjectile.followPath(mole.context).setSourcePosition(hole)
                .setTargetPosition(hole.translate(rand().nextInt(9) - 4, rand().nextInt(9) - 4))
                .setId(MUD_BALL).setStartHeight(0).setEndHeight(0).setTiming(0, 60).setInitialSlope(16)
                .build().display()
        }
    }

    /**
     * Covers the screens of the players beside [mole] in mud, which puts out their candles, torches and oil lamps.
     */
    private fun splatMud(mole: Npc) {
        for (player in mole.localHumans) {
            if (isBeside(mole, player)) {
                MudSplatOverlay.show(player)
                for (light in LightSource.extinguish(player) { it.naked }) {
                    player.sendMessage("Your ${light.messageName} seems to have been extinguished by the mud.")
                }
            }
        }
    }

    /**
     * Kicks up dust on the 3x3 tiles around [tile].
     */
    private fun dust(mole: Npc, tile: Position) {
        for (dx in -1..1) {
            for (dy in -1..1) {
                graphic(mole, MUD_CLOUD, tile.translate(dx, dy))
            }
        }
    }

    /**
     * Shows graphic [id] on [tile] to everyone nearby.
     */
    private fun graphic(mole: Npc, id: Int, tile: Position) =
        LocalGraphic(mole.context, id, 0, 0, tile, ChunkUpdatableView.globalView()).display()

    /**
     * The tile in the middle of the edge that a mole at [position] dives into when facing [facing].
     */
    fun holeTile(position: Position, facing: Direction): Position = when (facing) {
        Direction.NORTH -> position.translate(1, 2)
        Direction.EAST -> position.translate(2, 1)
        Direction.WEST -> position.translate(0, 1)
        else -> position.translate(1, 0)
    }

    /**
     * Whether [player] stands within melee distance of [mole]'s body.
     */
    fun isBeside(mole: Mob, player: Player): Boolean {
        val dx = maxOf(mole.position.x - player.position.x, player.position.x - (mole.position.x + mole.size() - 1), 0)
        val dy = maxOf(mole.position.y - player.position.y, player.position.y - (mole.position.y + mole.size() - 1), 0)
        return mole.position.z == player.position.z && maxOf(dx, dy) <= 1
    }

    /**
     * The middle tile of a mole whose south-west tile is [position].
     */
    private fun centre(position: Position) = position.translate(1, 1)
}
