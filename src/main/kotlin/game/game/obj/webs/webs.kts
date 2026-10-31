package game.obj.webs

import api.predef.*
import api.predef.ext.*
import game.obj.webs.Web.OPEN_DELAY
import game.obj.webs.Web.SLASHED_TICKS
import game.obj.webs.Web.SLASHED_WEB
import game.obj.webs.Web.WEB
import game.obj.webs.Web.slashAnimation
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.SoundMessageWriter

/**
 * Tries to slash [web] with [itemId], which is either the weapon [plr] is wielding or the item they used on it. Half
 * of all tries cut through.
 */
fun slash(plr: Player, web: GameObject, itemId: Int?) {
    val animation = itemId?.let { slashAnimation(it) }
    if (animation == null) {
        plr.sendMessage("Only a sharp blade can cut through this sticky web.")
        return
    }
    plr.animation(animation)
    plr.queue(SoundMessageWriter(Sound.HACKSWORD_SLASH.id, 1, 15))
    if (!randBoolean()) {
        plr.sendMessage("You fail to cut through it.")
        return
    }
    plr.sendMessage("You slash the web apart.")
    plr.submitAction(object : LockedAction(plr, false, OPEN_DELAY) {
        override fun run(): Boolean {
            open(web)
            return true
        }
    })
}

/**
 * Replaces [web] with a slashed web for [SLASHED_TICKS]. Does nothing if someone else has already slashed it.
 */
fun open(web: GameObject) {
    if (!world.removeObject(web)) {
        return
    }
    val slashed = world.addObject(SLASHED_WEB, web.position, web.objectType, web.direction)
    world.scheduleOnce(SLASHED_TICKS) {
        if (world.removeObject(slashed)) {
            world.addObject(web.id, web.position, web.objectType, web.direction)
        }
    }
}

object1(WEB) { slash(plr, gameObject, plr.equipment[Equipment.WEAPON]?.id) }

// Any item can be used on a web, but only a blade cuts it. This listener sees every object, so only webs get walked
// to, and other objects still say "Nothing interesting happens."
on(ItemOnObjectEvent::class, interaction = { _, target ->
    if (target is GameObject && target.id == WEB) InteractionPolicy.STANDARD_SIZE else InteractionPolicy.UNSPECIFIED
}) {
    if (gameObject.id == WEB) {
        slash(plr, gameObject, usedItemId)
    }
}
