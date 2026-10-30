package game.combat.npcHooks.tzhaar

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.task.Task

/**
 * TzTok-Jad's Yt-HurKot healers.
 *
 * When a hit leaves Jad at half health or less, four healers appear around him. Each walks up to him and heals him
 * until the player hits it; from then on it fights the player instead. A new set only comes if the healers brought
 * Jad back to full health first.
 */
object JadHealers {

    const val HEALER = 2746
    const val COUNT = 4

    /** How far from Jad's body the healers may appear, in tiles. */
    const val SPAWN_RADIUS = 5

    const val HEAL_AMOUNT = 5
    const val HEAL_INTERVAL = 4
    const val HEAL_ANIMATION = 2639
    const val HEAL_GRAPHIC = 444

    /** Ticks the healers stay after Jad dies when nothing else removes them. */
    const val REMOVE_AFTER_DEATH = 5

    /**
     * Picks the tiles new healers appear on. The default picks free tiles around Jad; a minigame can replace it.
     */
    var spawnTiles: (jad: Npc, count: Int) -> List<Position> = ::tilesAroundJad

    /** The healer state of each Jad currently fighting, keyed by the Jad npc. */
    private val encounters = HashMap<Npc, Encounter>()

    /**
     * A Yt-HurKot belonging to one Jad. It never respawns and only fights once the player has hit it.
     */
    class YtHurKot(val jad: Npc, position: Position) : Npc(jad.context, HEALER, position) {

        /** Set once the player hits this healer; it stops healing for good. */
        var provoked = false

        /** The player who hit it, whom it keeps chasing. */
        var foe: Player? = null

        /** Ticks until this healer may heal again. */
        var healCooldown = 0

        init {
            respawnTicks = -1
            combat.retreatPolicy = RetreatPolicy.NONE
        }

        override fun getAggressionProfile(): NpcAggressionProfile? = null
    }

    private class Encounter(val jad: Npc) {
        val healers = ArrayList<YtHurKot>()

        /** True while the next drop to half health brings healers. */
        var armed = true

        /** The player Jad is fighting, who hears the heal sound. */
        var player: Player? = null

        var task: Task? = null
    }

    /**
     * Called when a hit is about to land on Jad. Spawns healers if it leaves him at half health or less.
     */
    fun onJadHit(jad: Npc, attacker: Mob, amount: Int) {
        val encounter = encounters.getOrPut(jad) { start(jad) }
        if (attacker is Player) encounter.player = attacker
        val remaining = jad.health - amount
        if (encounter.armed && remaining > 0 && remaining <= jad.totalHealth / 2) {
            encounter.armed = false
            spawn(encounter)
        }
    }

    /**
     * Called when a hit is about to land on a healer: the player has drawn it away from Jad.
     */
    fun onHealerHit(healer: Npc, attacker: Mob, amount: Int) {
        val player = attacker as? Player ?: return
        if (healer is YtHurKot) {
            healer.provoked = true
            healer.foe = player
        }
        if (amount < healer.health) player.playSound(Sound.LIZARD_CLERIC_HIT)
    }

    /**
     * Removes every healer of [jad] at once and forgets him. For a minigame ending its run.
     */
    fun clear(jad: Npc) {
        val encounter = encounters.remove(jad) ?: return
        encounter.task?.cancel()
        encounter.healers.forEach { if (it.state == EntityState.ACTIVE) jad.world.removeNpc(it) }
    }

    private fun start(jad: Npc): Encounter {
        val encounter = Encounter(jad)
        jad.world.schedule(1) {
            encounter.task = it
            tick(encounter)
        }
        return encounter
    }

    private fun spawn(encounter: Encounter) {
        val jad = encounter.jad
        encounter.healers.removeIf { !it.isAlive || it.state != EntityState.ACTIVE }
        val missing = COUNT - encounter.healers.size
        if (missing <= 0) return
        for (tile in spawnTiles(jad, missing)) {
            val healer = YtHurKot(jad, tile)
            if (jad.world.npcs.add(healer)) encounter.healers += healer
        }
    }

    private fun tick(encounter: Encounter) {
        val jad = encounter.jad
        if (!jad.isAlive || jad.state != EntityState.ACTIVE) {
            encounters.remove(jad)
            encounter.task?.cancel()
            val world = jad.world
            val left = encounter.healers.toList()
            world.scheduleOnce(REMOVE_AFTER_DEATH) {
                left.forEach { if (it.state == EntityState.ACTIVE) world.removeNpc(it) }
            }
            return
        }
        for (healer in encounter.healers) {
            if (!healer.isAlive || healer.state != EntityState.ACTIVE) continue
            if (healer.provoked) {
                chase(healer)
                continue
            }
            if (healer.healCooldown > 0) healer.healCooldown--
            if (!touches(healer, jad)) {
                healer.navigator.navigate(jad, false, false)
                continue
            }
            if (healer.healCooldown > 0) continue
            healer.healCooldown = HEAL_INTERVAL
            healer.animation(Animation(HEAL_ANIMATION))
            healer.graphic(Graphic(HEAL_GRAPHIC))
            encounter.player?.playSound(Sound.SELFHEAL)
            jad.health = (jad.health + HEAL_AMOUNT).coerceAtMost(jad.totalHealth)
            if (jad.health == jad.totalHealth) encounter.armed = true
        }
    }

    /**
     * Sends a provoked healer back after the player who hit it once its combat has ended. Combat gives up on a
     * target it can't reach, and a healer stuck behind another one would otherwise stand idle for the rest of the
     * fight.
     */
    private fun chase(healer: YtHurKot) {
        val foe = healer.foe ?: return
        if (!foe.isAlive || foe.state != EntityState.ACTIVE) return
        if (healer.combat.target == foe && healer.actions.contains(CombatAction::class.java)) return
        if (!healer.position.isWithinDistance(foe.position, Position.VIEWING_DISTANCE)) return
        healer.combat.attack(foe)
    }

    /**
     * True if [mob] stands on a tile touching [jad]'s body, diagonals included.
     */
    fun touches(mob: Mob, jad: Npc): Boolean = gap(mob, jad) == 1

    /**
     * The number of tiles between two mobs' bodies along the larger axis: 0 when they overlap, 1 when they touch.
     */
    fun gap(a: Mob, b: Mob): Int {
        val dx = maxOf(0, b.position.x - (a.position.x + a.size() - 1), a.position.x - (b.position.x + b.size() - 1))
        val dy = maxOf(0, b.position.y - (a.position.y + a.size() - 1), a.position.y - (b.position.y + b.size() - 1))
        return maxOf(dx, dy)
    }

    private fun tilesAroundJad(jad: Npc, count: Int): List<Position> {
        val world = jad.world
        val base = jad.position
        val size = jad.size()
        val tiles = ArrayList<Position>()
        for (x in base.x - SPAWN_RADIUS until base.x + size + SPAWN_RADIUS) {
            for (y in base.y - SPAWN_RADIUS until base.y + size + SPAWN_RADIUS) {
                val inside = x in base.x until base.x + size && y in base.y until base.y + size
                val tile = Position(x, y, base.z)
                if (!inside && !world.collisionManager.isBlocked(tile, true)) tiles += tile
            }
        }
        tiles.shuffle(rand())
        return tiles.take(count)
    }
}
