package game.obj.webs

import io.luna.game.model.def.WeaponAnimationDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * The webs that block the way until they're slashed. webs.kts handles slashing them, and
 * [api.bot.webwalk.walk.crossing.WebCrossing] takes bots through them.
 *
 * @author TheLining
 */
object Web {

    /**
     * The web that blocks the way until it's slashed.
     */
    const val WEB = 733

    /**
     * The web after it's been slashed, which can be walked through.
     */
    const val SLASHED_WEB = 734

    /**
     * How many ticks a slashed web stays open.
     */
    const val SLASHED_TICKS = 100

    /**
     * How many ticks after a successful slash the web opens.
     */
    const val OPEN_DELAY = 2

    /**
     * The knife, which can be used on a web.
     */
    const val KNIFE = 946

    /**
     * Slashing a web with a knife.
     */
    val KNIFE_SLASH = Animation(3747)

    /**
     * Returns the animation for slashing a web with [itemId], or `null` if it can't cut one. A knife can, and so can
     * any weapon with a slash attack.
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
     * Determines if [itemId] can cut a web.
     */
    fun cuts(itemId: Int): Boolean = slashAnimation(itemId) != null

    /**
     * Returns the item that [plr] would cut a web with: the wielded weapon if it cuts, which the Slash option uses, or
     * else the first item in the inventory that cuts, which is used on the web. `null` if they carry nothing that cuts.
     *
     * @return The item id, and `true` if it's the wielded weapon.
     */
    fun blade(plr: Player): Pair<Int, Boolean>? {
        val weapon = plr.equipment[Equipment.WEAPON]?.id
        if (weapon != null && cuts(weapon)) {
            return Pair(weapon, true)
        }
        val carried = plr.inventory.firstOrNull { it != null && cuts(it.id) } ?: return null
        return Pair(carried.id, false)
    }
}
