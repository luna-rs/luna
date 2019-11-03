/**
 * An enum representing an item that can be cut into a [Bow].
 */
enum class Log(val id: Int, val bows: List<Bow>) {
    NORMAL(id = 1511,
           bows = listOf(Bow.ARROW_SHAFT, Bow.SHORTBOW, Bow.LONGBOW)),
    OAK(id = 1521,
        bows = listOf(Bow.OAK_SHORTBOW, Bow.OAK_LONGBOW)),
    WILLOW(id = 1519,
           bows = listOf(Bow.WILLOW_SHORTBOW, Bow.WILLOW_LONGBOW)),
    MAPLE(id = 1517,
          bows = listOf(Bow.MAPLE_SHORTBOW, Bow.MAPLE_LONGBOW)),
    YEW(id = 1515,
        bows = listOf(Bow.YEW_SHORTBOW, Bow.YEW_LONGBOW)),
    MAGIC(id = 1513,
          bows = listOf(Bow.MAGIC_SHORTBOW, Bow.MAGIC_LONGBOW));

    companion object {

        /**
         * The knife identifier.
         */
        const val KNIFE = 946

        /**
         * Mappings of [Log.id] to [Log] instances.
         */
        val ID_TO_LOG = values().associateBy { it.id }
    }

    /**
     * An array of unstrung identifiers made from this log.
     */
    val unstrungIds = bows.map { it.unstrung }.toIntArray()
}