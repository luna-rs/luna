package game.combat.npcHooks.dagannothKings

import api.predef.world
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import java.util.IdentityHashMap
import java.util.Optional

/**
 * Stationary Spinolyps periodically submerge into their non-attackable cache forms and emerge again.
 * The 100-tick surface, 15-tick submerged and two-tick transition intervals are Luna timings, not recovered
 * revision 377 script data. Submerged forms make weak, two-tick melee attacks against cardinally adjacent players.
 * Transformations preserve damage and drained stats; removal and respawn discard the preceding spawn's timer.
 *
 * @author lare96
 */
object Spinolyps {
    val forms = mapOf(2892 to 2891, 2894 to 2893, 2896 to 2895)
    private val visibleForms = forms.entries.associate { it.value to it.key }
    private val allForms = forms.keys + forms.values

    /**
     * Per-spawn lifecycle state, accessed only on the game thread.
     *
     * @author lare96
     */
    private data class State(val spawn: Int, var deadline: Long, var phase: Phase = Phase.SURFACE)

    /**
     * Keeps visible animations separate from the hidden cache form.
     *
     * @author lare96
     */
    private enum class Phase { SURFACE, SINKING, SUBMERGED, EMERGING }

    private val states = IdentityHashMap<Npc, State>()

    /**
     * Updates the configured stationary population once per game tick.
     */
    fun process() {
        states.keys.removeIf { it.state != EntityState.ACTIVE }
        for (npc in world.npcs) {
            if (npc.state != EntityState.ACTIVE || npc.baseId !in allForms || !npc.isAlive) continue
            process(npc, world.currentTick)
        }
    }

    /**
     * Advances one live spawn; transition animations suspend outgoing combat.
     */
    internal fun process(npc: Npc, tick: Long) {
        if (npc.state != EntityState.ACTIVE || npc.baseId !in allForms || !npc.isAlive) return
        val visibleId = visibleForms[npc.baseId] ?: npc.baseId
        val state = states[npc]?.takeIf { it.spawn == npc.spawnCount } ?: State(npc.spawnCount,
            tick + if (npc.id in visibleForms) 15 else 100,
            if (npc.id in visibleForms) Phase.SUBMERGED else Phase.SURFACE).also { states[npc] = it }
        npc.defaultDirection = Optional.of(Direction.NORTH)
        npc.navigator.cancel()
        npc.walking.clear()
        if (tick >= state.deadline) {
            when (state.phase) {
                Phase.EMERGING -> {
                    npc.combat.isDisabled = false
                    state.phase = Phase.SURFACE
                    state.deadline = tick + 100
                }
                Phase.SURFACE -> {
                    npc.animation(Animation(2865))
                    clearTarget(npc)
                    npc.combat.isDisabled = true
                    state.phase = Phase.SINKING
                    state.deadline = tick + 2
                }
                Phase.SINKING -> {
                    changeForm(npc, forms.getValue(visibleId))
                    npc.combat.isDisabled = false
                    state.phase = Phase.SUBMERGED
                    state.deadline = tick + 15
                }
                Phase.SUBMERGED -> {
                    clearTarget(npc)
                    npc.combat.isDisabled = true
                    changeForm(npc, visibleId)
                    npc.animation(Animation(2864))
                    state.phase = Phase.EMERGING
                    state.deadline = tick + 2
                }
            }
        }
        if (state.phase == Phase.SURFACE || state.phase == Phase.SUBMERGED) {
            val submerged = state.phase == Phase.SUBMERGED
            val current = npc.combat.target
            if (current != null && (current !is Player || !canReach(npc, current, submerged))) clearTarget(npc)
            if (npc.combat.target == null) {
                val victim = npc.world.locator.findPlayers(npc, if (submerged) 1 else 10) {
                    canReach(npc, it, submerged) && !it.isLocked && npc.combat.checkMultiCombat(it) &&
                        it.controllers.checkCombat(npc)
                }.minByOrNull { it.position.computeLongestDistance(npc.position) }
                if (victim != null) npc.combat.attack(victim)
            }
        }
    }

    /**
     * Hidden water cannot hit diagonally, through walls, across planes or from a projectile's range.
     */
    internal fun canReach(npc: Npc, player: Player, submerged: Boolean): Boolean {
        if (!player.isAlive || player.state != EntityState.ACTIVE || npc.position.z != player.position.z) return false
        return if (submerged) {
            kotlin.math.abs(npc.position.x - player.position.x) +
                kotlin.math.abs(npc.position.y - player.position.y) == 1 &&
                npc.world.collisionManager.reached(npc.position, player, InteractionPolicy.STANDARD_SIZE)
        } else npc.position.isWithinDistance(player.position, 10) &&
            npc.world.collisionManager.raycast(npc.position, player.position)
    }

    private fun clearTarget(npc: Npc) {
        npc.combat.target = null
        npc.combat.autoRetaliateTarget = null
    }

    /**
     * Changes cache form without healing damage, resetting drained levels or clearing damage attribution.
     */
    internal fun changeForm(npc: Npc, id: Int) {
        val levels = npc.skills.map { it.level }
        npc.transform(id)
        npc.skills.forEach { it.level = levels[it.id] }
    }
}
