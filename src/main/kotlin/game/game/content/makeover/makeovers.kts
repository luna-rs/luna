package game.content.makeover

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.PlayerAppearance

npc1(MakeoverMage.MALE_MAGE) { MakeoverMage.talk(plr, targetNpc) }
npc1(MakeoverMage.FEMALE_MAGE) { MakeoverMage.talk(plr, targetNpc) }

MakeoverMage.MALE_BUTTONS.forEach { button(it) { MakeoverMage.selectGender(plr, PlayerAppearance.GENDER_MALE) } }
MakeoverMage.FEMALE_BUTTONS.forEach { button(it) { MakeoverMage.selectGender(plr, PlayerAppearance.GENDER_FEMALE) } }
repeat(MakeoverMage.SKIN_COLOURS) { colour ->
    button(MakeoverMage.FIRST_SKIN_BUTTON + colour) { MakeoverMage.selectSkin(plr, colour) }
}
button(MakeoverMage.CONFIRM_BUTTON) { MakeoverMage.confirm(plr) }

npc1(Hairdresser.HAIRDRESSER) { Hairdresser.talk(plr, targetNpc) }

HairStyle.values().forEach { style ->
    style.kits.indices.forEach { index ->
        button(style.firstStyleButton + index) { Hairdresser.selectStyle(plr, style, index) }
    }
    repeat(HairStyle.HAIR_COLOURS) { colour ->
        button(style.firstColourButton + colour) { Hairdresser.selectColour(plr, style, colour) }
    }
    button(style.confirmButton) { Hairdresser.confirm(plr, style) }
}

/**
 * The make-over mages in the world, found once they have spawned.
 */
val mages = ArrayList<Npc>()

on(ServerLaunchEvent::class) {
    world.schedule(MakeoverMage.SWAP_TICKS) {
        if (mages.none { it.state == EntityState.ACTIVE }) {
            mages.clear()
            world.npcs.filterTo(mages) { it.id == MakeoverMage.MALE_MAGE || it.id == MakeoverMage.FEMALE_MAGE }
        }
        mages.filter { it.state == EntityState.ACTIVE }.forEach { MakeoverMage.swap(it) }
    }
}
