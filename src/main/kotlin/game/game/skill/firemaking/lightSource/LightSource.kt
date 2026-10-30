package game.skill.firemaking.lightSource

import api.predef.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The candles, torches, lamps and lanterns players light with a tinderbox to see in dark places. Lighting them gives
 * no experience.
 *
 * @param unlit The unlit item.
 * @param lit The lit item.
 * @param level The Firemaking level needed to light it.
 * @param brightness How much darkness it lifts, from 1 to 3. The bug lantern lights nothing.
 * @param naked Whether its flame is uncovered, so mud can put it out.
 * @author TheLining
 */
enum class LightSource(val unlit: Int, val lit: Int, val level: Int, val brightness: Int, val naked: Boolean) {
    CANDLE(unlit = 36, lit = 33, level = 1, brightness = 1, naked = true),
    BLACK_CANDLE(unlit = 38, lit = 32, level = 1, brightness = 1, naked = true),
    TORCH(unlit = 596, lit = 594, level = 1, brightness = 1, naked = true),
    CANDLE_LANTERN(unlit = 4529, lit = 4531, level = 4, brightness = 1, naked = false),
    BLACK_CANDLE_LANTERN(unlit = 4532, lit = 4534, level = 4, brightness = 1, naked = false),
    OIL_LAMP(unlit = 4522, lit = 4524, level = 12, brightness = 2, naked = true),
    OIL_LANTERN(unlit = 4537, lit = 4539, level = 26, brightness = 2, naked = false),
    BUG_LANTERN(unlit = 7051, lit = 7053, level = 33, brightness = 0, naked = false),
    BULLSEYE_LANTERN(unlit = 4548, lit = 4550, level = 49, brightness = 3, naked = false),
    SAPPHIRE_LANTERN(unlit = 4701, lit = 4702, level = 49, brightness = 2, naked = false),
    MINING_HELMET(unlit = 5014, lit = 5013, level = 65, brightness = 2, naked = false);

    /**
     * The name used in messages, such as "oil lamp".
     */
    val messageName: String
        get() = itemName(lit).lowercase().removePrefix("lit ")

    companion object {

        /**
         * The most light that several lights add up to.
         */
        const val MAX_BRIGHTNESS = 3

        /**
         * The light sources by lit item.
         */
        private val LIT = entries.associateBy { it.lit }

        /**
         * How much light [plr]'s lit lights give together, up to [MAX_BRIGHTNESS]. A worn mining helmet counts.
         */
        fun brightness(plr: Player): Int {
            var total = 0
            for (item in plr.inventory) {
                total += item?.let { LIT[it.id]?.brightness } ?: 0
            }
            total += plr.equipment[Equipment.HEAD]?.let { LIT[it.id]?.brightness } ?: 0
            return minOf(total, MAX_BRIGHTNESS)
        }

        /**
         * Puts out every lit light [plr] carries or wears that [filter] matches, and returns them.
         */
        fun extinguish(plr: Player, filter: (LightSource) -> Boolean = { true }): List<LightSource> {
            val out = ArrayList<LightSource>()
            for (index in 0 until plr.inventory.capacity()) {
                val light = plr.inventory[index]?.let { LIT[it.id] } ?: continue
                if (filter(light)) {
                    plr.inventory[index] = Item(light.unlit)
                    out += light
                }
            }
            val helmet = plr.equipment[Equipment.HEAD]?.let { LIT[it.id] }
            if (helmet != null && filter(helmet)) {
                plr.equipment[Equipment.HEAD] = Item(helmet.unlit)
                out += helmet
            }
            return out
        }
    }
}
