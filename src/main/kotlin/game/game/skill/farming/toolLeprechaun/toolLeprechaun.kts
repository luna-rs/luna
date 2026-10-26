package game.skill.farming.toolLeprechaun

import api.predef.*
import api.predef.ext.*
import game.skill.farming.*
import game.skill.farming.toolLeprechaun.ToolLeprechaunInterface.Companion.REMOVE_BULK_WIDGET
import game.skill.farming.toolLeprechaun.ToolLeprechaunInterface.Companion.REMOVE_SINGLE_WIDGET
import game.skill.farming.toolLeprechaun.ToolLeprechaunInterface.Companion.STORE_BULK_WIDGET
import game.skill.farming.toolLeprechaun.ToolLeprechaunInterface.Companion.STORE_SINGLE_WIDGET
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput

/**
 * The tool leprechaun npc id.
 */
val toolLeprechaun = 3021

/**
 * Stores or removes up to [amount] of the clicked tool, if the tool leprechaun interface is open.
 */
fun exchange(msg: WidgetItemClickEvent, amount: Int) {
    val plr = msg.plr
    if (amount <= 0) {
        return
    }
    val inter = plr.overlays[ToolLeprechaunInterface::class] ?: return
    val tool = ToolLeprechaunInterface.getTool(msg.widgetId, msg.index) ?: return
    when (msg.widgetId) {
        STORE_SINGLE_WIDGET, STORE_BULK_WIDGET -> inter.store(plr, tool, amount)
        REMOVE_SINGLE_WIDGET, REMOVE_BULK_WIDGET -> inter.remove(plr, tool, amount)
    }
}

/**
 * Opens the tool storage interface from the "Exchange" option.
 */
npc2(toolLeprechaun) { plr.overlays.open(ToolLeprechaunInterface()) }

/**
 * Store/remove 1, the only option for single tools.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { ToolLeprechaunInterface.getTool(widgetId, index) != null }
    .then { exchange(this, 1) }

/**
 * Store/remove 5.
 */
on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == STORE_BULK_WIDGET || widgetId == REMOVE_BULK_WIDGET }
    .then { exchange(this, 5) }

/**
 * Store/remove all.
 */
on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == STORE_BULK_WIDGET || widgetId == REMOVE_BULK_WIDGET }
    .then { exchange(this, Int.MAX_VALUE) }

/**
 * Store/remove (x).
 */
on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == STORE_BULK_WIDGET || widgetId == REMOVE_BULK_WIDGET }
    .then {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) = exchange(this@then, value)
        })
    }

/**
 * Sends the stored tools when logged in.
 */
on(LoginEvent::class) { Farming.sendStoredTools(plr) }
