package game.combat.npcHooks.rockCrab

import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatAction

/**
 * Rock crabs sit disguised as rocks and wake when a player steps next to them. They wake for players of every combat
 * level, new bots included, until the player has spent [TOLERANCE_MINUTES] in the area. A player resets that by walking
 * far enough away and coming back. A crab that has been out of combat for a while walks back to its spawn and turns
 * back into rocks.
 *
 * Crabs are spawned in their disguised form and have no other aggression, so they only start a fight by waking up.
 *
 * @author lare96
 */
object RockCrabs {

    /**
     * The disguised rock forms, mapped to the crabs they wake up as.
     */
    val DISGUISES = mapOf(1266 to 1265, 1268 to 1267)

    /**
     * How long a player can stay around the crabs before they stop waking for them.
     */
    const val TOLERANCE_MINUTES = 10

    /**
     * How long a crab that can't get back to its spawn keeps trying, after its last fight.
     */
    private const val GIVE_UP_HOMING_TICKS = 100

    /**
     * Determines if [npc] is a rock crab that was spawned disguised, whatever form it's in now.
     */
    fun isRockCrab(npc: Npc): Boolean = npc.baseId in DISGUISES

    /**
     * Determines if [npc] is a rock crab sitting in its disguised form.
     */
    fun isDisguised(npc: Npc): Boolean = npc.id in DISGUISES

    /**
     * Wakes or puts back to sleep every rock crab that needs it. Called once per tick.
     */
    fun process() {
        for (npc in world.npcs) {
            if (npc.state == EntityState.ACTIVE && isRockCrab(npc)) {
                process(npc)
            }
        }
    }

    /**
     * Wakes [crab] if it's disguised and a player is next to it. Otherwise, sends it home and disguises it once it
     * has been out of combat for a while.
     */
    private fun process(crab: Npc) {
        if (isDisguised(crab)) {
            val victim = findVictim(crab) ?: return
            crab.transform(DISGUISES.getValue(crab.id))
            crab.combat.attack(victim)
            return
        }
        if (!crab.isAlive || crab.isLocked || crab.combat.isRetreating || crab.combat.target != null ||
            crab.actions.contains(CombatAction::class.java) || crab.combat.inCombat()) {
            return
        }
        // Awake and idle: someone standing right next to it is still fair game.
        val victim = findVictim(crab)
        if (victim != null) {
            crab.combat.attack(victim)
            return
        }
        val timer = crab.combat.timer
        val idleTicks = if (timer.isRunning) timer.durationTicks else Long.MAX_VALUE
        if (crab.position == crab.basePosition || idleTicks >= GIVE_UP_HOMING_TICKS) {
            disguise(crab)
        } else if (!crab.navigator.isActive && crab.walking.isEmpty) {
            crab.navigator.navigate(crab.basePosition, false)
        }
    }

    /**
     * Finds a player standing next to [crab] that it can attack.
     */
    private fun findVictim(crab: Npc): Player? =
        world.locator.findPlayers(crab, 1) { it.isAlive && !it.isLocked && !isTolerant(it) &&
            crab.combat.isWithinLeash(it) && crab.combat.checkMultiCombat(it) }.firstOrNull()

    /**
     * Determines if [player] has been around long enough that rock crabs ignore them.
     */
    private fun isTolerant(player: Player): Boolean = player.tolerance.duration.toMinutes() >= TOLERANCE_MINUTES

    /**
     * Turns [crab] back into rocks, forgetting who damaged it.
     */
    private fun disguise(crab: Npc) {
        crab.navigator.cancel()
        crab.walking.clear()
        crab.interact(null)
        crab.combat.damageStack.clear()
        crab.resetTransform()
    }
}
