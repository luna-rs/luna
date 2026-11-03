package game.obj.doors

import api.predef.ext.*
import game.item.greegree.Greegree
import game.player.Sound
import game.skill.agility.AgilityApproach
import game.skill.agility.walkStraight
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import java.util.IdentityHashMap

/**
 * Ape Atoll's ravine gate, following Lost City's Monkey Madness reconstruction and the native 377 placements.
 * Both directions require an actual greegree disguise. The opened leaves last five ticks and only admitted
 * passengers receive forced steps; invisible walls keep an open gate from granting another player admission.
 *
 * @author lare96
 */
object BambooGate {
    val IDS = setOf(4787, 4788)
    val POLICY = InteractionPolicy(InteractionType.DISTANCE, 2)
    const val OPEN_TICKS = 5
    const val CROSSING_TICKS = 3
    private val openings = IdentityHashMap<World, Opening>()

    /**
     * A directed three-tile passage through one native leaf, with separate north and south approach tiles.
     *
     * @author lare96
     */
    data class Passage(val id: Int, val position: Position, val direction: ObjectDirection,
                       val from: Position, val to: Position)

    val PASSAGES = buildList {
        for ((id, x, direction) in listOf(Triple(4787, 2720, ObjectDirection.WEST),
                                         Triple(4788, 2721, ObjectDirection.EAST))) {
            val position = Position(if (id == 4787) 2719 else 2721, 2766)
            add(Passage(id, position, direction, Position(x, 2765), Position(x, 2768)))
            add(Passage(id, position, direction, Position(x, 2767), Position(x, 2764)))
        }
    }

    /**
     * Whether this world's pair is owned by a passage whose native opening has not yet expired.
     */
    fun isBusy(world: World) = openings.containsKey(world)

    /**
     * Approaches the clicked leaf from the player's current side and rechecks the disguise at the gate.
     */
    fun use(player: Player, obj: GameObject) {
        if (!ready(player) || !validObject(player, obj)) return
        if (!Greegree.disguises(player)) {
            player.sendMessage("The gate is too heavy to move!")
            return
        }
        val passage = PASSAGES.firstOrNull {
            it.id == obj.id && it.position == obj.position && it.direction == obj.direction &&
                if (it.from.y < it.to.y) player.y < 2766 else player.y > 2766
        } ?: return
        if (player.position.computeLongestDistance(passage.from) > AgilityApproach.RADIUS) return
        val begin = begin@ {
            if (!ready(player) || player.position != passage.from || !validObject(player, obj)) return@begin
            if (!Greegree.disguises(player)) {
                player.sendMessage("The gate is too heavy to move!")
                return@begin
            }
            if (isBusy(player.world)) return@begin
            player.submitAction(PassageAction(player, PassageState(player, passage)))
        }
        if (player.position == passage.from) begin() else {
            player.submitAction(AgilityApproach(player, passage.from) { begin() })
        }
    }

    /**
     * Owns both original leaves and only the replacements created by this particular opening.
     *
     * @author lare96
     */
    internal class Opening(val closed: List<GameObject>, val opened: List<GameObject>, val blockers: List<GameObject>)

    /**
     * Carries admission and opening ownership across the passage action's validation callbacks.
     *
     * @author lare96
     */
    internal class PassageState(val player: Player, val passage: Passage) {
        private var started = false
        private var opening: Opening? = null
        private var lockRevision: Long? = null

        fun valid(): Boolean {
            if (!alive(player) || !Greegree.disguises(player)) return false
            if (!started) return ready(player) && player.position == passage.from &&
                !isBusy(player.world) && closedPair(player).size == 2
            val owned = opening ?: return false
            return player.isActionLocked && player.actionLockRevision == lockRevision &&
                openings[player.world] === owned && (owned.opened + owned.blockers).all { current(player.world, it) } &&
                player.position.z == passage.from.z && player.x == passage.from.x &&
                player.y in minOf(passage.from.y, passage.to.y)..maxOf(passage.from.y, passage.to.y)
        }

        fun start() {
            started = true
            lockRevision = player.actionLockRevision
            val world = player.world
            if (isBusy(world)) return
            val pair = closedPair(player)
            if (pair.size != 2 || !world.removeObject(pair[0])) return
            if (!current(world, pair[1]) || !world.removeObject(pair[1])) {
                restoreClosed(world, pair[0])
                return
            }
            val north = passage.from.y > passage.to.y
            val opened = listOf(
                world.addObject(4789, Position(2719, if (north) 2766 else 2765), ObjectType.DEFAULT,
                    if (north) ObjectDirection.WEST else ObjectDirection.EAST),
                world.addObject(4790, Position(2722, if (north) 2766 else 2765), ObjectType.DEFAULT,
                    if (north) ObjectDirection.EAST else ObjectDirection.WEST))
            val blockers = (2719..2722).map { x ->
                world.addObject(83, Position(x, 2766), ObjectType.STRAIGHT_WALL, ObjectDirection.SOUTH)
            }
            val owned = Opening(pair, opened, blockers)
            opening = owned
            openings[world] = owned
            world.scheduleOnce(OPEN_TICKS) { restore(world, owned) }
            player.playSound(Sound.RAVINEGATE_OPEN)
            player.sendMessage(if (player.equipment.contains(4021))
                "Kruk shouts: Open the gates! A monkey wishes to pass!"
            else "Kruk calls out in monkey speech and the gates open.")
        }

        fun cancel() {
            opening?.let { restore(player.world, it) }
        }
    }

    /**
     * Walks with the passenger's current monkey model and cancels when death, displacement or stale state intervenes.
     *
     * @author lare96
     */
    internal class PassageAction(player: Player, private val session: PassageState) :
        Action<Player>(player, ActionType.SOFT, false, 1), DeathInterruptible {
        private var lockRevision: Long? = null
        private var elapsed = 0
        private var arrived = false

        override fun onSubmit() {
            if (!session.valid()) { complete(); return }
            mob.overlays.closeWindows()
            mob.walking.clear()
            mob.lock()
            lockRevision = mob.actionLockRevision
            session.start()
            if (!session.valid()) { complete(); return }
            mob.walkStraight(session.passage.to)
        }

        override fun run(): Boolean {
            if (!session.valid()) return true
            if (mob.position == session.passage.to) {
                arrived = true
                return true
            }
            return ++elapsed > CROSSING_TICKS + 2
        }

        override fun onFinished() {
            val revision = lockRevision ?: return
            lockRevision = null
            val ownsLock = mob.isActionLocked && mob.actionLockRevision == revision
            if (!arrived) session.cancel()
            if (!ownsLock) return
            mob.walking.clear()
            // An interrupted admitted walk may be standing in the closing footprint. Roll back only this owned
            // passage's passenger; never move a dead player or one displaced away by another piece of content.
            val passage = session.passage
            if (!arrived && alive(mob) && mob.position.z == passage.from.z && mob.x == passage.from.x &&
                mob.y in minOf(passage.from.y, passage.to.y)..maxOf(passage.from.y, passage.to.y) &&
                mob.position != passage.to) {
                mob.move(passage.from)
            }
            mob.walking.isRunning = mob.isRunning
            mob.unlock()
        }
    }

    private fun closedPair(player: Player): List<GameObject> = PASSAGES.distinctBy { it.id }.mapNotNull { leaf ->
        player.world.objects.findAll(leaf.position).filter {
            it.id == leaf.id && it.direction == leaf.direction && validObject(player, it)
        }.findFirst().orElse(null)
    }

    private fun restore(world: World, owned: Opening) {
        if (openings[world] !== owned) return
        try {
            (owned.opened + owned.blockers).filter { current(world, it) }.forEach { world.removeObject(it) }
            owned.closed.forEach { restoreClosed(world, it) }
        } finally {
            openings.remove(world)
        }
    }

    private fun restoreClosed(world: World, original: GameObject) {
        if (world.objects.findAll(original.position).noneMatch { it.objectType == original.objectType }) {
            world.addObject(original.id, original.position, original.objectType, original.direction)
        }
    }

    private fun current(world: World, obj: GameObject) = obj.state == EntityState.ACTIVE && !obj.isHidden &&
        world.objects.findAll(obj.position).anyMatch { it === obj }

    private fun validObject(player: Player, obj: GameObject) = obj.id in IDS && obj.objectType == ObjectType.DEFAULT &&
        current(player.world, obj) && obj.isVisibleTo(player) && PASSAGES.any {
            it.id == obj.id && it.position == obj.position && it.direction == obj.direction
        }

    private fun alive(player: Player) = player.state == EntityState.ACTIVE && player.isAlive &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun ready(player: Player) = alive(player) && !player.isLocked && !player.walking.isLocked &&
        !player.status.isImmobilized()
}
