package game.content.parcelService

import api.predef.*
import api.predef.ext.*
import game.content.parcelService.ParcelService.ParcelInterface
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput

on(LoginEvent::class) { ParcelService.show(plr) }

npc1(ParcelService.RIONASTA) { ParcelService.talk(plr) }

on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == ParcelService.GRID && ParcelInterface::class in plr.overlays }
    .then { ParcelService.value(plr, index) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == ParcelService.GRID && ParcelInterface::class in plr.overlays }
    .then { ParcelService.pack(plr, index, 1) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == ParcelService.GRID && ParcelInterface::class in plr.overlays }
    .then { ParcelService.pack(plr, index, 5) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == ParcelService.GRID && ParcelInterface::class in plr.overlays }
    .then { ParcelService.pack(plr, index, Int.MAX_VALUE) }

on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == ParcelService.GRID && ParcelInterface::class in plr.overlays }
    .then {
        val slot = index
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player, value: Int) = ParcelService.pack(player, slot, value)
        })
    }
