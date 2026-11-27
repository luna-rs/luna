package game.content.demonSlayer

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.schedule
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * Runs the original encounter on the shared Delrith spawn. Only the player who weakens him can banish him.
 *
 * @author lare96
 */
object Delrith {
    const val DEMON = 879
    const val WEAKENED = 880
    const val PORTAL_END = 71
    val spawn = Position(3229, 3370)

    private const val INCANTATION_TICKS = 100
    private const val INCANTATION_RANGE = 8
    private var Npc.attempt by Attr.nullableObj(Attempt::class)

    /**
     * Binds the vulnerable form to its player and the native attack that triggered it.
     *
     * @author lare96
     */
    private class Attempt(val player: Player, val attack: CombatAttack<*>)

    fun canFight(player: Player): Boolean =
        DemonSlayer.stage(player) != DemonSlayer.COMPLETE && wieldingSilverlight(player)

    internal fun hasAttempt(player: Player, npc: Npc): Boolean =
        npc.attempt?.let { it.player === player && canSpeak(npc, it) } == true

    internal fun stopAttack(player: Player, target: Npc): Boolean {
        if (target.baseId != DEMON) return false
        if (target.id == WEAKENED) return true
        if (canFight(player)) return false
        player.sendMessage(when {
            DemonSlayer.stage(player) == DemonSlayer.COMPLETE -> "You have already banished Delrith."
            player.inventory.contains(DemonSlayer.SILVERLIGHT) -> "You should wield Silverlight first."
            else -> "You need Silverlight to fight Delrith."
        })
        return true
    }

    internal fun weaken(player: Player, npc: Npc, attack: CombatAttack<*>) {
        val attempt = Attempt(player, attack)
        npc.attempt = attempt
        npc.combat.prepareForExecution()
        npc.navigator.cancel()
        npc.walking.clear()
        npc.interact(null)
        npc.transformKeepingLevels(WEAKENED)
        npc.animation(Animation.CANCEL)
        player.combat.target = null
        player.combat.autoRetaliateTarget = null
        player.navigator.cancel()
        player.walking.clear()
        player.interact(null)
        DemonSlayerDialogue.incantation(player, { finish(npc, attempt) }, { restore(npc, attempt) })

        var ticks = 0
        npc.world.schedule(1) { task ->
            if (npc.attempt !== attempt) {
                task.cancel()
            } else if (++ticks >= INCANTATION_TICKS || !canSpeak(npc, attempt)) {
                restore(npc, attempt)
                task.cancel()
            }
        }
    }

    private fun finish(npc: Npc, attempt: Attempt) {
        if (npc.attempt !== attempt || !canSpeak(npc, attempt)) return
        if (DemonSlayer.complete(attempt.player)) {
            npc.attempt = null
            npc.combat.finishExecution(attempt.attack)
        }
    }

    private fun restore(npc: Npc, attempt: Attempt) {
        if (npc.attempt !== attempt) return
        npc.attempt = null
        if (npc.state != EntityState.ACTIVE || !npc.isAlive || npc.id != WEAKENED) return
        npc.combat.reset()
        npc.walking.setLocked(false)
        npc.resetTransform()
        npc.animation(Animation.CANCEL)
    }

    private fun canSpeak(npc: Npc, attempt: Attempt): Boolean {
        val player = attempt.player
        return npc.state == EntityState.ACTIVE && npc.isAlive && npc.id == WEAKENED &&
            player.state == EntityState.ACTIVE && player.isAlive && player.z == npc.z &&
            player.position.isWithinDistance(npc.position, INCANTATION_RANGE) && wieldingSilverlight(player) &&
            DemonSlayer.stage(player) == DemonSlayer.SILVERLIGHT_RECLAIMED
    }

    private fun wieldingSilverlight(player: Player): Boolean =
        player.equipment[Equipment.WEAPON]?.id == DemonSlayer.SILVERLIGHT
}
