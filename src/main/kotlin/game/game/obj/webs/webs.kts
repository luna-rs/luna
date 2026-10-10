package game.obj.webs

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.def.WeaponAnimationDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * The web that blocks the way until it's slashed.
 */
val WEB = 733

/**
 * The web after it's been slashed, which can be walked through.
 */
val SLASHED_WEB = 734

/**
 * How many ticks a slashed web stays open.
 */
val SLASHED_TICKS = 100

/**
 * How many ticks after a successful slash the web opens.
 */
val OPEN_DELAY = 2

/**
 * The knife, which can be used on a web.
 */
val KNIFE = 946

/**
 * Slashing a web with a knife.
 */
val KNIFE_SLASH = Animation(3747)

/**
 * Returns the animation for slashing a web with [itemId], or `null` if it can't cut one. A knife can, and so can any
 * weapon with a slash attack.
 */
fun slashAnimation(itemId: Int): Animation? {
    if (itemId == KNIFE) {
        return KNIFE_SLASH
    }
    val styles = WeaponDefinition.ALL[itemId].map { it.typeDef.styles }.orElse(emptyList())
    val style = styles.firstOrNull { it.bonus == EquipmentBonus.SLASH_ATTACK } ?: return null
    val animation = WeaponAnimationDefinition.ALL[itemId]?.getAttackAnimation(style.type) ?: -1
    return Animation(if (animation != -1) animation else style.attackAnimation)
}

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
    plr.playSound(Sound.HACKSWORD_SLASH)
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
