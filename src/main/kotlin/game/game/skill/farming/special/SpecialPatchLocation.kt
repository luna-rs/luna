package game.skill.farming.special

import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Data for the special patches, one of each: the Al Kharid cactus patch, the Canifis mushroom patch and the Draynor
 * Manor belladonna patch. They share [Farming.SPECIAL_PATCH_VARP], with six bits each (varbits 744, 746 and 748).
 *
 * @author hydrozoa
 */
enum class SpecialPatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    CACTUS(7771, 0),
    MUSHROOM(8337, 8),
    BELLADONNA(7572, 16),
    ;

    override val varpId = Farming.SPECIAL_PATCH_VARP

    override val key = "special_${name.lowercase()}"

    override fun createPatch(): FarmingPatch = when (this) {
        CACTUS -> CactusPatch(this)
        MUSHROOM -> MushroomPatch(this)
        BELLADONNA -> BelladonnaPatch(this)
    }

    companion object {
        private val locations = SpecialPatchLocation.values().associateBy(SpecialPatchLocation::objectId)
        fun lookup(objectId: Int): SpecialPatchLocation? = locations[objectId]
    }
}
