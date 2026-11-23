package game.skill.firemaking.lightSource

import game.player.Sound
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * What the water in the Lumbridge Swamp Caves does to the lights of a player who falls in. A lit candle lantern
 * breaks, and naked flames and the oil lantern go out. The bullseye and sapphire lanterns and the mining helmet stay lit.
 *
 * @author TheLining
 */
object SwampWater {

    /**
     * What a lit candle lantern breaks into.
     */
    const val BROKEN_GLASS = 1469

    /**
     * The lit candle lanterns.
     */
    private val CANDLE_LANTERNS = setOf(LightSource.CANDLE_LANTERN.lit, LightSource.BLACK_CANDLE_LANTERN.lit)

    /**
     * The lights the water doesn't reach.
     */
    private val WATERPROOF = setOf(LightSource.BULLSEYE_LANTERN, LightSource.SAPPHIRE_LANTERN,
                                   LightSource.MINING_HELMET, LightSource.BUG_LANTERN)

    /**
     * Breaks or puts out [plr]'s lights after a fall into the water.
     */
    fun fallIn(plr: Player) {
        var broken = false
        for (index in 0 until plr.inventory.capacity()) {
            if (plr.inventory[index]?.id in CANDLE_LANTERNS) {
                plr.inventory[index] = Item(BROKEN_GLASS)
                broken = true
            }
        }
        val out = LightSource.extinguish(plr) { it !in WATERPROOF }
        if (broken) {
            plr.sendMessage("Your candle lantern breaks.")
        }
        if (out.isNotEmpty()) {
            plr.sendMessage("The water puts out your light.")
        }
        if (broken || out.isNotEmpty()) {
            plr.playSound(Sound.SIZZLE)
        }
    }
}
