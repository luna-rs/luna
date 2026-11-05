package game.combat.npcHooks.tzhaar

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * The TzHaar of the city come to the aid of a TzHaar-Hur or TzHaar-Mej that a player attacks. The larger TzHaar-Ket and
 * TzHaar-Xil answer both, and a TzHaar-Mej also answers a TzHaar-Hur. Nobody comes to help a Ket or a Xil.
 */
val MEJ = 2591..2597
val HUR = 2598..2603
val XIL = 2604..2609
val KET = 2610..2616

/**
 * How far from the attacked TzHaar a helper can be.
 */
val HELP_RADIUS = 6

/**
 * What an attacked TzHaar-Mej shouts when help comes ("Save me, attack the human!").
 */
val MEJ_SHOUT = "Kot kl, zek e JalYt!"

/**
 * Whether [helper] is free to come to [victim]'s aid against [attacker], by the same rules an aggressive NPC uses to
 * start a fight.
 */
fun canHelp(helper: Npc, victim: Npc, attacker: Player) =
    helper !== victim && helper.isAlive && !helper.isLocked && !helper.combat.isRetreating &&
        helper.combat.isAttackable && helper.combat.target == null && helper.isWithinDistance(victim, HELP_RADIUS) &&
        helper.combat.isWithinLeash(attacker) && helper.combat.checkMultiCombat(attacker)

/**
 * Sends every free helper near [victim] after [attacker]. Returns how many came.
 */
fun callForHelp(victim: Npc, attacker: Player, helpers: Set<Int>): Int {
    val coming = world.locator.findNpcs(victim, HELP_RADIUS) { it.id in helpers && canHelp(it, victim, attacker) }
    for (helper in coming) {
        helper.combat.attack(attacker)
    }
    return coming.size
}

val HUR_HELPERS = (KET + XIL + MEJ).toSet()
val MEJ_HELPERS = (KET + XIL).toSet()

combat(*HUR.toList().toIntArray()) {
    afterDamage { damage ->
        val attacker = damage.attacker as? Player ?: return@afterDamage
        callForHelp(this, attacker, HUR_HELPERS)
    }
}

combat(*MEJ.toList().toIntArray()) {
    afterDamage { damage ->
        val attacker = damage.attacker as? Player ?: return@afterDamage
        if (callForHelp(this, attacker, MEJ_HELPERS) > 0) {
            speak(MEJ_SHOUT)
        }
    }
}
