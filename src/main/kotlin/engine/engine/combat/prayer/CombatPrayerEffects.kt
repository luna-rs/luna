package engine.combat.prayer

import game.player.Sound
import io.luna.game.model.EntityType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage

/**
 * Delivered-hit and death effects for Smite, Redemption, and Retribution.
 * Smite uses actual health damage and the attack's launch snapshot. Redemption observes all real hits, including
 * poison and zero hits, but never revives a dead player. Retribution follows combat or poison deaths; reflected and
 * environmental damage do not start it. Its own hits cannot start another explosion or grant combat experience.
 *
 * @author lare96
 */
object CombatPrayerEffects {

    /**
     * Drains one prayer point per four delivered PvP damage before health loss. Reaching zero disables prayers
     * immediately, including Protect Item on a killing hit. Prayer bonus and protection prayers do not reduce drain.
     */
    fun smite(damage: CombatDamage, source: CombatAttack<*>) {
        val victim = damage.victim as? Player ?: return
        if (damage.attacker !is Player || damage.attacker === victim || !source.isSmiteActive ||
            source is RetributionCombatAttack) return
        val points = damage.rawAmount.coerceAtLeast(0) / 4
        if (points == 0) return
        val prayer = victim.skill(Skill.PRAYER)
        prayer.level = (prayer.level - points).coerceAtLeast(0)
        if (prayer.level == 0) victim.combat.prayers.deactivateAll()
    }

    /**
     * Handles Redemption after health loss, and permits poison deaths to trigger Retribution without treating
     * reflected or arbitrary environmental hits as combat kills. The strict ten-percent comparison avoids rounding
     * a 99-HP player's threshold to eight HP. Healing consumes all prayer before restoring health.
     */
    fun receivedHit(player: Player, hit: Hit) {
        if (!player.isAlive) {
            if (hit.type == Hit.HitType.POISON) retribution(player, player.combat.lastCombatWith)
            return
        }
        val prayers = player.combat.prayers
        val prayer = player.skill(Skill.PRAYER)
        if (prayer.level <= 0 || !prayers.isActive(CombatPrayer.REDEMPTION) ||
            player.health.toLong() * 10 >= player.totalHealth) return
        prayer.level = 0
        prayers.deactivateAll()
        val heal = minOf(prayer.staticLevel / 4, (player.totalHealth - player.health).coerceAtLeast(0))
        if (heal > 0) player.addHealth(heal)
        player.graphic(Graphic(436))
        player.playSound(Sound.REDEMPTION_HEAL)
    }

    /**
     * Starts Retribution once for a lethal delivered combat hit. A Retribution source is excluded explicitly to
     * prevent death chains; recoil bypasses the combat-damage delivery path and cannot reach this hook.
     */
    fun combatDeath(damage: CombatDamage, source: CombatAttack<*>) {
        val player = damage.victim as? Player ?: return
        if (!player.isAlive && source !is RetributionCombatAttack) retribution(player, damage.attacker)
    }

    /**
     * Consumes Retribution at death and schedules one burst on the following tick. Targets must still be adjacent
     * and legal when it lands. The original position, controller, opponent, and base Prayer level are retained so
     * movement, a finished activity, or a respawn cannot redirect the burst into another fight.
     * Each recipient owns its pending burst, allowing death, removal, and activity cleanup to discard it with other
     * incoming combat impacts before the recipient can enter another life or match.
     */
    private fun retribution(player: Player, opponent: Mob?) {
        val prayers = player.combat.prayers
        if (player.skill(Skill.PRAYER).level <= 0 || !prayers.deactivate(CombatPrayer.RETRIBUTION, true)) return
        val origin = player.position
        val controller = player.controllers.primary
        val maxHit = player.skill(Skill.PRAYER).staticLevel / 4
        player.graphic(Graphic(437))
        player.playSound(Sound.RETRIBUTION_TEST)
        val candidates = linkedSetOf<Mob>()
        if (opponent != null) candidates.add(opponent)
        candidates.addAll(player.world.locator.findViewable<Player>(EntityType.PLAYER, origin))
        candidates.addAll(player.world.locator.findViewable<io.luna.game.model.mob.Npc>(EntityType.NPC, origin))
        val bursts = candidates.filter { it !== player }.map {
            RetributionCombatAttack(player, it, origin, controller, opponent, maxHit)
        }.filter { it.canHit() }
        bursts.forEach { burst ->
            burst.victim.combat.impacts.schedule(1, burst::deliver) {}
        }
    }
}
