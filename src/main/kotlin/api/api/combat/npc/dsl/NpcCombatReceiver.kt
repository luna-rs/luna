package api.combat.npc.dsl

import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.attack.CombatAttack

/**
 * DSL receiver used to register attack and defence hooks for an NPC combat script.
 *
 * This class stores the hook callbacks produced by the DSL so they can later be invoked by the NPC combat system
 * for the configured attacker.
 *
 * @author lare96
 */
class NpcCombatReceiver {

    /**
     * Whether poison may deal the killing blow. Finishing-item creatures disable this so damage outside their
     * ordinary defence hook cannot bypass the required execution item. Ordinary NPCs retain lethal poison.
     */
    var poisonCanKill: Boolean = true

    /**
     * Scales damage-based player combat and Hitpoints experience against this NPC. Cast experience
     * is independent. The source uses 1000 as normal experience, so a value of 25 is 0.025 here.
     */
    var damageExperienceMultiplier: Double = 1.0
        set(value) {
            require(value.isFinite() && value >= 0.0) { "Invalid combat experience multiplier: $value" }
            field = value
        }

    /**
     * Attack hook callback.
     *
     * Defaults to the NPC's standard combat attack when no custom attack hook is registered.
     */
    var attack: NpcAttackCombatHookReceiver.() -> CombatAttack<out Npc> = { default() }

    /**
     * Whether [attack] was registered, so the NPC may attack in other ways than the standard melee attack.
     */
    var replacesAttack = false
        private set

    /**
     * Defence hook callback.
     *
     * Defaults to a no-op when no custom defence hook is registered.
     */
    var defend: NpcDefenceCombatHookReceiver.() -> Unit = { }

    /**
     * Registers the attack hook for this NPC combat receiver.
     *
     * The supplied callback is used to build the combat attack that should be executed when this NPC attacks.
     *
     * @param attackSupplier The callback that produces the attack to execute.
     */
    fun attack(attackSupplier: NpcAttackCombatHookReceiver.() -> CombatAttack<out Npc>) {
        attack = attackSupplier
        replacesAttack = true
    }

    /**
     * Registers the defence hook for this NPC combat receiver.
     *
     * The supplied callback is invoked when this NPC receives a hit, allowing defence-side effects such as animation
     * overrides or reactive behaviour.
     *
     * @param action The callback to execute when the NPC defends against an attack.
     */
    fun defend(action: NpcDefenceCombatHookReceiver.() -> Unit) {
        defend = action
    }
}
