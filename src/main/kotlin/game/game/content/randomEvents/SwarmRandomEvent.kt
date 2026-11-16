/*
 * Random-event behavior adapted from LostCityRS/Content (377-wip) and LostCityRS/Engine-TS,
 * engine commit 1d25566cb53e7af1b1cb18ade8af996316c19614.
 */
package game.content.randomEvents

import api.predef.world
import api.predef.ext.removeNpc
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Lost City's owner-bound swarm. It attacks immediately, has no gift or attack option, and releases
 * the personal-event slot at spawn. Native combat owns melee accuracy, pursuit, the seven-tick cooldown
 * and damage delivery. Every twenty ticks it leaves after losing its owner beyond three tiles.
 * Logout and death remove the temporary NPC; this encounter never restores on login.
 *
 * @author lare96
 */
class SwarmRandomEvent(override val player: Player, override val npc: Npc) : RandomEventEncounter {
    override val resumeKey get() = RandomEvents.Kind.SWARM.name
    override val blocksNewEvents get() = false

    /**
     * Source AI timer cadence, independent of combat cooldown and world-aligned readiness polling.
     */
    private var age = 0

    init {
        player.playSound(Sound.SWARM_APPEAR)
        RandomEvents.beginHostile(this)
    }

    override fun tick(): Boolean {
        if (npc.state != EntityState.ACTIVE) return false
        if (++age % 20 != 0) return true
        if (npc.position.z != player.position.z ||
            npc.combat.target == null && !npc.position.isWithinDistance(player.position, 3)) return false
        if (npc.combat.target !== player) npc.combat.attack(player)
        return true
    }

    override fun talk(visitor: Player) = Unit

    override fun close() {
        npc.combat.target = null
        npc.navigator.cancel()
        if (npc.state != EntityState.INACTIVE) world.removeNpc(npc)
    }

    /**
     * Rejects unrelated, stale or removed swarms before a native attack can commit or play audio.
     * The exact owner remains the only victim, including when another personal event has started.
     */
    internal fun canAttack(target: Mob): Boolean = target === player && RandomEvents.owns(this) &&
        npc.state == EntityState.ACTIVE && npc.isAlive && npc.combat.target === player &&
        player.state == EntityState.ACTIVE && player.isAlive
}
