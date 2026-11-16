/*
 * Rules and interface identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.treasureTrails

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.content.treasureTrails.TreasureTrails.trailItem
import game.content.treasureTrails.TreasureTrails.trailRevision
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Owns temporary Zamorak wizards. Only the clue owner can earn credit for the current dig step.
 * Leaving, dropping the clue or logging out removes the encounter without granting credit.
 *
 * @author lare96
 */
object TrailGuardians {
    var Player.defeatedGuardian by Attr.int().persist("trail_defeated_guardian")

    /**
     * Binds a guardian to one player and one generation of a clue.
     *
     * @author lare96
     */
    private data class Encounter(val owner: Player, val clue: Int, val revision: Int)

    private val encounters = mutableMapOf<Npc, Encounter>()

    fun owner(npc: Npc): Player? = encounters[npc]?.owner

    fun mayAttack(attacker: Mob, npc: Npc): Boolean = owner(npc)?.let { it === attacker } ?: true

    fun canDig(player: Player, clue: EasyClues.Clue): Boolean {
        if (player.defeatedGuardian == clue.id) return true
        if (encounters.values.any { it.owner === player }) return false
        val npc = Npc(ctx, 1007, player.position)
        npc.respawnTicks = -1
        npc.combat.retreatPolicy = RetreatPolicy.NONE
        world.addNpc(npc)
        track(player, npc, clue.id)
        npc.speak("Die, human!")
        npc.combat.attack(player)
        var lifetime = 0
        world.schedule(1) { task ->
            val encounter = encounters[npc]
            if (encounter == null) {
                task.cancel()
            } else if (++lifetime >= 500 || player.state != EntityState.ACTIVE || !player.isAlive ||
                !player.inventory.contains(clue.id) || player.trailRevision != encounter.revision ||
                !player.position.isWithinDistance(npc.position, 17) || npc.state != EntityState.ACTIVE) {
                encounters.remove(npc)
                world.removeNpc(npc)
                task.cancel()
            } else if (npc.isAlive && npc.combat.target !== player) {
                npc.combat.attack(player)
            }
        }
        return false
    }

    internal fun track(player: Player, npc: Npc, clueId: Int) {
        encounters[npc] = Encounter(player, clueId, player.trailRevision)
    }

    fun defeated(player: Player, npc: Npc): Boolean {
        val encounter = encounters[npc] ?: return false
        if (encounter.owner !== player || encounter.revision != player.trailRevision ||
            player.trailItem != encounter.clue || !player.inventory.contains(encounter.clue)) return false
        player.defeatedGuardian = encounter.clue
        encounters.remove(npc)
        return true
    }

    fun leave(player: Player, reset: Boolean = true) {
        val npcs = encounters.filterValues { it.owner === player }.keys.toList()
        npcs.forEach { encounters.remove(it); world.removeNpc(it) }
        if (reset) player.defeatedGuardian = 0
    }
}
