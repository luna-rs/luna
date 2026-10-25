package game.skill.farming

import game.skill.farming.patch.*

/**
 * Data for bush patches. Every patch is drawn from one byte of [Farming.BUSH_PATCH_VARP] (varbits 732 to 735).
 *
 * @author hydrozoa
 */
enum class BushPatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    CHAMPIONS_GUILD(7577, 0),
    RIMMINGTON(7578, 8),
    ETCETERIA(7579, 16),
    ARDOUGNE(7580, 24),
    ;

    override val varpId = Farming.BUSH_PATCH_VARP

    override val key = "bush_${name.lowercase()}"

    override fun createPatch() = BushPatch(this)

    companion object {
        private val locations = BushPatchLocation.values().associateBy(BushPatchLocation::objectId)
        fun lookup(objectId: Int): BushPatchLocation? = locations[objectId]
    }
}
