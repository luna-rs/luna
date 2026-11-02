package game.item.greegree

import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.event.impl.UseSpellEvent
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * Holds a player in monkey form to what a monkey can do: they can't attack anything or cast spells, and a zombie
 * monkey can't run.
 *
 * @author TheLining
 */
class GreegreeController(private val plr: Player) : PlayerController(plr) {

    companion object {

        /**
         * The Run button.
         */
        private const val RUN_BUTTON = 153

        /**
         * The spellbook interfaces.
         */
        private val SPELLBOOKS = Spellbook.values().map { it.defaultWidget }.toSet()

        /**
         * Whether [button] casts a spell from a spellbook.
         */
        private fun isSpell(button: Int) =
            WidgetDefinition.ALL[button].map { it.parentId in SPELLBOOKS }.orElse(false)
    }

    override fun combat(victim: Mob) = false

    override fun event(event: ControllableEvent) = when (event) {
        is AttackNpcEvent, is UseSpellEvent -> false
        is ButtonClickEvent -> when {
            isSpell(event.id) -> false
            event.id == RUN_BUTTON && Greegree.held(plr)?.canRun == false -> {
                // The client lights the Run button as it sends the click, so it's set back to Walk.
                plr.sendVarp(PersistentVarp.RUNNING, false)
                false
            }
            else -> true
        }
        else -> true
    }
}
