package game.content.darkness

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.player.Sound
import game.skill.firemaking.lightSource.LightSource
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player

/**
 * The swamp gas in two parts of the Lumbridge Swamp Caves. A naked flame carried into it flares up, and if it is still
 * there [FLARE_TICKS] later the gas explodes, hurting the player and putting out every naked flame they carry.
 *
 * @author TheLining
 */
object SwampGas {

    /**
     * Where the gas is: the cave north of the rope, and the patch in the south.
     */
    val AREAS = listOf(Area.of(3151, 9584, 3172, 9594), Area.of(3205, 9552, 3213, 9560))

    /**
     * The ticks a naked flame can stay in the gas before it explodes.
     */
    const val FLARE_TICKS = 7

    /**
     * The damage the explosion deals.
     */
    const val EXPLOSION_DAMAGE = 12

    /**
     * Lights shown flaring first, when a player carries more than one naked flame.
     */
    private val FLARE_ORDER = listOf(LightSource.OIL_LAMP, LightSource.CANDLE, LightSource.BLACK_CANDLE,
                                     LightSource.TORCH)

    /**
     * The ticks this player's flame has been flaring, or 0 while it isn't.
     */
    private var Player.gasTicks by Attr.int()

    /**
     * The light shown flaring for this player.
     */
    private var Player.flaringLight by Attr.nullableObj(LightSource::class)

    /**
     * Flares up, calms or explodes [plr]'s naked flames, by where they stand.
     */
    fun tick(plr: Player) {
        val flame = if (AREAS.any { it.contains(plr.position) }) nakedFlame(plr) else null
        val flaring = plr.flaringLight
        when {
            flame == null && flaring != null -> {
                plr.sendMessage("Your ${flaring.messageName} stops flaring.")
                reset(plr)
            }

            flame == null -> Unit
            flaring == null -> {
                plr.flaringLight = flame
                plr.gasTicks = 1
                plr.sendMessage("Your ${flame.messageName} flares brightly.")
            }

            ++plr.gasTicks > FLARE_TICKS -> explode(plr)
        }
    }

    /**
     * The naked flame [plr] carries that shows flaring, if any.
     */
    private fun nakedFlame(plr: Player): LightSource? {
        val carried = plr.inventory.mapNotNullTo(HashSet()) { item -> item?.id }
        return FLARE_ORDER.firstOrNull { it.lit in carried }
    }

    /**
     * Explodes the gas around [plr].
     */
    private fun explode(plr: Player) {
        reset(plr)
        LightSource.extinguish(plr) { it.naked }
        plr.playSound(Sound.LANTERN_EXPLODES)
        plr.sendMessage("The swamp gas explodes!")
        plr.damage(EXPLOSION_DAMAGE)
    }

    /**
     * Stops [plr]'s flame flaring.
     */
    fun reset(plr: Player) {
        plr.flaringLight = null
        plr.gasTicks = 0
    }
}
