package game.content.vampireSlayer

import api.predef.ext.schedule
import game.content.vampireSlayer.VampireSlayer as Quest
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.NpcAggressionProfile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.combat.damage.CombatDamage

/**
 * A single challenger owns each coffin encounter. A player who leaves can reopen the coffin and fight again.
 *
 * @author lare96
 */
class CountDraynor(val owner: Player) : Npc(owner.context, Quest.COUNT, SPAWN), CombatRestriction {
    companion object {
        val SPAWN = Position(3078, 9774)
        private const val CHASE_DISTANCE = 10
        private const val LIFETIME_TICKS = 500

        fun canWake(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
            Quest.stage(player) in Quest.STARTED..Quest.SPOKE_TO_HARLOW &&
            sameCrypt(player.position)

        fun find(player: Player): CountDraynor? = player.world.locator
            .findNpcs(SPAWN, CHASE_DISTANCE) { it is CountDraynor && it.owner === player && it.state == EntityState.ACTIVE }
            .firstOrNull() as? CountDraynor

        private fun sameCrypt(position: Position): Boolean =
            position.z == SPAWN.z && position.isWithinDistance(SPAWN, CHASE_DISTANCE)
    }

    private var regenerating = false

    init {
        respawnTicks = -1
        combat.retreatPolicy = RetreatPolicy.NONE
    }

    override fun getAggressionProfile(): NpcAggressionProfile? = null

    override fun canFight(opponent: Mob): Boolean = opponent === owner && state == EntityState.ACTIVE &&
        canWake(owner) && position.isWithinDistance(owner.position, CHASE_DISTANCE)

    override fun onSpawn() {
        var age = 0
        world.schedule(1) { task ->
            if (state != EntityState.ACTIVE || !isAlive) task.cancel()
            else if (!canFight(owner) || ++age >= LIFETIME_TICKS) {
                world.npcs.remove(this)
                task.cancel()
            }
        }
    }

    internal fun weaken() {
        if (health != totalHealth || !owner.inventory.contains(Quest.GARLIC)) return
        health -= 10
        for ((id, reduction) in listOf(Skill.ATTACK to 10, Skill.STRENGTH to 10, Skill.DEFENCE to 40)) {
            val skill = skill(id)
            skill.level = (skill.level - reduction).coerceAtLeast(1)
        }
        owner.sendMessage("The vampire seems to weaken.")
    }

    internal fun defend(hit: CombatDamage?): CombatDamage? {
        weaken()
        if (hit == null || hit.amount.orElse(-1) < health) return hit
        val inventory = owner.inventory
        if (inventory.containsAll(Quest.STAKE, Quest.HAMMER)) return hit
        if (inventory.contains(Quest.STAKE)) owner.sendMessage("You can't drive the stake far enough in without a hammer!")
        regenerating = true
        return hit.cappedAt(health - 1)
    }

    internal fun afterHit(hit: CombatDamage) {
        if (hit.attacker !== owner) return
        if (regenerating) {
            regenerating = false
            health = totalHealth
            owner.sendMessage("The vampire seems to regenerate!")
        } else if (!isAlive) {
            Quest.finish(owner)
        }
    }
}
