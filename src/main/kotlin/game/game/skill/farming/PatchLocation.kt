package game.skill.farming

import game.skill.farming.patch.*

/**
 * The location of a farming patch, implemented by the location enums of each patch type such as
 * [AllotmentPatchLocation] and [HerbPatchLocation].
 *
 * @author hydrozoa
 */
interface PatchLocation {

    /**
     * The id of the patch object, a multiloc drawn from this patch's bits of [varpId].
     */
    val objectId: Int

    /**
     * The id of the varp holding the state of this patch.
     */
    val varpId: Int

    /**
     * How far this patch's byte is shifted left within [varpId].
     */
    val shifts: Int

    /**
     * The unique key this patch is saved under.
     */
    val key: String

    /**
     * Creates a new, fully weeded patch at this location.
     */
    fun createPatch(): FarmingPatch
}
